import { useEffect, useRef, useState } from 'react';
import { CalendarPlus } from 'lucide-react';
import { useNavigate, useParams } from 'umi';
import { PageHeader } from '../../components/PageHeader';
import { getSelection } from '../../models/selection';
import { createAppointment, createWaitlist, getDepartments, getDoctorBookingStatus, getDoctors, getSlots } from '../../services/registration';
import type { AppointmentSlot, Doctor } from '../../typings/api';
import { findDoctorById, getDoctorScheduleDates, groupSlotsByHalfDay, summarizeHalfDaySlots, type DoctorScheduleDate } from '../../utils/doctor';
import { createIdempotencyKey, getApiErrorMessage } from '../../utils/form';
import { formatAmount } from '../../utils/medical';
import { isDuplicateDoctorAppointmentError } from '../../utils/registration';

/** 显示单个医生资料与未来七天上午、下午号源课程表。 */
export default function DoctorBookingPage() {
  const { doctorId: doctorIdText } = useParams();
  const navigate = useNavigate();
  const doctorId = Number(doctorIdText);
  const query = new URLSearchParams(location.search);
  const initialDepartmentId = Number(query.get('departmentId')) || undefined;
  const selection = getSelection();
  const dates = getDoctorScheduleDates();
  const [doctor, setDoctor] = useState<Doctor>();
  const [slotsByDate, setSlotsByDate] = useState<Record<string, AppointmentSlot[]>>({});
  const [selectedDate, setSelectedDate] = useState(dates[0].value);
  const [loadingDoctor, setLoadingDoctor] = useState(true);
  const [loadingSchedule, setLoadingSchedule] = useState(false);
  const [loadingDates, setLoadingDates] = useState<string[]>([]);
  const [checkingBookingStatus, setCheckingBookingStatus] = useState(true);
  const [notice, setNotice] = useState('');
  const [isDuplicateBookingBlocked, setDuplicateBookingBlocked] = useState(false);
  const operationKey = useRef<string>();
  const noticeTimer = useRef<number>();

  /** 展示最长三秒的提示，日期切换提示不会长期遮挡号源课程表。 */
  function showTransientNotice(message: string) {
    if (noticeTimer.current) window.clearTimeout(noticeTimer.current);
    setNotice(message);
    noticeTimer.current = window.setTimeout(() => setNotice(''), 3000);
  }

  useEffect(() => () => { if (noticeTimer.current) window.clearTimeout(noticeTimer.current); }, []);

  /** 在当前医院内定位医生，深链接缺少科室时按全部科室回退查询。 */
  async function loadDoctor() {
    if (!selection.hospitalId || !Number.isInteger(doctorId) || doctorId <= 0) {
      showTransientNotice('请先选择医院和医生');
      setLoadingDoctor(false);
      return;
    }
    setLoadingDoctor(true);
    try {
      const departments = await getDepartments(selection.hospitalId);
      const targetDepartments = initialDepartmentId ? departments.filter((department) => department.id === initialDepartmentId) : departments;
      // 仅在当前医院内聚合医生，确保深链接不会跨医院读取医生资料。
      const pages = await Promise.all(targetDepartments.map((department) =>
        getDoctors(selection.hospitalId!, department.id, selectedDate)
          .then((page) => page.records.map((item) => ({ ...item, departmentId: department.id, departmentName: department.name }))),
      ));
      const found = findDoctorById(doctorId, pages.flat());
      if (!found) {
        showTransientNotice('医生不存在、已停用或不属于当前医院');
        return;
      }
      setDoctor(found);
    } catch (error) {
      showTransientNotice(getApiErrorMessage(error));
    } finally {
      setLoadingDoctor(false);
    }
  }

  /** 读取未来七天真实号源，供课程表的每个日期列展示。 */
  async function loadWeeklySchedule(currentDoctor: Doctor) {
    if (!selection.hospitalId) return;
    setLoadingSchedule(true);
    try {
      const results = await Promise.all(dates.map(async (date) => {
        try {
          return [date.value, await getSlots(currentDoctor.id, selection.hospitalId!, date.value)] as const;
        } catch {
          // 某天请求失败不影响其他日期渲染，空单元格不伪造可预约号源。
          return [date.value, []] as const;
        }
      }));
      setSlotsByDate(Object.fromEntries(results));
    } catch (error) {
      showTransientNotice(getApiErrorMessage(error));
    } finally {
      setLoadingSchedule(false);
    }
  }

  /** 在展示可点击号源前核验账号维度的同医生待就诊挂号。 */
  async function loadDoctorBookingStatus(currentDoctor: Doctor) {
    setCheckingBookingStatus(true);
    setDuplicateBookingBlocked(false);
    try {
      const status = await getDoctorBookingStatus(currentDoctor.id);
      // 后端按支付账号和医生 ID 查询有效待就诊挂号，覆盖本人及所有家庭成员。
      setDuplicateBookingBlocked(status.booked);
    } catch (error) {
      // 查询失败时不伪造已预约状态，仍由创建接口完成最终并发校验。
      showTransientNotice(getApiErrorMessage(error));
    } finally {
      setCheckingBookingStatus(false);
    }
  }

  /** 刷新用户点击日期的号源状态，即使重复点击同一日期也重新请求后端。 */
  async function refreshDateSlots(date: string, notifyWhenEmpty: boolean) {
    if (!selection.hospitalId || !doctor) return;
    setLoadingDates((current) => current.includes(date) ? current : [...current, date]);
    try {
      const slots = await getSlots(doctor.id, selection.hospitalId, date);
      setSlotsByDate((current) => ({ ...current, [date]: slots }));
      // 点击日期后的空号源提示只保留三秒，避免始终覆盖课程表底部。
      if (notifyWhenEmpty && slots.length === 0) showTransientNotice('医生当日暂无可预约排班');
    } catch (error) {
      showTransientNotice(getApiErrorMessage(error));
    } finally {
      setLoadingDates((current) => current.filter((item) => item !== date));
    }
  }

  useEffect(() => { void loadDoctor(); }, [doctorId]);
  useEffect(() => {
    if (!doctor) return;
    void loadWeeklySchedule(doctor);
    void loadDoctorBookingStatus(doctor);
  }, [doctor]);

  /** 选择日期并刷新该日期号源；重复点击同一日期同样触发刷新。 */
  function selectDate(date: string) {
    setSelectedDate(date);
    void refreshDateSlots(date, true);
  }

  /** 创建挂号订单或对无余量时段发起候补登记。 */
  async function chooseSlot(slot: AppointmentSlot) {
    if (isDuplicateBookingBlocked) {
      showTransientNotice('当前账号下已有就诊人预约过该医生，不能再次预约');
      return;
    }
    if (!selection.hospitalId) {
      showTransientNotice('请先选择医院');
      return;
    }
    const key = operationKey.current || (operationKey.current = createIdempotencyKey());
    try {
      if (slot.availableCount <= 0) {
        const waitlist = await createWaitlist({ patientId: selection.patientId, slotId: slot.slotId }, key);
        operationKey.current = undefined;
        showTransientNotice(`候补登记成功，当前排队第 ${waitlist.queueNo} 位`);
        return;
      }
      const order = await createAppointment({ patientId: selection.patientId, hospitalId: selection.hospitalId, slotId: slot.slotId }, key);
      operationKey.current = undefined;
      navigate(`/assistant/pay/${order.paymentId}?appointmentId=${order.appointmentId}`);
    } catch (error) {
      if (isDuplicateDoctorAppointmentError(error)) {
        // 明确业务拒绝不应复用幂等键，后续仅允许用户查看已有挂号或取消待支付订单。
        operationKey.current = undefined;
        setDuplicateBookingBlocked(true);
        showTransientNotice('当前账号已有该医生待就诊挂号，暂不能再次预约');
        return;
      }
      // 网络失败保留幂等键，重复点击可安全重试同一业务请求。
      showTransientNotice(getApiErrorMessage(error));
    }
  }

  return <main className="subpage doctor-page"><PageHeader title="医生主页" backPath="/home/departments" /><section className="subpage-content">
    {loadingDoctor && <p className="empty-state">正在读取医生资料...</p>}
    {!loadingDoctor && doctor && <><section className="doctor-profile-card"><span className="doctor-profile-avatar">{doctor.name.slice(0, 1)}</span><div><h2>{doctor.name} <small>{doctor.title || '医生'}</small></h2><p>{doctor.departmentName || '所属科室待确认'}</p><p>{doctor.specialty || '暂无专长说明'}</p><strong>挂号费 {formatAmount(doctor.registrationFeeCent)}</strong></div></section><section className="doctor-service-card"><CalendarPlus size={26} /><div><h2>预约挂号</h2><p>按日期与上午、下午查看真实开放时段。</p></div></section>{isDuplicateBookingBlocked && <section className="duplicate-appointment-notice"><b>已有待就诊挂号</b><p>当前账号已有就诊人正在等待该医生接诊，完成、取消或失效后可再次预约。</p><button className="secondary-button" type="button" onClick={() => navigate('/assistant')}>查看挂号记录</button></section>}<section className="doctor-schedule"><h2>{doctor.departmentName || '门诊'}号源</h2>{loadingSchedule && <p className="empty-state">号源加载中...</p>}{!loadingSchedule && <ScheduleTable dates={dates} selectedDate={selectedDate} slotsByDate={slotsByDate} loadingDates={loadingDates} isDuplicateBookingBlocked={isDuplicateBookingBlocked} isCheckingBookingStatus={checkingBookingStatus} onSelectDate={selectDate} onChooseSlot={chooseSlot} />}</section></>}
    {!loadingDoctor && !doctor && <p className="empty-state">暂无可展示的医生资料</p>}
  </section>{notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}</main>;
}

/** 课程表式号源的入参。 */
interface ScheduleTableProps { dates: DoctorScheduleDate[]; selectedDate: string; slotsByDate: Record<string, AppointmentSlot[]>; loadingDates: string[]; isDuplicateBookingBlocked: boolean; isCheckingBookingStatus: boolean; onSelectDate: (date: string) => void; onChooseSlot: (slot: AppointmentSlot) => void; }

/** 按七天列、上午与下午行展示医生实际可预约号源。 */
function ScheduleTable({ dates, selectedDate, slotsByDate, loadingDates, isDuplicateBookingBlocked, isCheckingBookingStatus, onSelectDate, onChooseSlot }: ScheduleTableProps) {
  return <div className="doctor-timetable-wrap"><div className="doctor-timetable"><div className="doctor-timetable__corner" />{dates.map((date) => <button className={selectedDate === date.value ? 'doctor-timetable__date active' : 'doctor-timetable__date'} key={date.value} type="button" onClick={() => onSelectDate(date.value)}><b>{date.day}</b><span>{date.weekday}</span>{loadingDates.includes(date.value) && <i>刷新中</i>}</button>)}<ScheduleRow title="上午" period="morning" dates={dates} slotsByDate={slotsByDate} isDuplicateBookingBlocked={isDuplicateBookingBlocked} isCheckingBookingStatus={isCheckingBookingStatus} onChooseSlot={onChooseSlot} /><ScheduleRow title="下午" period="afternoon" dates={dates} slotsByDate={slotsByDate} isDuplicateBookingBlocked={isDuplicateBookingBlocked} isCheckingBookingStatus={isCheckingBookingStatus} onChooseSlot={onChooseSlot} /></div></div>;
}

/** 单个半天号源行的入参。 */
interface ScheduleRowProps { title: string; period: 'morning' | 'afternoon'; dates: DoctorScheduleDate[]; slotsByDate: Record<string, AppointmentSlot[]>; isDuplicateBookingBlocked: boolean; isCheckingBookingStatus: boolean; onChooseSlot: (slot: AppointmentSlot) => void; }

/** 展示某一半天内每个日期的汇总余号，避免逐时段展示造成移动端日程表拥挤。 */
function ScheduleRow({ title, period, dates, slotsByDate, isDuplicateBookingBlocked, isCheckingBookingStatus, onChooseSlot }: ScheduleRowProps) {
  return <><div className="doctor-timetable__period">{title}</div>{dates.map((date) => {
    // 同一半天的多个后端时段仅汇总余量，点击时仍提交其中一个真实 slotId。
    const summary = summarizeHalfDaySlots(groupSlotsByHalfDay(slotsByDate[date.value] || [])[period]);
    if (!summary.targetSlot) return <div className="doctor-timetable__cell" key={`${period}-${date.value}`}><small className="doctor-timetable__empty">暂无号源</small></div>;
    const hasAvailability = summary.availableCount > 0;
    const isDisabled = isDuplicateBookingBlocked || isCheckingBookingStatus;
    return <div className="doctor-timetable__cell" key={`${period}-${date.value}`}><button className={isDisabled ? 'doctor-timetable__slot is-disabled' : hasAvailability ? 'doctor-timetable__slot' : 'doctor-timetable__slot is-full'} type="button" disabled={isDisabled} onClick={() => onChooseSlot(summary.targetSlot!)}><span>剩余</span><b>{summary.availableCount}</b>{isDuplicateBookingBlocked ? <em className="doctor-timetable__duplicate-text">已有<br />待就<br />诊</em> : <em>{isCheckingBookingStatus ? '状态核验中' : hasAvailability ? '点击挂号' : '候补挂号'}</em>}</button></div>;
  })}</>;
}

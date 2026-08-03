import { useEffect, useRef, useState } from 'react';
import { CalendarPlus, Stethoscope } from 'lucide-react';
import { useNavigate, useParams } from 'umi';
import { PageHeader } from '../../components/PageHeader';
import { getSelection } from '../../models/selection';
import { createAppointment, createWaitlist, getDepartments, getDoctors, getSlots } from '../../services/registration';
import type { AppointmentSlot, Department, Doctor } from '../../typings/api';
import { buildDoctorPagePath, findDoctorById, getDoctorScheduleDates } from '../../utils/doctor';
import { createIdempotencyKey, getApiErrorMessage } from '../../utils/form';
import { formatAmount, formatMedicalTime } from '../../utils/medical';

/** 显示单个医生的真实资料与可预约号源。 */
export default function DoctorBookingPage() {
  const { doctorId: doctorIdText } = useParams();
  const navigate = useNavigate();
  const doctorId = Number(doctorIdText);
  const query = new URLSearchParams(location.search);
  const initialDepartmentId = Number(query.get('departmentId')) || undefined;
  const selection = getSelection();
  const dates = getDoctorScheduleDates();
  const [doctor, setDoctor] = useState<Doctor>();
  const [slots, setSlots] = useState<AppointmentSlot[]>([]);
  const [selectedDate, setSelectedDate] = useState(dates[0].value);
  const [loadingDoctor, setLoadingDoctor] = useState(true);
  const [loadingSlots, setLoadingSlots] = useState(false);
  const [notice, setNotice] = useState('');
  const operationKey = useRef<string>();

  /** 在当前医院内定位医生，深链接缺少科室时按所有科室回退查询。 */
  async function loadDoctor() {
    if (!selection.hospitalId || !Number.isInteger(doctorId) || doctorId <= 0) {
      setNotice('请先选择医院和医生');
      setLoadingDoctor(false);
      return;
    }
    setLoadingDoctor(true);
    try {
      const departments = await getDepartments(selection.hospitalId);
      const targetDepartments = initialDepartmentId
        ? departments.filter((department) => department.id === initialDepartmentId)
        : departments;
      // 仅在当前医院内聚合医生，确保深链接不会跨医院读取医生资料。
      const pages = await Promise.all(targetDepartments.map((department) => getDoctors(selection.hospitalId!, department.id, selectedDate).then((page) => page.records.map((item) => ({ ...item, departmentId: department.id, departmentName: department.name })))));
      const found = findDoctorById(doctorId, pages.flat());
      if (!found) {
        setNotice('医生不存在、已停用或不属于当前医院');
        return;
      }
      setDoctor(found);
    } catch (error) {
      setNotice(getApiErrorMessage(error));
    } finally {
      setLoadingDoctor(false);
    }
  }

  /** 加载选定日期下该医生已发布的实际号源。 */
  async function loadSlots() {
    if (!selection.hospitalId || !doctor) return;
    setLoadingSlots(true);
    try {
      setSlots(await getSlots(doctor.id, selection.hospitalId, selectedDate));
    } catch (error) {
      setSlots([]);
      setNotice(getApiErrorMessage(error));
    } finally {
      setLoadingSlots(false);
    }
  }

  useEffect(() => { void loadDoctor(); }, [doctorId]);
  useEffect(() => { if (doctor) void loadSlots(); }, [doctor, selectedDate]);

  /** 创建挂号订单或对无余量时段发起候补登记。 */
  async function chooseSlot(slot: AppointmentSlot) {
    if (!selection.hospitalId) {
      setNotice('请先选择医院');
      return;
    }
    const key = operationKey.current || (operationKey.current = createIdempotencyKey());
    try {
      if (slot.availableCount <= 0) {
        const waitlist = await createWaitlist({ patientId: selection.patientId, slotId: slot.slotId }, key);
        operationKey.current = undefined;
        setNotice(`候补登记成功，当前排队第 ${waitlist.queueNo} 位`);
        return;
      }
      const order = await createAppointment({ patientId: selection.patientId, hospitalId: selection.hospitalId, slotId: slot.slotId }, key);
      operationKey.current = undefined;
      navigate(`/assistant/pay/${order.paymentId}?appointmentId=${order.appointmentId}`);
    } catch (error) {
      // 网络失败保留幂等键，重复点击可安全重试同一业务请求。
      setNotice(getApiErrorMessage(error));
    }
  }

  return <main className="subpage doctor-page">
    <PageHeader title="医生主页" backPath="/home/departments" />
    <section className="subpage-content">
      {loadingDoctor && <p className="empty-state">正在读取医生资料...</p>}
      {!loadingDoctor && doctor && <>
        <section className="doctor-profile-card"><span className="doctor-profile-avatar">{doctor.name.slice(0, 1)}</span><div><h2>{doctor.name} <small>{doctor.title || '医生'}</small></h2><p>{doctor.departmentName || '所属科室待确认'}</p><p>{doctor.specialty || '暂无专长说明'}</p><strong>挂号费 {formatAmount(doctor.registrationFeeCent)}</strong></div></section>
        <section className="doctor-service-card"><CalendarPlus size={26} /><div><h2>预约挂号</h2><p>请选择就诊日期和实际开放时段完成预约。</p></div></section>
        <section className="doctor-schedule"><h2>{doctor.departmentName || '门诊'}号源</h2><nav className="doctor-date-tabs" aria-label="选择挂号日期">{dates.map((date) => <button className={selectedDate === date.value ? 'active' : ''} key={date.value} type="button" onClick={() => setSelectedDate(date.value)}><b>{date.day}</b><span>{date.weekday}</span></button>)}</nav>{loadingSlots && <p className="empty-state">号源加载中...</p>}{!loadingSlots && slots.map((slot) => <button className="doctor-slot-card" key={slot.slotId} type="button" onClick={() => void chooseSlot(slot)}><Stethoscope size={21} /><div><b>{formatMedicalTime(slot.startTime)} - {new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(new Date(slot.endTime))}</b><span>{formatAmount(slot.feeCent)}</span></div><em>{slot.availableCount > 0 ? `剩余 ${slot.availableCount} · 点击挂号` : '已满 · 候补登记'}</em></button>)}{!loadingSlots && !slots.length && <p className="empty-state">该日期暂无已发布号源</p>}</section>
      </>}
      {!loadingDoctor && !doctor && <p className="empty-state">暂无可展示的医生资料</p>}
    </section>
    {notice && <div className="toast" onClick={() => setNotice('')}>{notice}</div>}
  </main>;
}

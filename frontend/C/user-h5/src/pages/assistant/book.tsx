import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'umi';
import { PageHeader } from '../../components/PageHeader';
import { getSelection } from '../../models/selection';
import { createAppointment, createWaitlist, getDepartments, getDoctors, getSlots } from '../../services/registration';
import type { AppointmentSlot, Department, Doctor } from '../../typings/api';
import { createIdempotencyKey, getApiErrorMessage } from '../../utils/form';
import { formatAmount, formatMedicalTime } from '../../utils/medical';

/** 按科室、医生和时段完成挂号或候补登记。 */
export default function BookingPage() {
  const navigate = useNavigate(); const selection = getSelection(); const [departments, setDepartments] = useState<Department[]>([]); const [doctors, setDoctors] = useState<Doctor[]>([]); const [slots, setSlots] = useState<AppointmentSlot[]>([]); const [departmentId, setDepartmentId] = useState<number>(); const [doctor, setDoctor] = useState<Doctor>(); const [date, setDate] = useState(new Date().toISOString().slice(0, 10)); const [notice, setNotice] = useState(''); const key = useRef<string>();
  useEffect(() => { if (selection.hospitalId) void getDepartments(selection.hospitalId).then(setDepartments).catch((error) => setNotice(getApiErrorMessage(error))); }, [selection.hospitalId]);
  /** 选择科室后加载当天医生。 */
  async function chooseDepartment(id: number) { setDepartmentId(id); setDoctor(undefined); setSlots([]); if (selection.hospitalId) setDoctors((await getDoctors(selection.hospitalId, id, date)).records); }
  /** 选择医生后查询其可预约时段。 */
  async function chooseDoctor(item: Doctor) { setDoctor(item); if (selection.hospitalId) setSlots(await getSlots(item.id, selection.hospitalId, date)); }
  /** 创建挂号订单或为已满号源登记候补。 */
  async function chooseSlot(slot: AppointmentSlot) { const idempotencyKey = key.current || (key.current = createIdempotencyKey()); try { if (slot.availableCount <= 0) { const waitlist = await createWaitlist({ patientId: selection.patientId, slotId: slot.slotId }, idempotencyKey); key.current = undefined; setNotice(`候补登记成功，当前排队第 ${waitlist.queueNo} 位`); return; } const order = await createAppointment({ patientId: selection.patientId, hospitalId: selection.hospitalId!, slotId: slot.slotId }, idempotencyKey); key.current = undefined; navigate(`/assistant/pay/${order.paymentId}?appointmentId=${order.appointmentId}`); } catch (error) { setNotice(getApiErrorMessage(error)); } }
  return <main className="subpage"><PageHeader title="预约挂号" /><section className="subpage-content"><h2>选择科室</h2><div className="chip-list">{departments.map((item) => <button className={departmentId === item.id ? 'active' : ''} key={item.id} type="button" onClick={() => void chooseDepartment(item.id)}>{item.name}</button>)}</div>{departmentId && <><h2>选择医生</h2>{doctors.map((item) => <button className="record-card" key={item.id} type="button" onClick={() => void chooseDoctor(item)}><b>{item.name} {item.title}</b><span>{item.specialty || '暂无专长说明'} · {formatAmount(item.registrationFeeCent)}</span><em>{item.availableCount > 0 ? `余${item.availableCount}` : '已满'}</em></button>)}</>}{doctor && <><h2>选择时段</h2>{slots.map((item) => <button className="slot-card" key={item.slotId} type="button" onClick={() => void chooseSlot(item)}><b>{formatMedicalTime(item.startTime)}</b><span>{formatAmount(item.feeCent)}</span><em>{item.availableCount > 0 ? `余${item.availableCount}` : '候补'}</em></button>)}</>}</section>{notice && <div className="toast" onClick={() => setNotice('')}>{notice}</div>}</main>;
}

import { useEffect, useRef, useState } from 'react';
import { Search, Stethoscope } from 'lucide-react';
import { useLocation, useNavigate } from 'umi';
import { PageHeader } from '../../components/PageHeader';
import { getSelection } from '../../models/selection';
import { getDepartments, getDoctors } from '../../services/registration';
import type { Department, Doctor } from '../../typings/api';
import { buildDoctorPagePath } from '../../utils/doctor';
import { resolveInitialDepartment } from '../../utils/home-search';

/** 展示当前医院的科室目录及选中科室医生。 */
export default function DepartmentBrowserPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [departments, setDepartments] = useState<Department[]>([]);
  const [selectedDepartmentId, setSelectedDepartmentId] = useState<number>();
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState('');
  const doctorRequestId = useRef(0);

  /** 根据用户选中的科室加载该科室当天可预约医生。 */
  async function chooseDepartment(department: Department, hospitalId: number) {
    setSelectedDepartmentId(department.id);
    setLoading(true);
    const requestId = ++doctorRequestId.current;
    try {
      const page = await getDoctors(hospitalId, department.id, new Date().toISOString().slice(0, 10));
      // 忽略较早科室请求的返回，防止快速切换后显示错位医生。
      if (requestId === doctorRequestId.current) setDoctors(page.records.map((doctor) => ({ ...doctor, departmentId: department.id, departmentName: department.name })));
    } catch (error: any) {
      if (requestId === doctorRequestId.current) {
        setDoctors([]);
        setNotice(error.message || '医生列表加载失败');
      }
    } finally {
      if (requestId === doctorRequestId.current) setLoading(false);
    }
  }

  /** 初始化当前医院的科室目录，并优先恢复从医生主页返回时的科室选择。 */
  async function loadDepartments() {
    const hospitalId = getSelection().hospitalId;
    if (!hospitalId) {
      setNotice('请先选择医院');
      return;
    }
    try {
      const items = await getDepartments(hospitalId);
      setDepartments(items);
      const preferredDepartmentId = Number(new URLSearchParams(location.search).get('departmentId'));
      // 仅恢复当前医院真实存在的科室，旧链接或跨院科室参数仍安全回退至默认科室。
      const initial = items.find((department) => department.id === preferredDepartmentId) || resolveInitialDepartment(items);
      if (initial) await chooseDepartment(initial, hospitalId);
    } catch (error: any) {
      setNotice(error.message || '科室列表加载失败');
    }
  }

  useEffect(() => { void loadDepartments(); }, []);

  return <main className="subpage department-page">
    <PageHeader title="科室列表" backPath="/home" />
    <section className="department-content">
      <button className="department-search-entry" type="button" onClick={() => navigate('/home/search')}><Search size={21} /><span>搜索医生或科室</span></button>
      <section className="department-browser">
        <aside className="department-sidebar" aria-label="医院科室">
          {departments.map((department) => <button className={selectedDepartmentId === department.id ? 'active' : ''} key={department.id} type="button" onClick={() => { const hospitalId = getSelection().hospitalId; if (hospitalId) void chooseDepartment(department, hospitalId); }}>{department.name}</button>)}
          {!departments.length && <p>暂无科室</p>}
        </aside>
        <section className="department-doctors" aria-label="科室医生">
          {loading && <p className="empty-state">医生加载中...</p>}
          {!loading && doctors.map((doctor) => <button className="department-doctor" key={doctor.id} type="button" onClick={() => navigate(buildDoctorPagePath(doctor.id, doctor.departmentId))}>
            <span className="department-doctor__icon"><Stethoscope size={19} /></span><div><b>{doctor.name} {doctor.title || '医生'}</b><small>{doctor.specialty || '暂无专长说明'}</small></div><em>{doctor.availableCount > 0 ? `可约 ${doctor.availableCount}` : '暂时无号'}</em>
          </button>)}
          {!loading && departments.length > 0 && !doctors.length && <p className="empty-state">该科室暂无可展示医生</p>}
          {!loading && !departments.length && <p className="empty-state">请选择其他医院查看科室</p>}
        </section>
      </section>
    </section>
    {notice && <div className="toast" onClick={() => setNotice('')}>{notice}</div>}
  </main>;
}

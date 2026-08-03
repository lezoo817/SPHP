import { useState } from 'react';
import { FileSearch, Search, X } from 'lucide-react';
import { useNavigate } from 'umi';
import { PageHeader } from '../../components/PageHeader';
import { getSelection } from '../../models/selection';
import { getDepartments, getDoctors } from '../../services/registration';
import type { Department, Doctor } from '../../typings/api';
import { buildDoctorPagePath } from '../../utils/doctor';
import { hasSearchKeyword } from '../../utils/home-search';

/** 聚合当前医院的医生与科室搜索结果。 */
export default function MedicalSearchPage() {
  const navigate = useNavigate();
  const [keyword, setKeyword] = useState('');
  const [departments, setDepartments] = useState<Department[]>([]);
  const [doctors, setDoctors] = useState<Doctor[]>([]);
  const [tab, setTab] = useState<'doctor' | 'department'>('doctor');
  const [searched, setSearched] = useState(false);
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState('');

  /** 用户提交有效关键词后查询科室，并并行聚合各科室医生。 */
  async function search() {
    const hospitalId = getSelection().hospitalId;
    // 空搜索只展示引导状态，不触发接口请求。
    if (!hasSearchKeyword(keyword)) {
      setSearched(false);
      return;
    }
    if (!hospitalId) {
      setNotice('请先选择医院');
      return;
    }
    setLoading(true);
    setSearched(true);
    try {
      const normalizedKeyword = keyword.trim();
      const allDepartments = await getDepartments(hospitalId);
      const matchedDepartments = allDepartments.filter((item) => `${item.name}${item.description || ''}`.includes(normalizedKeyword));
      // 医生接口按科室提供，因此并行查询后在前端按姓名和专长聚合。
      const doctorPages = await Promise.all(allDepartments.map((department) => getDoctors(hospitalId, department.id, new Date().toISOString().slice(0, 10)).then((page) => page.records.map((doctor) => ({ ...doctor, departmentId: department.id, departmentName: department.name })))));
      setDepartments(matchedDepartments);
      setDoctors(doctorPages.flat().filter((doctor) => `${doctor.name}${doctor.specialty || ''}`.includes(normalizedKeyword)));
    } catch (error: any) {
      setNotice(error.message || '搜索失败');
    } finally {
      setLoading(false);
    }
  }

  /** 清空内容后恢复请输入搜索内容的引导状态。 */
  function clearSearch() {
    setKeyword('');
    setSearched(false);
    setDepartments([]);
    setDoctors([]);
  }

  return <main className="subpage discovery-page">
    <PageHeader title="搜索" backPath="/home/departments" />
    <section className="subpage-content medical-search-page">
      <div className="discovery-input">
        <Search size={22} /><input autoFocus value={keyword} placeholder="搜索医生或科室" onChange={(event) => setKeyword(event.target.value)} onKeyDown={(event) => event.key === 'Enter' && void search()} />
        {keyword && <button type="button" aria-label="清空搜索内容" onClick={clearSearch}><X size={18} /></button>}<button className="search-submit" type="button" disabled={loading} onClick={() => void search()}>{loading ? '查询中' : '搜索'}</button>
      </div>
      {!searched && !loading && <section className="search-empty-state"><FileSearch size={84} strokeWidth={1.3} /><p>请输入搜索内容</p></section>}
      {searched && <>
        <div className="result-tabs"><button className={tab === 'doctor' ? 'active' : ''} type="button" onClick={() => setTab('doctor')}>医生({doctors.length})</button><button className={tab === 'department' ? 'active' : ''} type="button" onClick={() => setTab('department')}>科室({departments.length})</button></div>
        {tab === 'doctor' && <section>{doctors.map((doctor) => <button className="doctor-result" key={doctor.id} type="button" onClick={() => navigate(buildDoctorPagePath(doctor.id, doctor.departmentId))}><span className="doctor-avatar">{doctor.name.slice(0, 1)}</span><div><b>{doctor.name} <small>{doctor.title || '医生'}</small></b><p>{doctor.departmentName} · {doctor.specialty || '暂无专长说明'}</p><em>{doctor.availableCount > 0 ? `可约 ${doctor.availableCount}` : '暂时无号'}</em></div></button>)}{!doctors.length && <p className="empty-state">未找到相关医生</p>}</section>}
        {tab === 'department' && <section>{departments.map((department) => <button className="department-result" key={department.id} type="button" onClick={() => navigate(`/assistant/book?departmentId=${department.id}`)}><b>{department.name}</b><span>{department.description || '暂无科室说明'}</span></button>)}{!departments.length && <p className="empty-state">未找到相关科室</p>}</section>}
      </>}
    </section>
    {notice && <div className="toast" onClick={() => setNotice('')}>{notice}</div>}
  </main>;
}

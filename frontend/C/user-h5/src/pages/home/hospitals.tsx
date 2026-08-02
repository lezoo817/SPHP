import { useEffect, useMemo, useState } from 'react';
import { Search } from 'lucide-react';
import { useNavigate } from 'umi';
import { PageHeader } from '../../components/PageHeader';
import { getSelection, saveSelection } from '../../models/selection';
import { getHospitals } from '../../services/registration';
import type { Hospital } from '../../typings/api';
import { filterHospitals, sortHospitals } from '../../utils/medical';

/** 以独立 H5 页面选择和筛选当前医院。 */
export default function HospitalsPage() {
  const navigate = useNavigate(); const [hospitals, setHospitals] = useState<Hospital[]>([]); const [keyword, setKeyword] = useState(''); const [notice, setNotice] = useState(''); const currentId = getSelection().hospitalId;
  useEffect(() => { void getHospitals().then((items) => setHospitals(sortHospitals(items))).catch((error) => setNotice(error.message || '医院列表加载失败')); }, []);
  const filtered = useMemo(() => filterHospitals(hospitals, keyword), [hospitals, keyword]);
  /** 保存所选医院并返回首页刷新医院展示。 */
  function chooseHospital(hospitalId: number) { saveSelection({ hospitalId }); navigate('/home'); }
  return <main className="subpage discovery-page"><PageHeader title="切换医院" /><section className="subpage-content"><div className="discovery-input"><Search size={22} /><input autoFocus value={keyword} placeholder="搜索医院名称" onChange={(event) => setKeyword(event.target.value)} />{keyword && <button type="button" onClick={() => setKeyword('')}>清空</button>}</div><p className="result-count">共 {filtered.length} 家医院</p>{filtered.map((hospital) => <button className={hospital.hospitalId === currentId ? 'hospital-item selected' : 'hospital-item'} key={hospital.hospitalId} type="button" onClick={() => chooseHospital(hospital.hospitalId)}><b>{hospital.name}</b><span>{hospital.level || '医院等级待完善'}</span><small>{hospital.address || '地址待完善'}</small></button>)}{!filtered.length && <p className="empty-state">未找到相关医院</p>}</section>{notice && <div className="toast" onClick={() => setNotice('')}>{notice}</div>}</main>;
}

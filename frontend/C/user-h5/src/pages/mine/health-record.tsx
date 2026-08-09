import { FormEvent, useEffect, useRef, useState } from 'react';
import { ChevronRight, ClipboardList, Pencil, Plus, Trash2 } from 'lucide-react';
import { useLocation, useNavigate } from 'umi';
import { Dialog } from '../../components/Dialog';
import { PageHeader } from '../../components/PageHeader';
import { getMinePatientId, resolveMinePatientId, saveMinePatientId } from '../../models/mine-patient';
import { getFamilyMembers } from '../../services/family';
import { createAllergy, createMedicalHistory, deleteAllergy, deleteMedicalHistory, getHealthRecord, updateAllergy, updateMedicalHistory } from '../../services/health';
import type { Allergy, FamilyMember, HealthRecord, MedicalHistory } from '../../typings/api';
import { createIdempotencyKey, getApiErrorMessage } from '../../utils/form';

type Editor = { type: 'allergy'; item?: Allergy } | { type: 'history'; item?: MedicalHistory } | null;
type DeleteTarget = { type: 'allergy' | 'history'; id: number; title: string } | null;

/** 查询并维护当前就诊人的过敏史和既往史。 */
export default function HealthRecordPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const patientIdFromUrl = Number(new URLSearchParams(location.search).get('patientId')) || undefined;
  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [patientId, setPatientId] = useState<number>();
  const [patientOpen, setPatientOpen] = useState(false);
  const [record, setRecord] = useState<HealthRecord | null>(null);
  const [editor, setEditor] = useState<Editor>(null);
  const [deleteTarget, setDeleteTarget] = useState<DeleteTarget>(null);
  const [allergen, setAllergen] = useState(''); const [reaction, setReaction] = useState('');
  const [content, setContent] = useState(''); const [occurredAt, setOccurredAt] = useState('');
  const [notice, setNotice] = useState(''); const [loading, setLoading] = useState(true); const [submitting, setSubmitting] = useState(false);
  const operationKey = useRef<string>();

  /** 初始化有效就诊人，优先保留入口传入的“我的”当前选择。 */
  async function initializePatient() {
    try {
      const nextMembers = await getFamilyMembers();
      setMembers(nextMembers);
      const resolvedPatientId = resolveMinePatientId(nextMembers, patientIdFromUrl || getMinePatientId());
      if (!resolvedPatientId) {
        setNotice('暂无可查询的就诊人');
        setLoading(false);
        return;
      }
      // 与“我的”同步当前选择，健康档案返回后仍能展示相同就诊人。
      saveMinePatientId(resolvedPatientId);
      setPatientId(resolvedPatientId);
    } catch (requestError) {
      setNotice(getApiErrorMessage(requestError));
      setLoading(false);
    }
  }

  /** 读取服务端健康档案，切换就诊人后重新请求。 */
  async function loadRecord(currentPatientId: number) { setLoading(true); try { setRecord(await getHealthRecord(currentPatientId)); } catch (requestError) { setNotice(getApiErrorMessage(requestError)); } finally { setLoading(false); } }
  useEffect(() => { void initializePatient(); }, [patientIdFromUrl]);
  useEffect(() => { if (patientId) void loadRecord(patientId); }, [patientId]);

  /** 切换健康档案就诊人，并同步“我的”页及地址栏的当前选择。 */
  function selectPatient(nextPatientId: number) {
    saveMinePatientId(nextPatientId);
    setPatientId(nextPatientId);
    setRecord(null);
    setEditor(null);
    setDeleteTarget(null);
    setPatientOpen(false);
    navigate(`/mine/health-record?patientId=${nextPatientId}`, { replace: true });
  }
  /** 初始化当前编辑的过敏史或既往史表单。 */
  function openEditor(nextEditor: Editor) { operationKey.current = undefined; setDeleteTarget(null); setEditor(nextEditor); if (nextEditor?.type === 'allergy') { setAllergen(nextEditor.item?.allergen || ''); setReaction(nextEditor.item?.reaction || ''); } if (nextEditor?.type === 'history') { setContent(nextEditor.item?.content || ''); setOccurredAt(nextEditor.item?.occurredAt || ''); } }
  /** 保存过敏史，并在网络重试时沿用当前幂等键。 */
  async function saveAllergy(event: FormEvent<HTMLFormElement>) { event.preventDefault(); if (!allergen.trim()) return setNotice('请填写过敏原'); if (!patientId) return setNotice('请先选择就诊人'); setSubmitting(true); const key = operationKey.current || (operationKey.current = createIdempotencyKey()); try { if (editor?.item) await updateAllergy(editor.item.id, { allergen: allergen.trim(), reaction: reaction.trim() || undefined }, key); else await createAllergy({ patientId, allergen: allergen.trim(), reaction: reaction.trim() || undefined }, key); operationKey.current = undefined; setEditor(null); await loadRecord(patientId); } catch (requestError) { setNotice(getApiErrorMessage(requestError)); } finally { setSubmitting(false); } }
  /** 保存既往史，并在网络重试时沿用当前幂等键。 */
  async function saveHistory(event: FormEvent<HTMLFormElement>) { event.preventDefault(); if (!content.trim()) return setNotice('请填写既往史内容'); if (!patientId) return setNotice('请先选择就诊人'); setSubmitting(true); const key = operationKey.current || (operationKey.current = createIdempotencyKey()); try { if (editor?.item) await updateMedicalHistory(editor.item.id, { content: content.trim(), occurredAt: occurredAt || undefined }, key); else await createMedicalHistory({ patientId, content: content.trim(), occurredAt: occurredAt || undefined }, key); operationKey.current = undefined; setEditor(null); await loadRecord(patientId); } catch (requestError) { setNotice(getApiErrorMessage(requestError)); } finally { setSubmitting(false); } }
  /** 确认软删除健康档案记录，网络重试沿用同一个幂等键。 */
  async function confirmDelete() { if (!deleteTarget || !patientId) return; setSubmitting(true); const key = operationKey.current || (operationKey.current = createIdempotencyKey()); try { if (deleteTarget.type === 'allergy') await deleteAllergy(deleteTarget.id, key); else await deleteMedicalHistory(deleteTarget.id, key); operationKey.current = undefined; setDeleteTarget(null); await loadRecord(patientId); } catch (requestError) { setNotice(getApiErrorMessage(requestError)); } finally { setSubmitting(false); } }
  return <main className="subpage"><PageHeader title="健康档案" /><section className="subpage-content">{loading ? <p className="empty-state">正在读取健康档案...</p> : record && <><button className="health-summary health-summary--switch" type="button" onClick={() => setPatientOpen(true)}><ClipboardList size={25} /><div><h2>{record.profile.name}的健康档案</h2><p>{record.summary || '暂未记录健康信息'}</p></div><ChevronRight size={20} aria-hidden="true" /></button><RecordSection title="过敏史" emptyText="暂无过敏史" onAdd={() => openEditor({ type: 'allergy' })}>{record.allergies.map((item) => <RecordItem key={item.id} title={item.allergen} detail={item.reaction || '未填写过敏反应'} onEdit={() => openEditor({ type: 'allergy', item })} onDelete={() => setDeleteTarget({ type: 'allergy', id: item.id, title: item.allergen })} />)}</RecordSection><RecordSection title="既往史" emptyText="暂无既往史" onAdd={() => openEditor({ type: 'history' })}>{record.medicalHistories.map((item) => <RecordItem key={item.id} title={item.content} detail={item.occurredAt || '未填写发生日期'} onEdit={() => openEditor({ type: 'history', item })} onDelete={() => setDeleteTarget({ type: 'history', id: item.id, title: item.content })} />)}</RecordSection></>}</section>{patientOpen && <Dialog title="切换就诊人" onClose={() => setPatientOpen(false)}>{members.map((member) => <button className="choice-row" key={member.patientId} type="button" onClick={() => selectPatient(member.patientId)}><span>{member.name}</span><small>{member.relationName || member.relation}{member.patientId === patientId ? ' · 当前选择' : ''}</small></button>)}</Dialog>}{deleteTarget && <Dialog title="确认删除健康记录" onClose={() => !submitting && setDeleteTarget(null)}><div className="health-delete-confirm"><p>删除后该记录将不再显示，历史数据会保留。确认删除“{deleteTarget.title}”吗？</p><button className="secondary-button" disabled={submitting} type="button" onClick={() => setDeleteTarget(null)}>取消</button><button className="primary-button" disabled={submitting} type="button" onClick={() => void confirmDelete()}>{submitting ? '删除中...' : '确认删除'}</button></div></Dialog>}{notice && <div className="toast" onClick={() => setNotice('')}>{notice}</div>}{editor?.type === 'allergy' && <Dialog title={editor.item ? '编辑过敏史' : '添加过敏史'} onClose={() => setEditor(null)}><form className="form-stack" onSubmit={saveAllergy}><label>过敏原<input value={allergen} maxLength={128} onChange={(event) => setAllergen(event.target.value)} /></label><label>过敏反应<input value={reaction} maxLength={512} placeholder="可选，如皮疹" onChange={(event) => setReaction(event.target.value)} /></label><button className="primary-button" disabled={submitting} type="submit">{submitting ? '保存中...' : '保存过敏史'}</button></form></Dialog>}{editor?.type === 'history' && <Dialog title={editor.item ? '编辑既往史' : '添加既往史'} onClose={() => setEditor(null)}><form className="form-stack" onSubmit={saveHistory}><label>既往史<textarea value={content} maxLength={2000} rows={4} onChange={(event) => setContent(event.target.value)} /></label><label>发生日期<input type="date" value={occurredAt} onChange={(event) => setOccurredAt(event.target.value)} /></label><button className="primary-button" disabled={submitting} type="submit">{submitting ? '保存中...' : '保存既往史'}</button></form></Dialog>}</main>;
}

/** 展示可新增的健康记录分组。 */
function RecordSection({ title, emptyText, onAdd, children }: { title: string; emptyText: string; onAdd: () => void; children: React.ReactNode }) { const hasItems = Array.isArray(children) ? children.length > 0 : Boolean(children); return <section className="record-section"><div className="section-title"><h2>{title}</h2><button className="text-button" type="button" onClick={onAdd}><Plus size={17} />添加</button></div>{hasItems ? <div className="record-list">{children}</div> : <p className="empty-state">{emptyText}</p>}</section>; }

/** 展示单条健康记录及编辑入口。 */
function RecordItem({ title, detail, onEdit, onDelete }: { title: string; detail: string; onEdit: () => void; onDelete: () => void }) { return <article className="record-item"><div><h3>{title}</h3><p>{detail}</p></div><div className="card-actions"><button className="icon-button" type="button" aria-label="编辑记录" onClick={onEdit}><Pencil size={18} /></button><button className="icon-button danger-icon" type="button" aria-label="删除记录" onClick={onDelete}><Trash2 size={18} /></button></div></article>; }

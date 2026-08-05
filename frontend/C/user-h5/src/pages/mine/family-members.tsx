import { FormEvent, useEffect, useRef, useState } from 'react';
import { Pencil, Plus, Trash2, UsersRound } from 'lucide-react';
import { useNavigate } from 'umi';
import { Dialog } from '../../components/Dialog';
import { PageHeader } from '../../components/PageHeader';
import { getMinePatientId, resolveMinePatientId, saveMinePatientId } from '../../models/mine-patient';
import { createFamilyMember, unbindFamilyMember, updateFamilyMember } from '../../services/family';
import { useFamilyMembers } from '../../hooks/useFamilyMembers';
import type { FamilyMember, FamilyMemberPayload } from '../../typings/api';
import { createIdempotencyKey, getApiErrorMessage, getRelationLabel, validateFamilyMember } from '../../utils/form';

const emptyMember: FamilyMemberPayload = { name: '', relation: 'CHILD', gender: 'UNKNOWN' };

/** 管理家庭成员，并选择“我的”页面当前就诊人。 */
export default function FamilyMembersPage() {
  const navigate = useNavigate();
  const [minePatientId, setMinePatientId] = useState<number>();
  const [editing, setEditing] = useState<FamilyMember | null | undefined>(undefined);
  const [form, setForm] = useState<FamilyMemberPayload>(emptyMember);
  const [notice, setNotice] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const operationKey = useRef<string>();
  const { data: members = [], isLoading: loading, error: membersError, refetch: refetchMembers } = useFamilyMembers();

  /** 根据共享成员数据恢复“我的”页面就诊人，解绑后自动回退本人。 */
  function syncMinePatient(nextMembers: FamilyMember[]) {
    const resolvedId = resolveMinePatientId(nextMembers, getMinePatientId());
    if (resolvedId) {
      saveMinePatientId(resolvedId);
      setMinePatientId(resolvedId);
    }
  }

  useEffect(() => { syncMinePatient(members); }, [members]);
  useEffect(() => { if (membersError) setNotice(getApiErrorMessage(membersError)); }, [membersError]);

  /** 写操作成功后主动刷新家庭成员，立即同步最新绑定关系。 */
  async function refreshMembers() {
    const result = await refetchMembers();
    syncMinePatient(result.data || []);
  }

  /** 选择成员作为“我的”页面当前就诊人后返回资料卡。 */
  function chooseMinePatient(member: FamilyMember) {
    saveMinePatientId(member.patientId);
    navigate('/mine');
  }

  /** 打开新增或编辑表单，并仅使用列表接口已返回的脱敏字段。 */
  function openEditor(member: FamilyMember | null) {
    operationKey.current = undefined;
    setEditing(member);
    // 脱敏手机号和身份证号不能回填，否则会覆盖服务端的完整资料。
    setForm(member ? { name: member.name, relation: member.relation === 'SELF' ? 'OTHER' : member.relation, gender: member.gender, birthday: member.birthday, phone: undefined, idCardNo: undefined } : emptyMember);
  }

  /** 新增或更新成员，网络重试期间沿用同一幂等键。 */
  async function submitMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const message = validateFamilyMember(form, !editing);
    if (message) return setNotice(message);
    setSubmitting(true);
    const key = operationKey.current || (operationKey.current = createIdempotencyKey());
    try {
      if (editing) await updateFamilyMember(editing.patientId, form, key);
      else await createFamilyMember(form, key);
      operationKey.current = undefined;
      setEditing(undefined);
      await refreshMembers();
    } catch (requestError) { setNotice(getApiErrorMessage(requestError)); }
    finally { setSubmitting(false); }
  }

  /** 解绑非本人家庭成员并刷新列表。 */
  async function removeMember(member: FamilyMember) {
    if (member.relation === 'SELF' || !window.confirm(`确认解绑${member.name}吗？`)) return;
    setSubmitting(true);
    const key = createIdempotencyKey();
    try { await unbindFamilyMember(member.patientId, key); await refreshMembers(); }
    catch (requestError) { setNotice(getApiErrorMessage(requestError)); }
    finally { setSubmitting(false); }
  }

  return <main className="subpage"><PageHeader title="就诊人管理" /><section className="subpage-content"><button className="add-button" type="button" onClick={() => openEditor(null)}><Plus size={19} />添加家庭成员</button>{loading ? <p className="empty-state">正在读取就诊人...</p> : <div className="member-list">{members.map((member) => {
    const isCurrent = member.patientId === minePatientId;
    return <article className={isCurrent ? 'member-card is-current' : 'member-card'} key={member.patientId}>
      <button className="member-card__select" type="button" onClick={() => chooseMinePatient(member)}><UsersRound size={28} /><div><h2>{member.name}{isCurrent ? <em>当前就诊人</em> : member.isDefault && <em>默认就诊人</em>}</h2><p>{getRelationLabel(member.relation)} · {member.gender === 'MALE' ? '男' : member.gender === 'FEMALE' ? '女' : '性别待完善'}</p><p>{member.phone || '联系电话待完善'}</p><p>身份证号：{member.idCardNo || '资料待完善'}</p></div></button>
      {member.relation !== 'SELF' && <div className="card-actions"><button className="icon-button" type="button" aria-label="编辑成员" onClick={(event) => { event.stopPropagation(); openEditor(member); }}><Pencil size={19} /></button><button className="icon-button danger-icon" disabled={submitting} type="button" aria-label="解绑成员" onClick={(event) => { event.stopPropagation(); void removeMember(member); }}><Trash2 size={19} /></button></div>}
    </article>;
  })}</div>}</section>{notice && <div className="toast" onClick={() => setNotice('')}>{notice}</div>}{editing !== undefined && <Dialog title={editing ? '编辑家庭成员' : '添加家庭成员'} onClose={() => setEditing(undefined)}><form className="form-stack" onSubmit={submitMember}><label>姓名<input required value={form.name} maxLength={64} onChange={(event) => setForm({ ...form, name: event.target.value })} /></label><label>关系<select value={form.relation} onChange={(event) => setForm({ ...form, relation: event.target.value as FamilyMemberPayload['relation'] })}><option value="SPOUSE">配偶</option><option value="PARENT">父母</option><option value="CHILD">子女</option><option value="OTHER">其他</option></select></label><label>性别<select value={form.gender || 'UNKNOWN'} onChange={(event) => setForm({ ...form, gender: event.target.value as FamilyMemberPayload['gender'] })}><option value="UNKNOWN">暂不填写</option><option value="MALE">男</option><option value="FEMALE">女</option></select></label><label>出生日期<input type="date" max={new Date().toISOString().slice(0, 10)} value={form.birthday || ''} onChange={(event) => setForm({ ...form, birthday: event.target.value || undefined })} /></label><label>手机号<input inputMode="numeric" value={form.phone || ''} placeholder="可选，11 位手机号" onChange={(event) => setForm({ ...form, phone: event.target.value || undefined })} /></label><label>{editing ? '新身份证号（可选）' : '身份证号（必填）'}<input required={!editing} inputMode="numeric" maxLength={18} value={form.idCardNo || ''} placeholder={editing ? '不修改请留空' : '请输入 15 或 18 位身份证号'} onChange={(event) => setForm({ ...form, idCardNo: event.target.value.replace(/\s/g, '').toUpperCase() || undefined })} /></label><button className="primary-button" disabled={submitting} type="submit">{submitting ? '保存中...' : '保存成员'}</button></form></Dialog>}</main>;
}

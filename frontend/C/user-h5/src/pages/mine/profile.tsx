import { FormEvent, useEffect, useRef, useState } from 'react';
import { ShieldCheck } from 'lucide-react';
import { PageHeader } from '../../components/PageHeader';
import { ApiError } from '../../services/request';
import { getProfile, updateProfile } from '../../services/profile';
import type { Profile } from '../../typings/api';
import { getApiErrorMessage } from '../../utils/form';
import { buildProfileUpdatePayload, resolveProfileIdempotencyKey, type ProfileFormValues, validateProfileForm } from '../../utils/profile';

const emptyForm: ProfileFormValues = { name: '', gender: '', birthday: '', phone: '', emergencyContact: '' };

/** 查询、展示并更新当前登录账号的本人资料。 */
export default function ProfilePage() {
  const [profile, setProfile] = useState<Profile>();
  const [form, setForm] = useState<ProfileFormValues>(emptyForm);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [notice, setNotice] = useState('');
  const idempotencyKeyRef = useRef<string>();

  /** 读取资料并仅预填服务端可安全回显的非敏感字段。 */
  async function loadProfile() {
    setLoading(true);
    try {
      const current = await getProfile();
      setProfile(current);
      // 脱敏手机号和紧急联系人不可回填为可编辑原值。
      setForm({ name: current.name, gender: current.gender || '', birthday: current.birthday || '', phone: '', emergencyContact: '' });
    } catch (error) {
      setNotice(getApiErrorMessage(error));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void loadProfile(); }, []);

  /** 提交局部资料更新，网络异常时保留首次幂等键供原请求重试。 */
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const message = validateProfileForm(form);
    if (message) {
      setNotice(message);
      return;
    }
    setSubmitting(true);
    try {
      const key = resolveProfileIdempotencyKey(idempotencyKeyRef.current);
      idempotencyKeyRef.current = key;
      await updateProfile(buildProfileUpdatePayload(form), key);
      // 成功后清除幂等键并重新查询，以获取脱敏后的完整最新资料。
      idempotencyKeyRef.current = undefined;
      await loadProfile();
      setNotice('个人资料已更新');
    } catch (error) {
      // 服务端已明确响应时允许用户修正表单后作为新请求提交；网络失败则保留原键重试。
      if (error instanceof ApiError && error.status) idempotencyKeyRef.current = undefined;
      setNotice(getApiErrorMessage(error));
    } finally {
      setSubmitting(false);
    }
  }

  return <main className="subpage profile-page">
    <PageHeader title="个人资料" backPath="/mine" />
    <section className="subpage-content">
      {loading && <p className="empty-state">正在读取个人资料...</p>}
      {!loading && profile && <>
        <section className="profile-safe-info"><ShieldCheck size={22} /><div><b>已保护的资料</b><span>手机号：{profile.phone || '暂未填写'}</span><span>紧急联系人：{profile.emergencyContact || '暂未填写'}</span></div></section>
        <form className="form-stack profile-form" onSubmit={submit}>
          <label>姓名<input value={form.name} maxLength={64} onChange={(event) => setForm({ ...form, name: event.target.value })} /></label>
          <label>性别<select value={form.gender} onChange={(event) => setForm({ ...form, gender: event.target.value as ProfileFormValues['gender'] })}><option value="">暂未填写</option><option value="MALE">男</option><option value="FEMALE">女</option><option value="UNKNOWN">未知</option></select></label>
          <label>出生日期<input type="date" max={new Date().toISOString().slice(0, 10)} value={form.birthday} onChange={(event) => setForm({ ...form, birthday: event.target.value })} /></label>
          <label>新手机号（可选）<input type="tel" inputMode="numeric" maxLength={11} placeholder="不修改请留空" value={form.phone} onChange={(event) => setForm({ ...form, phone: event.target.value })} /></label>
          <label>新紧急联系人（可选）<textarea rows={3} maxLength={256} placeholder="不修改请留空" value={form.emergencyContact} onChange={(event) => setForm({ ...form, emergencyContact: event.target.value })} /></label>
          <button className="primary-button" disabled={submitting} type="submit">{submitting ? '保存中...' : '保存资料'}</button>
        </form>
      </>}
      {!loading && !profile && <p className="empty-state">暂无可编辑的本人资料</p>}
    </section>
    {notice && <div className="toast" onClick={() => setNotice('')}>{notice}</div>}
  </main>;
}

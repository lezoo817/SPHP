import { useEffect, useState } from 'react';
import { Bell, BookHeart, ChevronRight, ClipboardPlus, FileChartColumn, HeartPulse, KeyRound, LogOut, MapPin, MessageSquare, Pill, ShieldCheck, UsersRound } from 'lucide-react';
import { useNavigate } from 'umi';
import { BottomTab } from '../../components/BottomTab';
import { Dialog } from '../../components/Dialog';
import { clearSession, getSession } from '../../models/session';
import { changePassword, logout } from '../../services/auth';
import { getProfile } from '../../services/profile';
import type { Profile } from '../../typings/api';
import { getApiErrorMessage, validatePassword } from '../../utils/form';

const healthEntries = [
  { label: '健康档案', icon: BookHeart, available: true }, { label: '我的处方', icon: ClipboardPlus },
  { label: '就诊记录', icon: HeartPulse }, { label: '报告查询', icon: FileChartColumn },
  { label: '用药提醒', icon: Pill }, { label: '随访计划', icon: HeartPulse }, { label: '通知消息', icon: Bell },
];

/** 提供“我的”首页、本人资料概览和已开放业务入口。 */
export default function MinePage() {
  const navigate = useNavigate();
  const [profile, setProfile] = useState<Profile>();
  const [loading, setLoading] = useState(true);
  const [notice, setNotice] = useState('');
  const [showPasswordDialog, setShowPasswordDialog] = useState(false);
  const [passwords, setPasswords] = useState({ oldPassword: '', newPassword: '', confirmPassword: '' });
  const [submitting, setSubmitting] = useState(false);

  /** 读取当前登录账号的本人资料，不以家庭成员默认项替代。 */
  async function loadProfile() {
    setLoading(true);
    try { setProfile(await getProfile()); }
    catch (requestError) { setNotice(getApiErrorMessage(requestError)); }
    finally { setLoading(false); }
  }

  useEffect(() => { void loadProfile(); }, []);

  /** 提交当前密码和新密码，成功后清除已失效会话。 */
  async function submitPasswordChange() {
    const message = validatePassword(passwords.oldPassword) || validatePassword(passwords.newPassword) || (passwords.newPassword !== passwords.confirmPassword ? '两次输入的新密码不一致' : undefined);
    if (message) return setNotice(message);
    setSubmitting(true);
    try {
      await changePassword({ oldPassword: passwords.oldPassword, newPassword: passwords.newPassword });
      clearSession(); setShowPasswordDialog(false); setNotice('密码已修改，请重新登录');
      window.setTimeout(() => navigate('/login'), 600);
    } catch (requestError) { setNotice(getApiErrorMessage(requestError)); }
    finally { setSubmitting(false); }
  }

  /** 二次确认后调用退出接口并清除本地会话。 */
  async function submitLogout() {
    const session = getSession();
    if (!session || !window.confirm('确认退出当前账号吗？')) return;
    setSubmitting(true);
    try { await logout(session.refreshToken); }
    catch (requestError) { setNotice(getApiErrorMessage(requestError)); }
    finally { clearSession(); setSubmitting(false); navigate('/login'); }
  }

  const genderText = profile?.gender === 'MALE' ? '男' : profile?.gender === 'FEMALE' ? '女' : profile?.gender === 'UNKNOWN' ? '未知' : '性别待完善';
  return <main className="mine-page"><header className="mine-hero"><span>我的</span><button className="more-button" aria-label="更多功能" type="button" onClick={() => setNotice('更多功能暂未开放')}>•••</button></header>
    <section className="profile-card">{loading ? <p>正在读取资料...</p> : profile ? <><div className="profile-card__top"><div><h1>{profile.name}<em>本人</em></h1><p>{genderText} · {profile.birthday || '生日待完善'}</p></div><ChevronRight color="#7a7477" /></div><div className="profile-lines"><p>手机号 <span>{profile.phone || '资料暂未完善'}</span></p></div><div className="profile-card__footer"><button type="button" className="text-button" onClick={() => navigate('/mine/family-members')}>管理就诊人</button><button type="button" className="text-button" onClick={() => navigate('/mine/profile')}>查看资料 <ChevronRight size={15} /></button></div></> : <p>暂无可展示的本人资料</p>}</section>
    <section className="mine-section"><h2>健康服务</h2><div className="health-grid">{healthEntries.map(({ label, icon: Icon, available }) => <button className="health-entry" key={label} type="button" onClick={() => available && profile ? navigate(`/mine/health-record?patientId=${profile.id}`) : setNotice(`${label}暂未开放`)}><Icon size={31} /><span>{label}</span></button>)}</div></section>
    <section className="mine-section"><h2>近期提醒</h2><div className="reminder-card"><p>当前暂无可联调的提醒数据</p><button type="button" className="text-button" onClick={() => setNotice('提醒服务暂未开放')}>查看</button></div></section>
    <section className="menu-card"><MenuItem icon={UsersRound} label="就诊人管理" onClick={() => navigate('/mine/family-members')} /><MenuItem icon={MapPin} label="我的地址" disabled onClick={() => setNotice('地址服务暂未开放')} /><MenuItem icon={ShieldCheck} label="账号与安全" onClick={() => setShowPasswordDialog(true)} /><MenuItem icon={MessageSquare} label="用户反馈" disabled onClick={() => setNotice('反馈服务暂未开放')} /></section>
    <button className="logout-button" disabled={submitting} type="button" onClick={submitLogout}><LogOut size={18} />退出登录</button>
    {notice && <div className="toast" role="status" onClick={() => setNotice('')}>{notice}</div>}
    {showPasswordDialog && <Dialog title="修改登录密码" onClose={() => setShowPasswordDialog(false)}><div className="form-stack"><label>当前密码<input type="password" autoComplete="current-password" value={passwords.oldPassword} onChange={(event) => setPasswords({ ...passwords, oldPassword: event.target.value })} /></label><label>新密码<input type="password" autoComplete="new-password" value={passwords.newPassword} onChange={(event) => setPasswords({ ...passwords, newPassword: event.target.value })} /></label><label>确认新密码<input type="password" autoComplete="new-password" value={passwords.confirmPassword} onChange={(event) => setPasswords({ ...passwords, confirmPassword: event.target.value })} /></label><button className="primary-button" disabled={submitting} type="button" onClick={submitPasswordChange}>{submitting ? '保存中...' : '保存新密码'}</button></div></Dialog>}
    <BottomTab onUnavailable={() => setNotice('该页面暂未开放')} />
  </main>;
}

/** 展示“我的”中的单行设置入口。 */
function MenuItem({ icon: Icon, label, onClick, disabled }: { icon: typeof UsersRound; label: string; onClick: () => void; disabled?: boolean }) {
  return <button className="menu-item" type="button" onClick={onClick}><Icon size={24} /><span>{label}</span>{disabled && <em>暂未开放</em>}<ChevronRight size={18} /></button>;
}

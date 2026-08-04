import { useEffect, useState } from 'react';
import { Bell, BookHeart, ChevronRight, ClipboardPlus, FileChartColumn, HeartPulse, LogOut, MapPin, Pill, ShieldCheck, UsersRound } from 'lucide-react';
import { useNavigate } from 'umi';
import { BottomTab } from '../../components/BottomTab';
import { Dialog } from '../../components/Dialog';
import { getMinePatientId, resolveMinePatientId, saveMinePatientId } from '../../models/mine-patient';
import { clearSession, getSession } from '../../models/session';
import { changePassword, logout } from '../../services/auth';
import { getFamilyMembers } from '../../services/family';
import { getNotifications } from '../../services/notification';
import { getProfile } from '../../services/profile';
import type { FamilyMember, Profile } from '../../typings/api';
import { getApiErrorMessage, getRelationLabel, validatePassword } from '../../utils/form';

const healthEntries = [
  { label: '健康档案', icon: BookHeart, path: 'health-record' },
  { label: '我的处方', icon: ClipboardPlus },
  { label: '就诊记录', icon: HeartPulse },
  { label: '报告查询', icon: FileChartColumn, path: '/reports?source=mine' },
  { label: '用药提醒', icon: Pill, path: '/mine/medication-plans' },
  { label: '随访计划', icon: HeartPulse, path: '/mine/follow-ups' },
  { label: '通知消息', icon: Bell, path: '/mine/notifications', showUnreadBadge: true },
];

/** 提供“我的”首页、专属当前就诊人资料概览和已开放业务入口。 */
export default function MinePage() {
  const navigate = useNavigate();
  const [profile, setProfile] = useState<Profile>();
  const [members, setMembers] = useState<FamilyMember[]>([]);
  const [minePatientId, setMinePatientId] = useState<number>();
  const [loading, setLoading] = useState(true);
  const [hasUnreadNotifications, setHasUnreadNotifications] = useState(false);
  const [notice, setNotice] = useState('');
  const [showPasswordDialog, setShowPasswordDialog] = useState(false);
  const [passwords, setPasswords] = useState({ oldPassword: '', newPassword: '', confirmPassword: '' });
  const [submitting, setSubmitting] = useState(false);

  /** 读取本人资料、家属与未读通知数量，并恢复“我的”页面独立选择。 */
  async function loadMineData() {
    setLoading(true);
    const [profileResult, membersResult, unreadResult] = await Promise.allSettled([
      getProfile(),
      getFamilyMembers(),
      // 仅读取未读总数，首页红点不需要加载完整通知内容。
      getNotifications({ read: false, pageNo: 1, pageSize: 1 }),
    ]);
    if (profileResult.status === 'fulfilled') setProfile(profileResult.value);
    else setNotice(getApiErrorMessage(profileResult.reason));
    if (membersResult.status === 'fulfilled') {
      const nextMembers = membersResult.value;
      setMembers(nextMembers);
      const resolvedId = resolveMinePatientId(nextMembers, getMinePatientId());
      if (resolvedId) {
        // 被解绑成员不再存在时，回退后的本人选择同步覆盖旧会话值。
        saveMinePatientId(resolvedId);
        setMinePatientId(resolvedId);
      }
    } else setNotice(getApiErrorMessage(membersResult.reason));
    if (unreadResult.status === 'fulfilled') setHasUnreadNotifications(unreadResult.value.total > 0);
    else setNotice(getApiErrorMessage(unreadResult.reason));
    setLoading(false);
  }

  useEffect(() => { void loadMineData(); }, []);

  /** 提交当前密码和新密码，成功后清除已失效会话。 */
  async function submitPasswordChange() {
    const message = validatePassword(passwords.oldPassword) || validatePassword(passwords.newPassword) || (passwords.newPassword !== passwords.confirmPassword ? '两次输入的新密码不一致' : undefined);
    if (message) return setNotice(message);
    setSubmitting(true);
    try {
      await changePassword({ oldPassword: passwords.oldPassword, newPassword: passwords.newPassword });
      clearSession();
      setShowPasswordDialog(false);
      setNotice('密码已修改，请重新登录');
      window.setTimeout(() => navigate('/login'), 600);
    } catch (requestError) {
      setNotice(getApiErrorMessage(requestError));
    } finally {
      setSubmitting(false);
    }
  }

  /** 二次确认后调用退出接口并清除本地会话。 */
  async function submitLogout() {
    const session = getSession();
    if (!session || !window.confirm('确认退出当前账号吗？')) return;
    setSubmitting(true);
    try {
      await logout(session.refreshToken);
    } catch (requestError) {
      setNotice(getApiErrorMessage(requestError));
    } finally {
      clearSession();
      setSubmitting(false);
      navigate('/login');
    }
  }

  /** 按服务入口类型跳转，健康档案需要携带当前就诊人。 */
  function openHealthEntry(path?: string) {
    if (!path) return setNotice('该服务暂未开放');
    if (path === 'health-record') {
      const patientId = selectedMember?.patientId || (isSelf ? profile?.id : undefined);
      if (!patientId) return setNotice('暂无可展示的就诊人资料');
      navigate(`/mine/health-record?patientId=${patientId}`);
      return;
    }
    navigate(path);
  }

  const selectedMember = members.find((member) => member.patientId === minePatientId);
  const isSelf = selectedMember?.relation === 'SELF';
  const current = isSelf && profile ? profile : selectedMember;
  const genderText = current?.gender === 'MALE' ? '男' : current?.gender === 'FEMALE' ? '女' : current?.gender === 'UNKNOWN' ? '未知' : '性别待完善';

  /** 进入当前就诊人的资料处理入口。 */
  function openCurrentPatientProfile() {
    if (isSelf) navigate('/mine/profile');
    else setNotice('家属资料请在就诊人管理中编辑');
  }

  return <main className="mine-page">
    <header className="mine-hero"><span>我的</span></header>
    <section className="profile-card">{loading ? <p>正在读取资料...</p> : current ? <><div className="profile-card__top"><div><h1>{current.name}<em>{isSelf ? '本人' : getRelationLabel(selectedMember?.relation || '')}</em></h1><p>{genderText} · {current.birthday || '生日待完善'}</p></div><button className="profile-card__switch icon-button" type="button" aria-label="管理就诊人" onClick={() => navigate('/mine/family-members')}><ChevronRight color="#7a7477" /></button></div><div className="profile-lines"><p>手机号 <span>{current.phone || '资料暂未完善'}</span></p></div><div className="profile-card__footer"><button type="button" className="text-button" onClick={() => navigate('/mine/family-members')}>管理就诊人</button><button type="button" className="text-button" onClick={openCurrentPatientProfile}>{isSelf ? '查看资料' : '管理资料'} <ChevronRight size={15} /></button></div></> : <p>暂无可展示的就诊人资料</p>}</section>
    <section className="mine-section"><h2>健康服务</h2><div className="health-grid">{healthEntries.map(({ label, icon: Icon, path, showUnreadBadge }) => <button className="health-entry" key={label} type="button" onClick={() => openHealthEntry(path)}><span className="health-entry__icon"><Icon size={34} />{showUnreadBadge && hasUnreadNotifications && <i className="health-entry__badge" aria-label="有未读消息" />}</span><span className="health-entry__label">{label}</span></button>)}</div></section>
    <section className="menu-card"><MenuItem icon={UsersRound} label="就诊人管理" onClick={() => navigate('/mine/family-members')} /><MenuItem icon={MapPin} label="我的地址" onClick={() => navigate('/mine/addresses')} /><MenuItem icon={ShieldCheck} label="账号与安全" onClick={() => setShowPasswordDialog(true)} /></section>
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

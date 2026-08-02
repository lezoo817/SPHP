import { FormEvent, useEffect, useState } from 'react';
import { Eye, EyeOff, LockKeyhole, RefreshCw, UserRound } from 'lucide-react';
import { useNavigate } from 'umi';
import { Dialog } from '../../components/Dialog';
import { getRememberedAccount, saveRememberedAccount, saveSession } from '../../models/session';
import { getCaptcha, login, register } from '../../services/auth';
import type { CaptchaData } from '../../typings/api';
import { getApiErrorMessage, validateAccount, validatePassword } from '../../utils/form';

interface RegisterState { account: string; password: string; captchaCode: string; }

/** 提供 C 端登录、居中验证码和注册流程。 */
export default function LoginPage() {
  const navigate = useNavigate();
  const [account, setAccount] = useState('');
  const [password, setPassword] = useState('');
  const [rememberAccount, setRememberAccount] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [captcha, setCaptcha] = useState<CaptchaData | null>(null);
  const [registerState, setRegisterState] = useState<RegisterState>({ account: '', password: '', captchaCode: '' });
  const [captchaSeconds, setCaptchaSeconds] = useState(0);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const remembered = getRememberedAccount();
    setAccount(remembered);
    setRememberAccount(Boolean(remembered));
  }, []);

  useEffect(() => {
    if (!captchaSeconds) return undefined;
    const timer = window.setInterval(() => setCaptchaSeconds((seconds) => Math.max(seconds - 1, 0)), 1000);
    return () => window.clearInterval(timer);
  }, [captchaSeconds]);

  /** 提交账号密码并保存会话级 Token。 */
  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const message = validateAccount(account) || validatePassword(password);
    if (message) return setError(message);
    setLoading(true); setError('');
    try {
      const data = await login({ account: account.trim(), password });
      // 仅保存 Token 对和用户摘要，密码始终停留在当前输入框。
      saveSession(data);
      saveRememberedAccount(account.trim(), rememberAccount);
      // 登录成功后进入首页，加载医院和就诊人上下文。
      navigate('/home');
    } catch (requestError) { setError(getApiErrorMessage(requestError)); }
    finally { setLoading(false); }
  }

  /** 获取一次性验证码并打开居中注册弹层。 */
  async function openRegister() {
    setLoading(true); setError('');
    try {
      const data = await getCaptcha();
      setCaptcha(data); setCaptchaSeconds(data.expireSeconds);
      setRegisterState({ account: account.trim(), password: '', captchaCode: '' });
    } catch (requestError) { setError(getApiErrorMessage(requestError)); }
    finally { setLoading(false); }
  }

  /** 刷新即将失效或已消费的验证码。 */
  async function refreshCaptcha() {
    setLoading(true);
    try { const data = await getCaptcha(); setCaptcha(data); setCaptchaSeconds(data.expireSeconds); setRegisterState((state) => ({ ...state, captchaCode: '' })); }
    catch (requestError) { setError(getApiErrorMessage(requestError)); }
    finally { setLoading(false); }
  }

  /** 校验验证码并提交注册账号请求。 */
  async function handleRegister(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!captcha || !captchaSeconds) return setError('验证码已失效，请换一张后重试');
    const message = validateAccount(registerState.account) || validatePassword(registerState.password) || (!registerState.captchaCode.trim() ? '请输入图形验证码' : undefined);
    if (message) return setError(message);
    setLoading(true); setError('');
    try {
      await register({ ...registerState, account: registerState.account.trim(), challengeId: captcha.challengeId });
      setAccount(registerState.account.trim()); setCaptcha(null);
      setError('注册成功，请使用新账号登录');
    } catch (requestError) {
      // 验证码只能使用一次，失败后主动换取新验证码。
      setError(getApiErrorMessage(requestError)); await refreshCaptcha();
    } finally { setLoading(false); }
  }

  return <main className="login-page">
    <section className="login-brand"><div className="brand-mark">智</div><div><strong>智愈先锋</strong><span>省人民医院智慧医疗服务</span></div></section>
    <section className="login-intro"><h1>安心就医，从这里开始</h1><p>登录后查看挂号、问诊、处方和健康提醒</p></section>
    <section className="login-card"><h2>账号登录</h2><p>请输入已注册的账号和密码</p>
      <form onSubmit={handleLogin} noValidate>
        <label>账号<div className="input-wrap"><UserRound size={22} /><input value={account} maxLength={32} placeholder="请输入账号" autoComplete="username" onChange={(event) => setAccount(event.target.value)} /></div></label>
        <label>密码<div className="input-wrap"><LockKeyhole size={22} /><input value={password} type={showPassword ? 'text' : 'password'} placeholder="请输入登录密码" autoComplete="current-password" onChange={(event) => setPassword(event.target.value)} /><button className="input-icon" type="button" aria-label={showPassword ? '隐藏密码' : '显示密码'} onClick={() => setShowPassword(!showPassword)}>{showPassword ? <EyeOff size={20} /> : <Eye size={20} />}</button></div></label>
        <div className="login-options"><label className="check-label"><input type="checkbox" checked={rememberAccount} onChange={(event) => setRememberAccount(event.target.checked)} />记住账号</label><button type="button" className="text-button" onClick={() => setError('密码找回功能暂未开放')}>忘记密码</button></div>
        {error && <p className={error.startsWith('注册成功') ? 'form-success' : 'form-error'}>{error}</p>}
        <button className="primary-button" disabled={loading} type="submit">{loading ? '处理中...' : '登录'}</button>
        <button className="secondary-button" disabled={loading} type="button" onClick={openRegister}>注册用户</button>
      </form>
    </section>
    {captcha && <Dialog title="注册用户" onClose={() => setCaptcha(null)}><p className="dialog-hint">请完成图形验证后填写账号和密码。</p><form className="register-form" onSubmit={handleRegister} noValidate>
      <div className="captcha-row"><img src={captcha.imageBase64} alt="图形验证码" /><button type="button" className="text-button" disabled={loading} onClick={refreshCaptcha}><RefreshCw size={16} />换一张</button></div>
      <p className="captcha-tip">验证码剩余 {captchaSeconds} 秒</p>
      <label>账号<input value={registerState.account} maxLength={32} placeholder="4 至 32 位账号" onChange={(event) => setRegisterState({ ...registerState, account: event.target.value })} /></label>
      <label>密码<input value={registerState.password} type="password" maxLength={64} placeholder="8 至 64 位密码" onChange={(event) => setRegisterState({ ...registerState, password: event.target.value })} /></label>
      <label>验证码<input value={registerState.captchaCode} placeholder="请输入图片中的验证码" onChange={(event) => setRegisterState({ ...registerState, captchaCode: event.target.value })} /></label>
      <button className="primary-button" disabled={loading || !captchaSeconds} type="submit">验证并注册</button>
    </form></Dialog>}
  </main>;
}

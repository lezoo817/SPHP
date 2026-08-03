import { useEffect } from 'react';
import { Outlet, useLocation, useNavigate } from 'umi';
import { clearSession, getSession, isSessionTokenExpired } from '../models/session';
import { AgentFloatingButton } from '../components/agent/AgentFloatingButton';
import '../styles/app.less';
import '../styles/health-notification.less';
import '../styles/delivery-address.less';

/** 提供全局样式、路由内容容器与 AI 助手悬浮入口。 */
export default function Layout() {
  const location = useLocation();
  const navigate = useNavigate();
  const isLoginPage = location.pathname === '/login';
  const isAgentPage = location.pathname === '/agent';
  const isAuthenticated = !isSessionTokenExpired(getSession());

  useEffect(() => {
    if (!isLoginPage && !isAuthenticated) {
      // 无令牌或本地令牌过期时替换当前历史记录，返回键不会回到业务页。
      clearSession();
      navigate('/login', { replace: true });
    }
  }, [isAuthenticated, isLoginPage, navigate]);

  if (!isLoginPage && !isAuthenticated) return null;
  return (
    <>
      <Outlet />
      {/* AI 助手悬浮球：登录页与 AI 助手页本身不展示 */}
      {isAuthenticated && !isLoginPage && !isAgentPage && (
        <AgentFloatingButton onClick={() => navigate('/agent', { state: { from: location.pathname } })} />
      )}
    </>
  );
}

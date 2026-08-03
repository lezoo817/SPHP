import { useEffect } from 'react';
import { Outlet, useLocation, useNavigate } from 'umi';
import { clearSession, getSession, isSessionTokenExpired } from '../models/session';
import '../styles/app.less';

/** 提供全局样式与路由内容容器。 */
export default function Layout() {
  const location = useLocation();
  const navigate = useNavigate();
  const isLoginPage = location.pathname === '/login';
  const isAuthenticated = !isSessionTokenExpired(getSession());

  useEffect(() => {
    if (!isLoginPage && !isAuthenticated) {
      // 无令牌或本地令牌过期时替换当前历史记录，返回键不会回到业务页。
      clearSession();
      navigate('/login', { replace: true });
    }
  }, [isAuthenticated, isLoginPage, navigate]);

  if (!isLoginPage && !isAuthenticated) return null;
  return <Outlet />;
}

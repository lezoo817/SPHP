import { Outlet } from 'umi';
import '../styles/app.less';

/** 提供全局样式与路由内容容器。 */
export default function Layout() {
  return <Outlet />;
}

import { ArrowLeft } from 'lucide-react';
import { useNavigate } from 'umi';

/**
 * 显示二级页面标题与返回入口。
 * @param props 页面标题和可选的返回路径
 * @returns 二级页面顶部导航
 */
export function PageHeader({ title, backPath = '/mine' }: { title: string; backPath?: string }) {
  const navigate = useNavigate();
  return (
    <header className="page-header">
      <button className="icon-button" type="button" aria-label="返回上一页" onClick={() => navigate(backPath)}><ArrowLeft size={22} /></button>
      <h1>{title}</h1>
      <span />
    </header>
  );
}

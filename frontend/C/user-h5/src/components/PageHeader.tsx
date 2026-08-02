import { ArrowLeft } from 'lucide-react';
import { useNavigate } from 'umi';

/** 显示二级页面标题与返回入口。 */
export function PageHeader({ title }: { title: string }) {
  const navigate = useNavigate();
  return (
    <header className="page-header">
      <button className="icon-button" type="button" aria-label="返回我的" onClick={() => navigate('/mine')}><ArrowLeft size={22} /></button>
      <h1>{title}</h1>
      <span />
    </header>
  );
}

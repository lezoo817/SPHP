import { ArrowLeft, House } from 'lucide-react';
import { useNavigate } from 'umi';

/** 二级页面头的展示与跳转配置。 */
export interface PageHeaderProps {
  /** 页面标题。 */
  title: string;
  /** 返回图标的目标路径。 */
  backPath?: string;
  /** 自定义返回操作，需保留路由 state 时使用。 */
  onBack?: () => void;
  /** 是否显示首页快捷入口，支付流程应关闭以减少中断操作。 */
  showHome?: boolean;
  /** 首页快捷入口的目标路径。 */
  homePath?: string;
}

/**
 * 显示二级页面标题与返回入口。
 * @param props 页面标题、返回路径和首页快捷入口配置
 * @returns 二级页面顶部导航
 */
export function PageHeader({ title, backPath = '/mine', onBack, showHome = true, homePath = '/home' }: PageHeaderProps) {
  const navigate = useNavigate();
  return (
    <header className="page-header">
      <div className="page-header__controls">
        <button className="icon-button" type="button" aria-label="返回上一页" onClick={() => onBack ? onBack() : navigate(backPath)}><ArrowLeft size={22} /></button>
        {showHome && <button className="page-header__home icon-button" type="button" aria-label="返回首页" onClick={() => navigate(homePath)}><House size={19} /></button>}
      </div>
      <h1>{title}</h1>
      <span aria-hidden="true" />
    </header>
  );
}

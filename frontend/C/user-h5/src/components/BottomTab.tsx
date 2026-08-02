import { CirclePlus, House, Pill, UserRound } from 'lucide-react';
import { useLocation, useNavigate } from 'umi';

const tabs = [
  { label: '首页', path: '/home', icon: House },
  { label: '就诊助手', path: '/assistant', icon: CirclePlus },
  { label: '购药', path: '/pharmacy', icon: Pill },
  { label: '我的', path: '/mine', icon: UserRound },
];

/** 展示 C 端四项底部导航，未实现页面保留提示。 */
export function BottomTab({ onUnavailable }: { onUnavailable: () => void }) {
  const location = useLocation();
  const navigate = useNavigate();
  return <nav className="bottom-tab" aria-label="主导航">{tabs.map(({ label, path, icon: Icon }) => {
    const active = location.pathname.startsWith(path);
    return <button className={active ? 'bottom-tab__item is-active' : 'bottom-tab__item'} key={path} type="button" onClick={() => ['/mine', '/home', '/assistant'].includes(path) ? navigate(path) : onUnavailable()}>
      <Icon size={25} strokeWidth={active ? 2.6 : 2} /><span>{label}</span>
    </button>;
  })}</nav>;
}

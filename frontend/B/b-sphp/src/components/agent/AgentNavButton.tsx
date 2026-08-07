import { useState } from 'react';
import { useAccess, useNavigate } from '@umijs/max';
import { AppstoreOutlined } from '@ant-design/icons';
import { Button, Popover } from 'antd';
import { AGENT_NAV_GROUPS, type AgentNavItem } from '@/constants/agent';

interface AgentNavButtonProps {
  /** 跳转后回调（抽屉场景用于关闭自身） */
  onNavigate?: () => void;
}

/** AI 助手导航按钮：点击弹出按模块分组的 Popover 网格，点选即跳转对应模块。 */
export function AgentNavButton({ onNavigate }: AgentNavButtonProps) {
  const [open, setOpen] = useState(false);
  const { isAdmin, canAudit } = useAccess();
  const navigate = useNavigate();

  /** 点击模块后跳转并关闭 Popover。 */
  function handleNavigate(path: string) {
    navigate(path);
    setOpen(false);
    onNavigate?.();
  }

  /** 按角色过滤空组，构建可见分组列表。 */
  const visibleGroups: { label: string; items: AgentNavItem[] }[] = [];
  for (const group of AGENT_NAV_GROUPS) {
    const items = group.items.filter((item) => {
      if (item.requireAdmin && !isAdmin) return false;
      if (item.requireAudit && !canAudit) return false;
      if (item.hideForAdmin && isAdmin) return false;
      return true;
    });
    if (items.length > 0) {
      visibleGroups.push({ label: group.label, items });
    }
  }

  if (visibleGroups.length === 0) return null;

  return (
    <Popover
      trigger="click"
      placement="bottomRight"
      open={open}
      onOpenChange={setOpen}
      content={
        <div className="agent-nav">
          {visibleGroups.map((group) => (
            <div className="agent-nav__group" key={group.label}>
              <span className="agent-nav__group-title">{group.label}</span>
              <div className="agent-nav__grid">
                {group.items.map((item) => (
                  <button
                    key={item.key}
                    type="button"
                    className="agent-nav__item"
                    onClick={() => handleNavigate(item.key)}
                  >
                    {item.label}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      }
    >
      <Button type="text" size="small" icon={<AppstoreOutlined />} title="功能导航" />
    </Popover>
  );
}

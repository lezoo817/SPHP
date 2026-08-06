import { useEffect, useState } from 'react';
import { ArrowLeft } from 'lucide-react';
import { useLocation, useNavigate } from 'umi';
import { AgentChat } from '../../components/agent/AgentChat';
import { resolveAgentContext } from '../../models/agent';
import type { AgentChatContext, AgentNavigationState, AgentPresetAction } from '../../typings/agent';

/** AI 助手全屏会话页。 */
export default function AgentPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const routeState = location.state as AgentNavigationState | null;
  const fromPath = routeState?.from || location.pathname;
  const presetAction = resolvePresetAction(routeState?.presetAction);
  // 异步加载完整对话上下文（默认收货地址 + 基础字段）
  const [context, setContext] = useState<AgentChatContext | undefined>(undefined);

  useEffect(() => {
    let cancelled = false;
    resolveAgentContext(fromPath).then((next) => {
      if (!cancelled) setContext(next);
    });
    return () => { cancelled = true; };
  }, [fromPath]);

  return (
    <main className="agent-page">
      <header className="agent-page__header">
        <button type="button" className="agent-page__back" onClick={() => navigate(-1)} aria-label="返回">
          <ArrowLeft size={22} />
        </button>
        <h1>AI 助手</h1>
      </header>
      <AgentChat context={context} presetAction={presetAction} />
    </main>
  );
}

/**
 * 校验路由状态中的一次性预设动作。
 * @param action 路由状态传入的候选动作
 * @returns 合法预设动作；非法数据不触发自动调用
 */
function resolvePresetAction(action: AgentPresetAction | undefined): AgentPresetAction | undefined {
  if (action?.type !== 'interpret_prescription' || !Number.isInteger(action.prescriptionId) || action.prescriptionId <= 0) {
    return undefined;
  }
  return action;
}

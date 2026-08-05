import { useEffect, useState } from 'react';
import { ArrowLeft } from 'lucide-react';
import { useLocation, useNavigate } from 'umi';
import { AgentChat } from '../../components/agent/AgentChat';
import { resolveAgentContext } from '../../models/agent';
import type { AgentChatContext } from '../../typings/agent';

/** AI 助手全屏会话页。 */
export default function AgentPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const fromPath = (location.state as { from?: string } | null)?.from || location.pathname;
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
      <AgentChat context={context} />
    </main>
  );
}

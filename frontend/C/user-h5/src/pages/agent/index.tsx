import { ArrowLeft } from 'lucide-react';
import { useLocation, useNavigate } from 'umi';
import { AgentChat } from '../../components/agent/AgentChat';
import { buildAgentContext } from '../../models/agent';

/** AI 助手全屏会话页。 */
export default function AgentPage() {
  const navigate = useNavigate();
  const location = useLocation();
  // 基于来源页路径构造上下文（医院、就诊人、当前页面）
  const context = buildAgentContext((location.state as { from?: string } | null)?.from || location.pathname);

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

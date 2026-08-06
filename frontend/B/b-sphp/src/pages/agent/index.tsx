/**
 * B 端 AI 助手全屏会话页。
 *
 * 全屏形态；嵌入式形态见接诊台 ConsultDetail 右侧 AiPanel。
 */
import { useLocation } from '@umijs/max';
import { Card } from 'antd';
import { AiPanel } from '@/components/agent/AiPanel';
import { buildAgentContext } from '@/models/agent';

export default function AgentPage() {
  const location = useLocation();
  // 基于来源页路径构造上下文（医院、医生、当前页面）
  const context = buildAgentContext(
    (location.state as { from?: string } | null)?.from || location.pathname,
  );

  return (
    <Card
      styles={{ body: { padding: 0, height: 'calc(100vh - 140px)', overflow: 'hidden' } }}
      style={{ height: 'calc(100vh - 140px)' }}
    >
      <AiPanel context={context} embedded={false} />
    </Card>
  );
}

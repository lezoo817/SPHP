/**
 * B 端 AI 辅助面板（AiPanel）。
 *
 * 双形态：
 * 1. 嵌入式（embedded=true）：作为接诊台详情页右侧侧栏渲染，宽度由父容器控制；
 * 2. 全屏式（embedded=false）：作为独立 AI 助手页主体渲染，自带历史会话抽屉。
 *
 * 通过 Antd 组件实现消息流、连接状态、错误提示、L2 确认卡片、历史会话管理。
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Drawer,
  Empty,
  Input,
  Space,
  Spin,
  Tag,
  Typography,
  theme,
} from 'antd';
import {
  ClockCircleOutlined,
  DeleteOutlined,
  PlusOutlined,
  RobotOutlined,
  SendOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { useAgentStream } from '@/hooks/useAgentStream';
import {
  AGENT_CONTENT_MAX,
  AGENT_QUICK_PROMPTS,
  AGENT_UNAVAILABLE_TEXT,
  AGENT_WELCOME,
} from '@/constants/agent';
import { AgentMessageBubble } from './AgentMessage';
import { AgentThoughtPanel } from './AgentThought';
import { AgentToolCardView } from './AgentToolCard';
import { AgentConfirmCardView } from './AgentConfirmCard';
import { AgentActionCardView } from './AgentActionCard';
import { AgentSelectCardView } from './AgentSelectCard';
import { AgentNavButton } from './AgentNavButton';
import type { AgentActionCard, AgentChatContext, AgentSession } from '@/typings/agent';
import './agent.css';

const { Text, Paragraph } = Typography;
const { TextArea } = Input;

/** 会话时间格式化。 */
function formatSessionTime(isoString: string): string {
  const date = new Date(isoString);
  const now = new Date();
  const isToday = date.toDateString() === now.toDateString();
  const yesterday = new Date(now);
  yesterday.setDate(yesterday.getDate() - 1);
  const isYesterday = date.toDateString() === yesterday.toDateString();
  if (isToday) return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  if (isYesterday) return '昨天';
  return date.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' });
}

export interface AiPanelProps {
  /** 对话上下文（接诊台场景携带 consultation_id / patient_id） */
  context?: AgentChatContext;
  /** 是否嵌入式渲染（接诊台侧栏）。默认 false（全屏页形态）。 */
  embedded?: boolean;
  /** 嵌入式场景下，切换患者时由父组件触发会话重置（传入新的 consultationId 即重置） */
  consultationId?: number;
  /** 跳转到业务模块后的回调（抽屉场景用于关闭自身） */
  onNavigate?: () => void;
}

export function AiPanel({ context, embedded = false, consultationId, onNavigate }: AiPanelProps) {
  const {
    entries,
    connection,
    errorMessage,
    isStreaming,
    sessionId,
    send,
    confirm,
    selectOption,
    cancel,
    retry,
    reset,
    loadSession,
    sessions,
    sessionsLoading,
    refreshSessions,
    removeSession,
  } = useAgentStream();
  const { token } = theme.useToken();
  const [input, setInput] = useState('');
  const [showHistory, setShowHistory] = useState(false);
  const listRef = useRef<HTMLDivElement>(null);

  // 切换患者（consultationId 变化）时重置会话（一次问诊一个会话策略）
  useEffect(() => {
    if (embedded && consultationId !== undefined) {
      reset();
    }
    // 仅依赖 consultationId，不依赖 reset 以避免循环
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [embedded, consultationId]);

  // 新消息到达或流式累加时，自动滚动到底部
  useEffect(() => {
    if (listRef.current) listRef.current.scrollTop = listRef.current.scrollHeight;
  }, [entries]);

  // 打开历史会话抽屉时刷新列表
  useEffect(() => {
    if (showHistory) void refreshSessions();
  }, [showHistory, refreshSessions]);

  /** 提交当前输入。 */
  const handleSubmit = useCallback(() => {
    const text = input.trim();
    if (!text || isStreaming) return;
    send(text, context);
    setInput('');
  }, [input, isStreaming, send, context]);

  /** 点击快捷入口。 */
  const handleQuick = useCallback(
    (prompt: string) => {
      if (isStreaming) return;
      send(prompt, context);
    },
    [isStreaming, send, context],
  );

  /** 非 L2 业务交互卡：发送 buttonText 作为用户消息触发动作。 */
  const handleAction = useCallback(
    (card: AgentActionCard) => {
      if (isStreaming) return;
      send(card.buttonText, context);
    },
    [isStreaming, send, context],
  );

  /** 选择历史会话。 */
  const handleSelectSession = useCallback(
    (targetSessionId: string) => {
      setShowHistory(false);
      void loadSession(targetSessionId);
    },
    [loadSession],
  );

  /** 删除历史会话。 */
  const handleDeleteSession = useCallback(
    async (targetSessionId: string, e: React.MouseEvent) => {
      e.stopPropagation();
      try {
        await removeSession(targetSessionId);
      } catch (err) {
        // 静默处理，仅记录
        // eslint-disable-next-line no-console
        console.warn('删除会话失败：', (err as Error).message);
      }
    },
    [removeSession],
  );

  /** 新建会话。 */
  const handleNewSession = useCallback(() => {
    setShowHistory(false);
    reset();
  }, [reset]);

  const showWelcome = entries.length === 0;
  const containerStyle: React.CSSProperties = embedded
    ? {
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        minHeight: 0,
        background: token.colorBgContainer,
      }
    : {
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        minHeight: 0,
      };

  return (
    <div style={containerStyle}>
      {/* 顶部工具栏 */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          padding: '8px 12px',
          borderBottom: `1px solid ${token.colorBorderSecondary}`,
        }}
      >
        <RobotOutlined style={{ color: token.colorPrimary }} />
        <Text strong style={{ fontSize: 14 }}>
          AI 辅助助手
        </Text>
        <Tag
          color={
            connection === 'streaming'
              ? 'processing'
              : connection === 'connecting'
                ? 'processing'
                : connection === 'error'
                  ? 'error'
                  : 'default'
          }
          style={{ marginLeft: 'auto' }}
        >
          {isStreaming && <Spin size="small" style={{ marginRight: 4 }} />}
          {connection === 'connecting' && '连接中'}
          {connection === 'streaming' && '回复中'}
          {connection === 'error' && '连接异常'}
          {connection === 'idle' && '在线'}
        </Tag>
        <Space size={4}>
          {entries.length > 0 && (
            <Button
              type="text"
              size="small"
              icon={<PlusOutlined />}
              onClick={handleNewSession}
              title="新会话"
            />
          )}
          <Button
            type="text"
            size="small"
            icon={<ClockCircleOutlined />}
            onClick={() => setShowHistory(true)}
            title="历史会话"
          />
          <AgentNavButton onNavigate={onNavigate} />
        </Space>
      </div>

      {/* 错误提示 */}
      {errorMessage && (
        <Alert
          type="error"
          message={errorMessage}
          showIcon
          banner
          style={{ borderRadius: 0 }}
          action={
            <Button size="small" onClick={retry}>
              重试
            </Button>
          }
        />
      )}

      {/* 消息列表 */}
      <div
        ref={listRef}
        style={{
          flex: 1,
          overflowY: 'auto',
          padding: 12,
          minHeight: 0,
          background: embedded ? token.colorBgLayout : undefined,
        }}
      >
        {showWelcome && (
          <div style={{ textAlign: 'center', padding: '24px 8px' }}>
            <RobotOutlined style={{ fontSize: 36, color: token.colorPrimary, marginBottom: 12 }} />
            <Paragraph type="secondary" style={{ marginBottom: 16 }}>
              {AGENT_WELCOME}
            </Paragraph>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                gap: 8,
                marginTop: 8,
              }}
            >
              {AGENT_QUICK_PROMPTS.map((prompt) => (
                <Button
                  key={prompt.label}
                  size="small"
                  disabled={isStreaming}
                  onClick={() => handleQuick(prompt.content)}
                  style={{ textAlign: 'left' }}
                >
                  {prompt.label}
                </Button>
              ))}
            </div>
          </div>
        )}

        {entries.map((entry) => {
          if (entry.kind === 'message') return <AgentMessageBubble key={entry.data.id} message={entry.data} />;
          if (entry.kind === 'thought') return <AgentThoughtPanel key={entry.data.id} thought={entry.data} />;
          if (entry.kind === 'tool') return <AgentToolCardView key={entry.data.id} card={entry.data} />;
          if (entry.kind === 'card')
            return (
              <AgentConfirmCardView
                key={entry.data.id}
                card={entry.data}
                onConfirm={(card) => void confirm(card)}
              />
            );
          if (entry.kind === 'action')
            return (
              <AgentActionCardView
                key={entry.data.id}
                card={entry.data}
                disabled={isStreaming}
                onAction={handleAction}
              />
            );
          if (entry.kind === 'select')
            return (
              <AgentSelectCardView
                key={entry.data.id}
                card={entry.data}
                disabled={isStreaming}
                onSelect={(item) => selectOption(entry.data, item)}
              />
            );
          return null;
        })}

        {isStreaming && entries.length > 0 && (
          <div style={{ textAlign: 'center', marginTop: 8 }}>
            <Button size="small" icon={<StopOutlined />} onClick={cancel}>
              停止生成
            </Button>
          </div>
        )}
      </div>

      {/* 输入区 */}
      <div style={{ padding: 12, borderTop: `1px solid ${token.colorBorderSecondary}` }}>
        <TextArea
          value={input}
          onChange={(e) => setInput(e.target.value.slice(0, AGENT_CONTENT_MAX))}
          placeholder="输入咨询内容，1 至 2000 字（Ctrl+Enter 发送）"
          autoSize={{ minRows: 1, maxRows: 4 }}
          disabled={isStreaming}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
              e.preventDefault();
              handleSubmit();
            }
          }}
        />
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginTop: 4,
          }}
        >
          <Text type="secondary" style={{ fontSize: 11 }}>
            {input.length}/{AGENT_CONTENT_MAX}
            {sessionId && ` · 会话 ${sessionId.slice(0, 12)}`}
          </Text>
          <Button
            type="primary"
            icon={<SendOutlined />}
            disabled={isStreaming || !input.trim()}
            onClick={handleSubmit}
          >
            发送
          </Button>
        </div>
      </div>

      {/* 历史会话抽屉 */}
      <Drawer
        title="历史会话"
        open={showHistory}
        onClose={() => setShowHistory(false)}
        width={360}
        styles={{ body: { padding: 0 } }}
      >
        {sessionsLoading ? (
          <div style={{ textAlign: 'center', padding: 24 }}>
            <Spin />
          </div>
        ) : sessions.length === 0 ? (
          <Empty description="暂无历史会话" style={{ marginTop: 48 }} />
        ) : (
          <div>
            {sessions.map((session: AgentSession) => (
              <div
                key={session.session_id}
                style={{
                  padding: '12px 16px',
                  borderBottom: `1px solid ${token.colorBorderSecondary}`,
                  cursor: 'pointer',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 8,
                }}
                onClick={() => handleSelectSession(session.session_id)}
                className="agent-session-item"
              >
                <div style={{ flex: 1, minWidth: 0 }}>
                  <Text strong ellipsis style={{ display: 'block' }}>
                    {session.title || '新会话'}
                  </Text>
                  <Text type="secondary" ellipsis style={{ display: 'block', fontSize: 12 }}>
                    {session.last_message || '暂无消息'}
                  </Text>
                  <Space size={8} style={{ marginTop: 4 }}>
                    <Text type="secondary" style={{ fontSize: 11 }}>
                      {session.message_count} 轮
                    </Text>
                    <Text type="secondary" style={{ fontSize: 11 }}>
                      {formatSessionTime(session.updated_at)}
                    </Text>
                  </Space>
                </div>
                <Button
                  type="text"
                  size="small"
                  icon={<DeleteOutlined />}
                  onClick={(e) => void handleDeleteSession(session.session_id, e)}
                  title="删除会话"
                />
              </div>
            ))}
          </div>
        )}
      </Drawer>

      {/* 降级提示（SSE 连接失败时展示） */}
      {connection === 'error' && !errorMessage && (
        <Alert
          type="warning"
          message={AGENT_UNAVAILABLE_TEXT}
          banner
          style={{ borderRadius: 0 }}
        />
      )}
    </div>
  );
}

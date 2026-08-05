/**
 * 渲染单条对话消息（医生或 AI）。
 *
 * 使用 Antd Typography 按角色区分样式：医生消息右对齐，AI 消息左对齐并附带免责声明。
 */
import { Typography } from 'antd';
import { RobotOutlined, UserOutlined } from '@ant-design/icons';
import { AGENT_DISCLAIMER } from '../../constants/agent';

const { Text, Paragraph } = Typography;

export function AgentMessageBubble({ message }: { message: Agent.Message }) {
  const isUser = message.role === 'user';
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: isUser ? 'row-reverse' : 'row',
        gap: 8,
        marginBottom: 12,
      }}
    >
      <div
        style={{
          width: 32,
          height: 32,
          borderRadius: '50%',
          background: isUser ? '#1677ff' : '#f0f5ff',
          color: isUser ? '#fff' : '#1677ff',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
        }}
      >
        {isUser ? <UserOutlined /> : <RobotOutlined />}
      </div>
      <div
        style={{
          maxWidth: '78%',
          padding: '8px 12px',
          borderRadius: 8,
          background: isUser ? '#1677ff' : '#f5f5f5',
          color: isUser ? '#fff' : 'inherit',
        }}
      >
        <Paragraph
          style={{ margin: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}
          type={isUser ? undefined : 'secondary'}
        >
          {message.content || (message.streaming ? '…' : '')}
        </Paragraph>
        {!isUser && message.content && !message.streaming && (
          <Text
            type="secondary"
            style={{ fontSize: 12, display: 'block', marginTop: 4, opacity: 0.7 }}
          >
            {AGENT_DISCLAIMER}
          </Text>
        )}
      </div>
    </div>
  );
}

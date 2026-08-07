import type { AgentMessage } from '@/typings/agent';
import { AGENT_DISCLAIMER } from '@/constants/agent';

/**
 * 清洗 AI 消息中的 Markdown 标记，让聊天气泡呈现更清爽。
 *
 * LLM（DeepSeek 等）默认用 Markdown 排版（**粗体**、`代码`、- 列表项、# 标题），
 * 但当前气泡以纯文本渲染，不解析 Markdown，导致 `**`、`-` 等符号直接暴露给用户。
 * 保留语义：去除标记符但保留语义文本（粗体/斜体只剥外壳，反引号只剥包围，
 * 列表项的 `-` 换成更友好的 `•`，标题 `#` 整段去除）。
 *
 * 不解析为富文本，避免引入额外依赖与复杂的样式回归；后续如需富文本展示，
 * 可平滑切换到 react-markdown。
 */
function cleanMarkdown(text: string): string {
  return text
    // 粗体：**text** -> text（先剥双星，避免斜体正则误伤）
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    // 斜体：*text* -> text（要求前后非 *，防止重复剥除）
    .replace(/(?<!\*)\*([^*\n]+)\*(?!\*)/g, '$1')
    .replace(/(?<!\w)_([^_\n]+)_(?!\w)/g, '$1')
    // 行内代码：`code` -> code
    .replace(/`([^`]+)`/g, '$1')
    // 行首无序列表标记 - 或 *（后跟空格或 tab） -> •
    .replace(/^[ \t]*[-*][ \t]+/gm, '• ')
    // 行首标题：# ## ### 等（最多 6 级）
    .replace(/^[ \t]*#{1,6}[ \t]+/gm, '')
    // 链接 [text](url) -> text
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1');
}

/** 渲染单条对话消息（用户或 AI）。 */
export function AgentMessageBubble({ message }: { message: AgentMessage }) {
  const isUser = message.role === 'user';
  // AI 消息：清洗 Markdown 标记；用户消息按原样展示。
  const displayText = isUser ? message.content : cleanMarkdown(message.content || '');
  return (
    <div className={isUser ? 'agent-msg agent-msg--user' : 'agent-msg agent-msg--ai'}>
      {!isUser && <div className="agent-msg__avatar">AI</div>}
      <div className="agent-msg__bubble">
        <p className="agent-msg__text">{displayText || (message.streaming ? '…' : '')}</p>
        {!isUser && message.content && !message.streaming && (
          <p className="agent-msg__disclaimer">{AGENT_DISCLAIMER}</p>
        )}
      </div>
    </div>
  );
}

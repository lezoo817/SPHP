/**
 * 推理过程折叠面板：默认折叠，按需展开查看 thought 增量。
 *
 * 仅推理模型（如 DeepSeek-R1）触发 thought 事件，使用 Antd Collapse 展示。
 */
import { Collapse, Typography } from 'antd';
import { BulbOutlined } from '@ant-design/icons';

const { Paragraph } = Typography;

export function AgentThoughtPanel({ thought }: { thought: Agent.Thought }) {
  if (!thought.content) return null;
  return (
    <Collapse
      ghost
      size="small"
      style={{ marginBottom: 8 }}
      items={[
        {
          key: 'thought',
          label: (
            <span style={{ color: '#8c8c8c', fontSize: 12 }}>
              <BulbOutlined /> 思考过程
            </span>
          ),
          children: (
            <Paragraph
              type="secondary"
              style={{ margin: 0, whiteSpace: 'pre-wrap', fontSize: 12 }}
            >
              {thought.content}
            </Paragraph>
          ),
        },
      ]}
    />
  );
}

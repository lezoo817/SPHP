/**
 * 工具调用卡片：action 与 observation 配对，展示 loading / 成功 / 失败。
 *
 * 使用 Antd Card + Tag 展示工具名、参数、结果摘要，可展开查看完整参数与结果。
 */
import { useState } from 'react';
import { Card, Tag, Typography, Collapse, theme } from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
  ToolOutlined,
} from '@ant-design/icons';

const { Text, Paragraph } = Typography;

/** 工具状态对应的 Tag 颜色与文案。 */
function statusTag(status: Agent.ToolCard['status']): { color: string; text: string; icon: React.ReactNode } {
  switch (status) {
    case 'loading':
      return { color: 'processing', text: '调用中', icon: <LoadingOutlined /> };
    case 'success':
      return { color: 'success', text: '成功', icon: <CheckCircleOutlined /> };
    case 'error':
      return { color: 'error', text: '失败', icon: <CloseCircleOutlined /> };
    default:
      return { color: 'default', text: '未知', icon: null };
  }
}

export function AgentToolCardView({ card }: { card: Agent.ToolCard }) {
  const { token } = theme.useToken();
  const [expanded, setExpanded] = useState(false);
  const hasArgs = card.arguments && Object.keys(card.arguments).length > 0;
  const hasResult = card.result !== undefined && card.result !== null;
  const tag = statusTag(card.status);

  return (
    <Card
      size="small"
      style={{
        marginBottom: 8,
        borderColor:
          card.status === 'error'
            ? token.colorErrorBorder
            : card.status === 'success'
              ? token.colorSuccessBorder
              : token.colorBorderSecondary,
      }}
      styles={{
        body: { padding: 8 },
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
        <ToolOutlined style={{ color: token.colorPrimary }} />
        <Text strong style={{ fontSize: 13 }}>
          {card.label}
        </Text>
        <Tag color={tag.color} style={{ marginLeft: 'auto', marginRight: 0 }}>
          {tag.icon} {tag.text}
        </Tag>
      </div>

      {card.summary && (
        <Paragraph type="secondary" style={{ margin: 0, fontSize: 12 }}>
          {card.summary}
          {typeof card.durationMs === 'number' && ` · ${card.durationMs}ms`}
        </Paragraph>
      )}
      {card.error && (
        <Paragraph type="danger" style={{ margin: '4px 0 0', fontSize: 12 }}>
          {card.error}
        </Paragraph>
      )}

      {(hasArgs || hasResult) && (
        <Collapse
          ghost
          size="small"
          style={{ marginTop: 4 }}
          activeKey={expanded ? ['detail'] : []}
          onChange={(keys) => setExpanded(keys.includes('detail'))}
          items={[
            {
              key: 'detail',
              label: <Text type="secondary" style={{ fontSize: 12 }}>{expanded ? '收起详情' : '查看详情'}</Text>,
              children: (
                <div style={{ fontSize: 12 }}>
                  {hasArgs && (
                    <div style={{ marginBottom: 4 }}>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        参数
                      </Text>
                      <pre
                        style={{
                          margin: '4px 0',
                          padding: 8,
                          background: token.colorFillTertiary,
                          borderRadius: 4,
                          fontSize: 12,
                          overflow: 'auto',
                          maxHeight: 160,
                        }}
                      >
                        {JSON.stringify(card.arguments, null, 2)}
                      </pre>
                    </div>
                  )}
                  {hasResult && (
                    <div>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        结果
                      </Text>
                      <pre
                        style={{
                          margin: '4px 0',
                          padding: 8,
                          background: token.colorFillTertiary,
                          borderRadius: 4,
                          fontSize: 12,
                          overflow: 'auto',
                          maxHeight: 200,
                        }}
                      >
                        {JSON.stringify(card.result, null, 2)}
                      </pre>
                    </div>
                  )}
                </div>
              ),
            },
          ]}
        />
      )}
    </Card>
  );
}

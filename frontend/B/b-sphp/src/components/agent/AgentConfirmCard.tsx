/**
 * L2 操作确认卡片。
 *
 * 状态机：pending -> confirming -> done / error / expired。
 * 令牌过期或已消费时置灰禁用，不再调用确认接口。
 * 使用 Antd Card + Descriptions + Button 展示详情与确认按钮。
 */
import { useState } from 'react';
import { Button, Card, Descriptions, Tag, Typography, theme } from 'antd';
import {
  CheckCircleFilled,
  CloseCircleFilled,
  ExclamationCircleFilled,
  LoadingOutlined,
} from '@ant-design/icons';
import { AGENT_CONFIRM_ERROR_TEXT } from '../../constants/agent';

const { Text, Paragraph } = Typography;

/** details 字段中文标签映射。 */
const DETAIL_LABEL_MAP: Record<string, string> = {
  department_name: '科室',
  doctor_name: '医生',
  schedule_time: '就诊时段',
  fee_cent: '挂号费',
  appointment_id: '挂号订单',
  chief_complaint: '主诉',
  history_summary: '现病史',
  message_preview: '消息预览',
  pharmacy_name: '药房',
  drug_list: '药品',
  total_cent: '金额',
  drug_order_id: '购药订单',
  allergen: '过敏原',
  reaction: '过敏反应',
  action: '操作',
  content: '内容',
  occurred_at: '发生日期',
  report_name: '报告名称',
  report_date: '报告日期',
  indicators: '指标',
  plan_name: '用药计划',
  follow_up_type: '随访类型',
  remind_at: '提醒时间',
  patient_name: '患者',
  note_preview: '草稿预览',
};

/** 渲染 details 关键字段为可读键值对。 */
function renderDetails(details: Record<string, unknown> | undefined): { key: string; label: string; value: string }[] {
  if (!details) return [];
  return Object.entries(details)
    .filter(([, v]) => v !== null && v !== undefined && v !== '')
    .map(([k, v]) => ({
      key: k,
      label: DETAIL_LABEL_MAP[k] || k,
      value: typeof v === 'object' ? JSON.stringify(v) : String(v),
    }));
}

/** 金额分转元展示。 */
function formatAmount(value: string, key: string): string {
  if ((key === 'fee_cent' || key === 'total_cent') && /^\d+$/.test(value)) {
    return `¥${(Number(value) / 100).toFixed(2)}`;
  }
  return value;
}

export function AgentConfirmCardView({
  card,
  onConfirm,
}: {
  card: Agent.ConfirmCard;
  onConfirm: (card: Agent.ConfirmCard) => void;
}) {
  const { token } = theme.useToken();
  const [expanded, setExpanded] = useState(false);
  const detailRows = renderDetails(card.details);
  const disabled =
    card.status === 'done' || card.status === 'error' || card.status === 'expired' || card.status === 'confirming';
  const expired = card.status === 'expired' || (!!card.expiresAt && Date.parse(card.expiresAt) <= Date.now());

  const statusIcon = () => {
    if (card.status === 'done') return <CheckCircleFilled style={{ color: token.colorSuccess }} />;
    if (card.status === 'error' || card.status === 'expired')
      return <CloseCircleFilled style={{ color: token.colorError }} />;
    return <ExclamationCircleFilled style={{ color: token.colorWarning }} />;
  };

  return (
    <Card
      size="small"
      style={{
        marginBottom: 8,
        borderColor:
          card.status === 'error' || card.status === 'expired'
            ? token.colorErrorBorder
            : card.status === 'done'
              ? token.colorSuccessBorder
              : token.colorWarningBorder,
      }}
      styles={{ body: { padding: 8 } }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
        {statusIcon()}
        <Text strong style={{ fontSize: 13 }}>
          {card.title}
        </Text>
        {card.status !== 'pending' && card.status !== 'confirming' && (
          <Tag
            color={
              card.status === 'done' ? 'success' : card.status === 'expired' ? 'default' : 'error'
            }
            style={{ marginLeft: 'auto', marginRight: 0 }}
          >
            {card.status === 'done' ? '已完成' : card.status === 'expired' ? '已过期' : '失败'}
          </Tag>
        )}
      </div>

      <Paragraph type="secondary" style={{ margin: 0, fontSize: 12 }}>
        {card.summary}
      </Paragraph>

      {detailRows.length > 0 && (
        <>
          <Button
            type="link"
            size="small"
            style={{ padding: '4px 0', fontSize: 12 }}
            onClick={() => setExpanded((v) => !v)}
          >
            {expanded ? '收起详情' : '查看详情'}
          </Button>
          {expanded && (
            <Descriptions
              size="small"
              column={1}
              colon={false}
              style={{ marginTop: 4 }}
              items={detailRows.map((row) => ({
                key: row.key,
                label: <Text type="secondary" style={{ fontSize: 12 }}>{row.label}</Text>,
                children: <Text style={{ fontSize: 12 }}>{formatAmount(row.value, row.key)}</Text>,
              }))}
            />
          )}
        </>
      )}

      {card.expiresAt && card.status === 'pending' && (
        <Text type="secondary" style={{ display: 'block', marginTop: 4, fontSize: 11 }}>
          令牌有效期至：{new Date(card.expiresAt).toLocaleString('zh-CN')}
        </Text>
      )}

      {card.status === 'done' && card.resultMessage && (
        <Paragraph style={{ margin: '4px 0 0', fontSize: 12, color: token.colorSuccess }}>
          {card.resultMessage}
        </Paragraph>
      )}

      {(card.status === 'error' || card.status === 'expired') && card.errorMessage && (
        <Paragraph type="danger" style={{ margin: '4px 0 0', fontSize: 12 }}>
          {card.errorMessage || AGENT_CONFIRM_ERROR_TEXT[card.errorCode || ''] || '操作失败'}
        </Paragraph>
      )}

      {card.status === 'pending' && (
        <Button
          type="primary"
          size="small"
          block
          disabled={expired}
          onClick={() => onConfirm(card)}
          style={{ marginTop: 8 }}
        >
          {expired ? '已过期' : '确认操作'}
        </Button>
      )}

      {card.status === 'confirming' && (
        <Button type="primary" size="small" block disabled style={{ marginTop: 8 }}>
          <LoadingOutlined /> 确认中…
        </Button>
      )}
    </Card>
  );
}

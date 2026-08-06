/**
 * 待审核处方列表列配置。
 *
 * 通过 getColumns(deps) 工厂生成：风险列展示预警标签（稳定 key），
 * 操作列依赖查看/通过/驳回回调。
 */
import { Tag, Button, Space, Typography } from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import type { ProColumns } from '@ant-design/pro-components';
import dayjs from 'dayjs';

const { Text } = Typography;

/** 待审核状态标签（固定为橙色「待审核」，简化处理） */
const STATUS_TAG = { text: '待审核', color: 'orange' };

/** 风险预警稳定 key：同一规则+级别视为同一条，避免用数组下标 */
const riskKey = (w: API.RiskWarning) => `${w.level}-${w.rule}`;

interface ColumnsDeps {
  onViewDetail: (id: number) => void;
  onApprove: (id: number) => void;
  onReject: (id: number) => void;
}

export function getColumns(deps: ColumnsDeps): ProColumns<API.PendingAuditItem>[] {
  const { onViewDetail, onApprove, onReject } = deps;

  return [
    {
      title: '处方ID',
      dataIndex: 'id',
      width: 80,
      hideInSearch: true,
    },
    {
      title: '患者',
      dataIndex: 'patientName',
      width: 100,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '医生',
      dataIndex: 'doctorName',
      width: 100,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '科室',
      dataIndex: 'deptName',
      width: 120,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '药品数',
      dataIndex: 'itemCount',
      width: 70,
      align: 'right',
      hideInSearch: true,
      render: (_: unknown, record: API.PendingAuditItem) => `${record.itemCount} 项`,
    },
    {
      title: '状态',
      width: 80,
      hideInSearch: true,
      render: () => <Tag color={STATUS_TAG.color}>{STATUS_TAG.text}</Tag>,
    },
    {
      title: '风险',
      dataIndex: 'riskWarnings',
      width: 180,
      ellipsis: true,
      hideInSearch: true,
      render: (_: unknown, record: API.PendingAuditItem) => {
        const warnings = record.riskWarnings;
        if (!warnings || warnings.length === 0) {
          return <Text type="secondary">-</Text>;
        }
        return (
          <Space size={4} wrap>
            {warnings.map((w) => (
              <Tag key={riskKey(w)} color={w.level === 'AUDIT' ? 'red' : 'orange'}>
                {w.rule}
              </Tag>
            ))}
          </Space>
        );
      },
    },
    {
      title: '提交时间',
      dataIndex: 'createdAt',
      width: 160,
      hideInSearch: true,
      // issuedAt 仅在审核通过后写入，待审核状态一律展示 createdAt
      render: (_: unknown, record: API.PendingAuditItem) =>
        dayjs(record.createdAt).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: '操作',
      width: 220,
      hideInSearch: true,
      render: (_: unknown, record: API.PendingAuditItem) => (
        <Space size={0}>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => onViewDetail(record.id)}
          >
            查看
          </Button>
          <Button
            type="link"
            size="small"
            icon={<CheckCircleOutlined />}
            style={{ color: '#52c41a' }}
            onClick={() => onApprove(record.id)}
          >
            通过
          </Button>
          <Button
            type="link"
            size="small"
            danger
            icon={<CloseCircleOutlined />}
            onClick={() => onReject(record.id)}
          >
            驳回
          </Button>
        </Space>
      ),
    },
  ];
}

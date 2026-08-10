/**
 * 处方列表页列配置。
 *
 * 通过 getColumns(deps) 工厂生成：操作列依赖查看详情/提交回调；
 * 搜索项（问诊ID/患者ID）置于列表首部且隐藏于表格（hideInTable）。
 */
import { Tag, Button, Space, Select, Tooltip, Typography } from 'antd';
import { ExclamationCircleOutlined, EyeOutlined, SendOutlined } from '@ant-design/icons';
import type { ProColumns } from '@ant-design/pro-components';
import dayjs from 'dayjs';
import { STATUS_MAP, STATUS_OPTIONS } from '../constants';
import { STATUS_DRAFT } from '@/constants/businessStatus';

const { Text } = Typography;

interface ColumnsDeps {
  onViewDetail: (id: number) => void;
  onSubmit: (record: API.Prescription) => void;
}

export function getColumns(deps: ColumnsDeps): ProColumns<API.Prescription>[] {
  const { onViewDetail, onSubmit } = deps;

  return [
    // 搜索项：问诊ID、患者ID（隐藏于表格，仅用于筛选）
    {
      title: '问诊ID',
      dataIndex: 'consultId',
      valueType: 'digit',
      hideInTable: true,
    },
    {
      title: '患者ID',
      dataIndex: 'patientId',
      valueType: 'digit',
      hideInTable: true,
    },
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
      render: (_: unknown, record: API.Prescription) => `${record.itemCount} 项`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      valueEnum: {
        DRAFT: { text: '草稿', status: 'Default' },
        SUBMITTED: { text: '待审核', status: 'Processing' },
        APPROVED: { text: '已通过', status: 'Success' },
        REJECTED: { text: '已驳回', status: 'Error' },
      },
      renderFormItem: () => (
        <Select allowClear placeholder="全部" options={STATUS_OPTIONS} mode="multiple" />
      ),
      render: (_: unknown, record: API.Prescription) => {
        const s = STATUS_MAP[record.status];
        return <Tag color={s?.color}>{s?.text ?? record.status}</Tag>;
      },
    },
    {
      title: '风险',
      dataIndex: 'riskWarnings',
      width: 120,
      hideInSearch: true,
      render: (_: unknown, record: API.Prescription) => {
        const warnings = record.riskWarnings ?? [];
        if (warnings.length === 0) return <Text type="secondary">-</Text>;
        // 命中审核级（AUDIT，高危）优先标红，仅提示（WARNING，重复用药）标黄
        const hasAudit = warnings.some((w) => w.level === 'AUDIT');
        return (
          <Tooltip
            title={
              <Space direction="vertical" size={2}>
                {warnings.map((w, i) => (
                  <span key={i}>
                    {w.level === 'AUDIT' ? '⚠️' : 'ℹ️'} {w.message}
                  </span>
                ))}
              </Space>
            }
          >
            <Tag color={hasAudit ? 'red' : 'orange'} style={{ cursor: 'pointer' }}>
              <ExclamationCircleOutlined /> {warnings.length} 条风险
            </Tag>
          </Tooltip>
        );
      },
    },
    {
      title: '开具时间',
      dataIndex: 'issuedAt',
      width: 160,
      hideInSearch: true,
      render: (_: unknown, record: API.Prescription) =>
        record.issuedAt ? dayjs(record.issuedAt).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: '操作',
      width: 200,
      hideInSearch: true,
      render: (_: unknown, record: API.Prescription) => (
        <Space size={0}>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => onViewDetail(record.id)}
          >
            查看
          </Button>
          {record.status === STATUS_DRAFT && (
            <Button
              type="link"
              size="small"
              icon={<SendOutlined />}
              onClick={() => onSubmit(record)}
            >
              提交
            </Button>
          )}
        </Space>
      ),
    },
  ];
}

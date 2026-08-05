/**
 * 处方列表页
 * - ProTable 列表，支持问诊ID/患者ID/状态筛选
 * - DRAFT 状态可提交处方（调用 POST /api/b/prescriptions）
 * - 提交后处理红线拦截 / 待审核（含重复用药、高危风险）两种结果
 * - 查看详情弹窗
 */
import {
  Tag,
  Button,
  Modal,
  message,
  Select,
  Space,
  Table,
  Descriptions,
  Typography,
} from 'antd';
import {
  EyeOutlined,
  SendOutlined,
} from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import { useNavigate, useLocation } from '@umijs/max';
import { useRef, useState, useEffect, useMemo } from 'react';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  getPrescriptions,
  getPrescriptionDetail,
  submitPrescription,
} from '@/services/admin';
import dayjs from 'dayjs';

const { Text } = Typography;

/** 处方状态映射 */
const STATUS_MAP: Record<string, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  SUBMITTED: { text: '待审核', color: 'orange' },
  APPROVED: { text: '已通过', color: 'green' },
  REJECTED: { text: '已驳回', color: 'red' },
};

const STATUS_OPTIONS = [
  { label: '草稿', value: 'DRAFT' },
  { label: '待审核', value: 'SUBMITTED' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已驳回', value: 'REJECTED' },
];

export default function PrescriptionList() {
  const navigate = useNavigate();
  const location = useLocation();
  const actionRef = useRef<ActionType>();

  // 详情弹窗
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailData, setDetailData] = useState<API.PrescriptionDetail | null>(null);

  /** 从 URL 参数恢复状态筛选（如 /prescription/list?status=SUBMITTED 深链进入） */
  const [urlStatus, setUrlStatus] = useState<string | undefined>(() => {
    const params = new URLSearchParams(location.search);
    return params.get('status') || undefined;
  });

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    setUrlStatus(params.get('status') || undefined);
  }, [location.search]);

  /** 透传给 ProTable 的筛选参数（无 URL 状态时保持稳定引用，避免多余请求） */
  const proParams = useMemo(() => (urlStatus ? { status: urlStatus } : {}), [urlStatus]);

  /** 查看详情 */
  const handleViewDetail = async (id: number) => {
    setDetailLoading(true);
    setDetailOpen(true);
    setDetailData(null);
    try {
      const data = await getPrescriptionDetail(id);
      setDetailData(data);
    } catch (err: any) {
      message.error(err?.message || '加载处方详情失败');
      setDetailOpen(false);
    } finally {
      setDetailLoading(false);
    }
  };

  /** 提交处方 */
  const handleSubmit = async (record: API.Prescription) => {
    Modal.confirm({
      title: '提交处方',
      content: `确定提交处方 #${record.id} 吗？提交后进入审核流程。`,
      okText: '确认提交',
      onOk: async () => {
        try {
          // 先获取详情拿到完整 items，再提交
          const detail = await getPrescriptionDetail(record.id);
          const result = await submitPrescription({
            consultId: detail.consultId,
            items: detail.items.map((item) => ({
              drugId: item.drugId,
              dosage: item.dosage,
              frequency: item.frequency,
              usageMethod: item.usageMethod,
              days: item.days,
              quantity: item.quantity,
            })),
          });

          // 红线拦截 — 理论上不会走到这里（后端返回 3004 抛异常），兜底处理
          if (result.riskWarnings?.some((w) => w.level === 'ERROR')) {
            const errMsg = result.riskWarnings.find((w) => w.level === 'ERROR')?.message;
            message.error(errMsg || '红线规则拦截，请修改后重提');
            return;
          }

          if (result.auditRequired) {
            // 命中风险规则（重复用药/高危）→ 进入待审核
            Modal.success({
              title: '提交成功',
              content: '处方已提交审核，请等待审核结果',
              okText: '前往待审核列表',
              onOk: () => navigate('/prescription/pending-audit'),
            });
          } else {
            message.success('处方提交成功');
          }

          actionRef.current?.reload();
        } catch (err: any) {
          // 处理 code 3004 红线拦截
          const errMsg = err?.message || err?.data?.message || '提交失败';
          Modal.error({
            title: '提交被拦截',
            content: errMsg,
            okText: '修改后重提',
          });
        }
      },
    });
  };

  const columns: ProColumns<API.Prescription>[] = [
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
      render: (_, record) => `${record.itemCount} 项`,
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
      render: (_, record) => {
        const s = STATUS_MAP[record.status];
        return <Tag color={s?.color}>{s?.text ?? record.status}</Tag>;
      },
    },
    {
      title: '开具时间',
      dataIndex: 'issuedAt',
      width: 160,
      hideInSearch: true,
      render: (_, record) => record.issuedAt
        ? dayjs(record.issuedAt).format('YYYY-MM-DD HH:mm')
        : '-',
    },
    {
      title: '操作',
      width: 200,
      hideInSearch: true,
      render: (_, record) => (
        <Space size={0}>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(record.id)}
          >
            查看
          </Button>
          {record.status === 'DRAFT' && (
            <Button
              type="link"
              size="small"
              icon={<SendOutlined />}
              onClick={() => handleSubmit(record)}
            >
              提交
            </Button>
          )}
        </Space>
      ),
    },
  ];

  // 搜索项：问诊ID、患者ID
  columns.splice(0, 0, {
    title: '问诊ID',
    dataIndex: 'consultId',
    valueType: 'digit',
    hideInTable: true,
  });
  columns.splice(1, 0, {
    title: '患者ID',
    dataIndex: 'patientId',
    valueType: 'digit',
    hideInTable: true,
  });

  return (
    <>
      <ProTable<API.Prescription, API.PrescriptionListParams>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        params={proParams}
        request={async (params) => {
          const { current, pageSize, ...rest } = params;
          try {
            const res = await getPrescriptions({
              page: current,
              size: pageSize,
              consultId: rest.consultId,
              patientId: rest.patientId,
              status: rest.status,
            });
            return { data: res.list, total: res.total, success: true };
          } catch (err: any) {
            message.error(err?.message || '查询失败');
            return { data: [], total: 0, success: true };
          }
        }}
        search={{
          labelWidth: 'auto',
          span: 6,
          defaultFormItemsNumber: 4,
        }}
        pagination={{ pageSize: 10, showSizeChanger: true }}
      />

      {/* 处方详情弹窗 */}
      <Modal
        title={`处方详情 #${detailData?.id ?? ''}`}
        open={detailOpen}
        footer={null}
        onCancel={() => setDetailOpen(false)}
        width={640}
        destroyOnClose
      >
        {detailLoading ? (
          <div style={{ textAlign: 'center', padding: 40 }}>加载中...</div>
        ) : detailData ? (
          <>
            <Descriptions size="small" column={2} bordered style={{ marginBottom: 16 }}>
              <Descriptions.Item label="患者">{detailData.patientName}</Descriptions.Item>
              <Descriptions.Item label="医生">{detailData.doctorName}</Descriptions.Item>
              <Descriptions.Item label="科室">{detailData.deptName}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={STATUS_MAP[detailData.status]?.color}>
                  {STATUS_MAP[detailData.status]?.text ?? detailData.status}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="创建时间">
                {dayjs(detailData.createdAt).format('YYYY-MM-DD HH:mm')}
              </Descriptions.Item>
              {detailData.rejectReason && (
                <Descriptions.Item label="驳回原因" span={2}>
                  <Text type="danger">{detailData.rejectReason}</Text>
                </Descriptions.Item>
              )}
            </Descriptions>

            <Text strong style={{ display: 'block', marginBottom: 8 }}>
              处方明细（{detailData.items.length} 项）
            </Text>
            <Table
              dataSource={detailData.items}
              rowKey="id"
              size="small"
              pagination={false}
              columns={[
                { title: '药品', dataIndex: 'drugName', width: 140 },
                { title: '用量', dataIndex: 'dosage', width: 80 },
                { title: '频次', dataIndex: 'frequency', width: 100 },
                { title: '用法', dataIndex: 'usageMethod', width: 80 },
                { title: '天数', dataIndex: 'days', width: 60, align: 'right' },
                { title: '数量', dataIndex: 'quantity', width: 60, align: 'right' },
              ]}
            />
          </>
        ) : (
          <div style={{ textAlign: 'center', padding: 40, color: '#999' }}>加载失败</div>
        )}
      </Modal>
    </>
  );
}
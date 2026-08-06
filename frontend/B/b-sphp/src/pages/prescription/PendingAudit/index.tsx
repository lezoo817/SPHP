/**
 * 待审核处方列表页
 * - 仅 canAudit（ADMIN / DEPT_HEAD）角色可访问（路由层级 access 控制）
 * - 审核操作：通过（APPROVED）/ 驳回（REJECTED，需填写原因）
 * - 审核完成后刷新本列表，同时通知处方列表页刷新缓存
 */
import {
  Tag,
  Button,
  Modal,
  message,
  Space,
  Typography,
  Input,
  Descriptions,
  Table,
  Alert,
} from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import { useRef, useState } from 'react';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  getPendingAudits,
  getPrescriptionDetail,
  auditPrescription,
} from '@/services/admin';
import dayjs from 'dayjs';

const { Text } = Typography;
const { TextArea } = Input;

/** 待审核状态标签（固定为橙色「待审核」，简化处理） */
const STATUS_TAG = { text: '待审核', color: 'orange' };

export default function PendingAudit() {
  const actionRef = useRef<ActionType>();

  // 详情弹窗
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailData, setDetailData] = useState<API.PrescriptionDetail | null>(null);

  // 驳回弹窗
  const [rejectOpen, setRejectOpen] = useState(false);
  const [rejectTargetId, setRejectTargetId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [rejecting, setRejecting] = useState(false);

  // 通过确认
  const [approvingId, setApprovingId] = useState<number | null>(null);

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

  /** 通过审核 */
  const handleApprove = (id: number) => {
    Modal.confirm({
      title: '通过审核',
      icon: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
      content: '确定通过该处方审核吗？',
      okText: '确认通过',
      onOk: async () => {
        try {
          await auditPrescription(id, { action: 'APPROVED' });
          message.success('处方审核已通过');
          actionRef.current?.reload();
        } catch (err: any) {
          message.error(err?.message || '审核失败');
        }
      },
    });
  };

  /** 打开驳回弹窗 */
  const handleRejectClick = (id: number) => {
    setRejectTargetId(id);
    setRejectReason('');
    setRejectOpen(true);
  };

  /** 提交驳回 */
  const handleRejectSubmit = async () => {
    if (!rejectTargetId) return;
    if (!rejectReason.trim()) {
      message.warning('请填写驳回原因');
      return;
    }
    setRejecting(true);
    try {
      await auditPrescription(rejectTargetId, {
        action: 'REJECTED',
        rejectReason: rejectReason.trim(),
      });
      message.success('处方已驳回');
      setRejectOpen(false);
      actionRef.current?.reload();
    } catch (err: any) {
      message.error(err?.message || '驳回失败');
    } finally {
      setRejecting(false);
    }
  };

  const columns: ProColumns<API.PendingAuditItem>[] = [
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
      render: (_, record) => {
        const warnings = record.riskWarnings;
        if (!warnings || warnings.length === 0) {
          return <Text type="secondary">-</Text>;
        }
        return (
          <Space size={4} wrap>
            {warnings.map((w, i) => (
              <Tag key={i} color={w.level === 'AUDIT' ? 'red' : 'orange'}>
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
      render: (_, record) => dayjs(record.createdAt).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: '操作',
      width: 220,
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
          <Button
            type="link"
            size="small"
            icon={<CheckCircleOutlined />}
            style={{ color: '#52c41a' }}
            onClick={() => handleApprove(record.id)}
          >
            通过
          </Button>
          <Button
            type="link"
            size="small"
            danger
            icon={<CloseCircleOutlined />}
            onClick={() => handleRejectClick(record.id)}
          >
            驳回
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <>
      <ProTable<API.PendingAuditItem, API.PageParams>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={async (params) => {
          const { current, pageSize } = params;
          try {
            const res = await getPendingAudits({ page: current, size: pageSize });
            return { data: res.list, total: res.total, success: true };
          } catch (err: any) {
            message.error(err?.message || '查询失败');
            return { data: [], total: 0, success: true };
          }
        }}
        search={false}
        pagination={{ pageSize: 10, showSizeChanger: true }}
        toolBarRender={() => [
          <Button key="refresh" onClick={() => actionRef.current?.reload()}>
            刷新
          </Button>,
        ]}
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
                <Tag color="orange">待审核</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="提交时间" span={2}>
                {dayjs(detailData.createdAt).format('YYYY-MM-DD HH:mm')}
              </Descriptions.Item>
              {detailData.rejectReason && (
                <Descriptions.Item label="驳回原因" span={2}>
                  <Text type="danger">{detailData.rejectReason}</Text>
                </Descriptions.Item>
              )}
            </Descriptions>

            {detailData.riskWarnings && detailData.riskWarnings.length > 0 && (
              <div style={{ marginBottom: 16 }}>
                <Text strong style={{ display: 'block', marginBottom: 8 }}>
                  风险提示
                </Text>
                <Space direction="vertical" size={8} style={{ width: '100%' }}>
                  {detailData.riskWarnings.map((w, i) => (
                    <Alert
                      key={i}
                      type={w.level === 'AUDIT' ? 'warning' : 'info'}
                      showIcon
                      message={w.rule}
                      description={w.message}
                    />
                  ))}
                </Space>
              </div>
            )}

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

            <Space style={{ marginTop: 16, justifyContent: 'flex-end', width: '100%' }}>
              <Button
                type="primary"
                icon={<CheckCircleOutlined />}
                style={{ background: '#52c41a', borderColor: '#52c41a' }}
                onClick={() => {
                  setDetailOpen(false);
                  handleApprove(detailData.id);
                }}
              >
                通过
              </Button>
              <Button
                danger
                icon={<CloseCircleOutlined />}
                onClick={() => {
                  setDetailOpen(false);
                  handleRejectClick(detailData.id);
                }}
              >
                驳回
              </Button>
            </Space>
          </>
        ) : (
          <div style={{ textAlign: 'center', padding: 40, color: '#999' }}>加载失败</div>
        )}
      </Modal>

      {/* 驳回弹窗 */}
      <Modal
        title="驳回处方"
        open={rejectOpen}
        onOk={handleRejectSubmit}
        onCancel={() => setRejectOpen(false)}
        okText="确认驳回"
        okButtonProps={{ danger: true, loading: rejecting }}
        cancelText="取消"
        destroyOnClose
      >
        <div style={{ marginBottom: 8 }}>
          <Text>驳回原因：</Text>
          <Text type="danger" style={{ fontSize: 12 }}>（必填）</Text>
        </div>
        <TextArea
          rows={4}
          value={rejectReason}
          onChange={(e) => setRejectReason(e.target.value)}
          placeholder="请输入驳回原因，以便医生了解修改方向"
        />
      </Modal>
    </>
  );
}
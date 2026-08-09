/**
 * 处方详情弹窗（列表页查看）。
 *
 * 展示处方元信息（患者/医生/科室取自后端嵌套 doctor/patient 结构）、
 * 风险规则快照（命中重复用药/高危时 Alert 提示）、明细项表格；
 * 驳回原因存在时高亮展示。
 */
import { Alert, Modal, Descriptions, Space, Table, Tag, Typography } from 'antd';
import dayjs from 'dayjs';
import { STATUS_MAP } from '../constants';

const { Text } = Typography;

/** 风险预警稳定 key：同一规则+级别视为同一条，避免用数组下标 */
const riskKey = (w: API.RiskWarning) => `${w.level}-${w.rule}`;

interface Props {
  open: boolean;
  loading: boolean;
  data: API.PrescriptionDetail | null;
  onCancel: () => void;
}

export default function PrescriptionDetailModal({ open, loading, data, onCancel }: Props) {
  return (
    <Modal
      title={`处方详情 #${data?.id ?? ''}`}
      open={open}
      footer={null}
      onCancel={onCancel}
      width={640}
      destroyOnHidden
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: 40 }}>加载中...</div>
      ) : data ? (
        <>
          <Descriptions size="small" column={2} bordered style={{ marginBottom: 16 }}>
            <Descriptions.Item label="患者">{data.patient?.name ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="医生">{data.doctor?.name ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="科室">{data.doctor?.deptName ?? '-'}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={STATUS_MAP[data.status]?.color}>
                {STATUS_MAP[data.status]?.text ?? data.status}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="创建时间">
              {data.createdAt ? dayjs(data.createdAt).format('YYYY-MM-DD HH:mm') : '-'}
            </Descriptions.Item>
            {data.rejectReason && (
              <Descriptions.Item label="驳回原因" span={2}>
                <Text type="danger">{data.rejectReason}</Text>
              </Descriptions.Item>
            )}
          </Descriptions>

          {data.riskWarnings && data.riskWarnings.length > 0 && (
            <div style={{ marginBottom: 16 }}>
              <Text strong style={{ display: 'block', marginBottom: 8 }}>
                风险提示
              </Text>
              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                {data.riskWarnings.map((w) => (
                  <Alert
                    key={riskKey(w)}
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
            处方明细（{data.items.length} 项）
          </Text>
          <Table
            dataSource={data.items}
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
  );
}

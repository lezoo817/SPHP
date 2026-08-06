/**
 * 处方详情弹窗（列表页查看）。
 *
 * 展示处方元信息 + 明细项表格；驳回原因存在时高亮展示。
 */
import { Modal, Descriptions, Table, Tag, Typography } from 'antd';
import dayjs from 'dayjs';
import { STATUS_MAP } from '../constants';

const { Text } = Typography;

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
      destroyOnClose
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: 40 }}>加载中...</div>
      ) : data ? (
        <>
          <Descriptions size="small" column={2} bordered style={{ marginBottom: 16 }}>
            <Descriptions.Item label="患者">{data.patientName}</Descriptions.Item>
            <Descriptions.Item label="医生">{data.doctorName}</Descriptions.Item>
            <Descriptions.Item label="科室">{data.deptName}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={STATUS_MAP[data.status]?.color}>
                {STATUS_MAP[data.status]?.text ?? data.status}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="创建时间">
              {dayjs(data.createdAt).format('YYYY-MM-DD HH:mm')}
            </Descriptions.Item>
            {data.rejectReason && (
              <Descriptions.Item label="驳回原因" span={2}>
                <Text type="danger">{data.rejectReason}</Text>
              </Descriptions.Item>
            )}
          </Descriptions>

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

/**
 * 处方模板详情弹窗。
 *
 * 展示模板元信息 + 药品明细表格。
 */
import { Modal, Descriptions, Table, Typography } from 'antd';
import dayjs from 'dayjs';

const { Text } = Typography;

/** 模板明细项类型（取自全局 API 命名空间的内联定义） */
type TemplateItem = API.PrescriptionTemplate['items'][number];

interface Props {
  open: boolean;
  data: API.PrescriptionTemplate | null;
  onCancel: () => void;
}

export default function TemplateDetailModal({ open, data, onCancel }: Props) {
  return (
    <Modal
      title={`模板详情：${data?.name ?? ''}`}
      open={open}
      footer={null}
      onCancel={onCancel}
      width={640}
      destroyOnClose
    >
      {data && (
        <>
          <Descriptions size="small" column={2} bordered style={{ marginBottom: 16 }}>
            <Descriptions.Item label="模板名称">{data.name}</Descriptions.Item>
            <Descriptions.Item label="科室">{data.deptName ?? '全院通用'}</Descriptions.Item>
            <Descriptions.Item label="创建人">{data.doctorName}</Descriptions.Item>
            <Descriptions.Item label="创建时间">
              {data.createdAt ? dayjs(data.createdAt).format('YYYY-MM-DD HH:mm') : '-'}
            </Descriptions.Item>
          </Descriptions>

          <Text strong style={{ display: 'block', marginBottom: 8 }}>
            药品明细（{data.itemCount} 项）
          </Text>
          <Table<TemplateItem>
            dataSource={data.items ?? []}
            rowKey="drugId"
            size="small"
            pagination={false}
            columns={[
              { title: '药品', dataIndex: 'drugName', width: 140 },
              { title: '用量', dataIndex: 'dosage', width: 80 },
              { title: '频次', dataIndex: 'frequency', width: 100 },
              { title: '用法', dataIndex: 'usageMethod', width: 80 },
              { title: '天数', dataIndex: 'days', width: 60, align: 'right' },
              {
                title: '数量',
                dataIndex: 'quantity',
                width: 70,
                align: 'right',
                render: (_: unknown, item: TemplateItem) =>
                  item.quantityUnit ? `${item.quantity}${item.quantityUnit}` : item.quantity,
              },
            ]}
          />
        </>
      )}
    </Modal>
  );
}

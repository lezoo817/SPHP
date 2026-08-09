/**
 * 更新库存弹窗（头部展示当前库存概览，表单提交调整库存/安全库存/单价）。
 */
import { Modal, Descriptions } from 'antd';
import { ProForm, ProFormDigit } from '@ant-design/pro-components';

interface Props {
  open: boolean;
  item: API.InventoryItem | null;
  submitting: boolean;
  onCancel: () => void;
  onSubmit: (values: API.UpdateInventoryReq) => void;
}

export default function UpdateInventoryModal({
  open,
  item,
  submitting,
  onCancel,
  onSubmit,
}: Props) {
  return (
    <Modal
      title="更新库存"
      open={open}
      footer={null}
      destroyOnHidden
      onCancel={onCancel}
      width={480}
    >
      {item && (
        <Descriptions size="small" column={1} style={{ marginBottom: 16 }}>
          <Descriptions.Item label="药房">
            {item.pharmacyName ?? '汇总'}
          </Descriptions.Item>
          <Descriptions.Item label="药品">{item.drugName}</Descriptions.Item>
          <Descriptions.Item label="规格">{item.specification}</Descriptions.Item>
          <Descriptions.Item label="当前库存">{item.availableCount}</Descriptions.Item>
          <Descriptions.Item label="锁定数量">{item.lockedCount}</Descriptions.Item>
        </Descriptions>
      )}
      <ProForm<API.UpdateInventoryReq>
        initialValues={
          item
            ? {
                availableCount: item.availableCount,
                safetyStock: item.safetyStock,
                unitPriceCent: item.unitPriceCent,
              }
            : undefined
        }
        onFinish={onSubmit}
        submitter={{
          submitButtonProps: { loading: submitting },
        }}
      >
        <ProFormDigit
          name="availableCount"
          label="库存数量"
          rules={[{ required: true, message: '请输入库存数量' }]}
          min={0}
          max={9999999}
        />
        <ProFormDigit
          name="safetyStock"
          label="安全库存"
          rules={[{ required: true, message: '请输入安全库存' }]}
          min={0}
          max={9999999}
        />
        <ProFormDigit
          name="unitPriceCent"
          label="单价（分）"
          rules={[{ required: true, message: '请输入单价' }]}
          min={0}
          max={999999999}
          fieldProps={{
            addonAfter: '分（如 1500 分 = 15 元）',
          }}
        />
      </ProForm>
    </Modal>
  );
}

/**
 * 释放锁定库存弹窗（需填写关联药品订单 ID 与释放原因）。
 */
import { Modal, Descriptions } from 'antd';
import { ProForm, ProFormDigit, ProFormText } from '@ant-design/pro-components';

interface Props {
  open: boolean;
  item: API.InventoryItem | null;
  submitting: boolean;
  onCancel: () => void;
  onSubmit: (values: API.UnlockInventoryReq) => void;
}

export default function UnlockInventoryModal({
  open,
  item,
  submitting,
  onCancel,
  onSubmit,
}: Props) {
  return (
    <Modal
      title="释放锁定库存"
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
          <Descriptions.Item label="当前锁定数量">
            {item.lockedCount}
          </Descriptions.Item>
        </Descriptions>
      )}
      <ProForm<API.UnlockInventoryReq>
        onFinish={onSubmit}
        submitter={{
          submitButtonProps: { loading: submitting },
        }}
      >
        <ProFormDigit
          name="drugOrderId"
          label="药品订单ID"
          rules={[{ required: true, message: '请输入药品订单ID' }]}
          min={1}
          placeholder="请输入关联的药品订单ID"
        />
        <ProFormText
          name="reason"
          label="释放原因"
          rules={[
            { required: true, message: '请输入释放原因' },
            { max: 200, message: '最多 200 个字符' },
          ]}
          placeholder="如：支付超时人工补偿"
        />
      </ProForm>
    </Modal>
  );
}

/**
 * 驳回处方弹窗。
 *
 * 原因必填校验：为空时提示不提交；提交前 trim 后交由父组件执行审核接口。
 */
import { useState } from 'react';
import { Modal, Input, Typography, message } from 'antd';

const { Text } = Typography;
const { TextArea } = Input;

interface Props {
  open: boolean;
  submitting: boolean;
  onCancel: () => void;
  onSubmit: (reason: string) => void;
}

export default function RejectModal({ open, submitting, onCancel, onSubmit }: Props) {
  const [reason, setReason] = useState('');

  /** 确认驳回：原因非空才提交 */
  const handleOk = () => {
    const trimmed = reason.trim();
    if (!trimmed) {
      message.warning('请填写驳回原因');
      return;
    }
    onSubmit(trimmed);
  };

  return (
    <Modal
      title="驳回处方"
      open={open}
      onOk={handleOk}
      onCancel={onCancel}
      okText="确认驳回"
      okButtonProps={{ danger: true, loading: submitting }}
      cancelText="取消"
      destroyOnClose
    >
      <div style={{ marginBottom: 8 }}>
        <Text>驳回原因：</Text>
        <Text type="danger" style={{ fontSize: 12 }}>（必填）</Text>
      </div>
      <TextArea
        rows={4}
        value={reason}
        onChange={(e) => setReason(e.target.value)}
        placeholder="请输入驳回原因，以便医生了解修改方向"
      />
    </Modal>
  );
}

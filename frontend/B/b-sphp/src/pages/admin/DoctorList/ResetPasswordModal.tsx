/**
 * 重置医生密码弹窗（提交时二次确认由父组件 Modal.confirm 处理）。
 */
import { Modal, Descriptions } from 'antd';
import { ProForm, ProFormText } from '@ant-design/pro-components';

interface Props {
  open: boolean;
  doctor: API.Doctor;
  submitting: boolean;
  onCancel: () => void;
  onSubmit: (values: API.ResetDoctorPasswordReq) => void;
}

export default function ResetPasswordModal({
  open,
  doctor,
  submitting,
  onCancel,
  onSubmit,
}: Props) {
  return (
    <Modal
      title="重置密码"
      open={open}
      footer={null}
      destroyOnClose
      onCancel={onCancel}
      width={480}
    >
      <Descriptions size="small" column={1} style={{ marginBottom: 16 }}>
        <Descriptions.Item label="医生">{doctor.name}</Descriptions.Item>
      </Descriptions>
      <ProForm<API.ResetDoctorPasswordReq>
        onFinish={onSubmit}
        submitter={{
          submitButtonProps: { loading: submitting },
        }}
      >
        <ProFormText.Password
          name="password"
          label="新密码"
          rules={[
            { required: true, message: '请输入新密码' },
            { min: 6, max: 64, message: '密码长度为 6-64 位' },
          ]}
          placeholder="6-64位"
        />
      </ProForm>
    </Modal>
  );
}

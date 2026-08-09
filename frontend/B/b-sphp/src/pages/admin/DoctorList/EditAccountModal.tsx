/**
 * 修改医生登录账号弹窗。
 */
import { Modal, Descriptions } from 'antd';
import { ProForm, ProFormText } from '@ant-design/pro-components';

interface Props {
  open: boolean;
  doctor: API.Doctor;
  submitting: boolean;
  onCancel: () => void;
  onSubmit: (values: API.UpdateDoctorAccountReq) => void;
}

export default function EditAccountModal({
  open,
  doctor,
  submitting,
  onCancel,
  onSubmit,
}: Props) {
  return (
    <Modal
      title="修改登录账号"
      open={open}
      footer={null}
      destroyOnHidden
      onCancel={onCancel}
      width={480}
    >
      <Descriptions size="small" column={1} style={{ marginBottom: 16 }}>
        <Descriptions.Item label="医生">{doctor.name}</Descriptions.Item>
        <Descriptions.Item label="当前账号">
          {doctor.licenseNo || '-'}
        </Descriptions.Item>
      </Descriptions>
      <ProForm<API.UpdateDoctorAccountReq>
        onFinish={onSubmit}
        submitter={{
          submitButtonProps: { loading: submitting },
        }}
      >
        <ProFormText
          name="account"
          label="新登录账号"
          rules={[
            { required: true, message: '请输入新登录账号' },
            { min: 4, max: 32, message: '账号长度为 4-32 位' },
            {
              pattern: /^[a-zA-Z0-9_]+$/,
              message: '账号只能包含字母、数字和下划线',
            },
          ]}
          placeholder="4-32位，字母、数字或下划线"
        />
      </ProForm>
    </Modal>
  );
}

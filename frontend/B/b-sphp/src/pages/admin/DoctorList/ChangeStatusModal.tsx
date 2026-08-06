/**
 * 切换医生状态弹窗。
 */
import { Modal, Descriptions, Tag } from 'antd';
import { ProForm, ProFormSelect } from '@ant-design/pro-components';
import { STATUS_MAP, STATUS_OPTIONS, type DoctorStatus } from './constants';

interface Props {
  open: boolean;
  doctor: API.Doctor;
  submitting: boolean;
  onCancel: () => void;
  onSubmit: (values: { status: DoctorStatus }) => void;
}

export default function ChangeStatusModal({
  open,
  doctor,
  submitting,
  onCancel,
  onSubmit,
}: Props) {
  return (
    <Modal
      title="切换医生状态"
      open={open}
      footer={null}
      destroyOnClose
      onCancel={onCancel}
      width={480}
    >
      <Descriptions size="small" column={1} style={{ marginBottom: 16 }}>
        <Descriptions.Item label="医生">{doctor.name}</Descriptions.Item>
        <Descriptions.Item label="当前状态">
          <Tag color={STATUS_MAP[doctor.status]?.color}>
            {STATUS_MAP[doctor.status]?.text}
          </Tag>
        </Descriptions.Item>
      </Descriptions>
      <ProForm<{ status: DoctorStatus }>
        initialValues={{ status: doctor.status }}
        onFinish={onSubmit}
        submitter={{
          submitButtonProps: { loading: submitting },
        }}
      >
        <ProFormSelect
          name="status"
          label="新状态"
          rules={[{ required: true, message: '请选择新状态' }]}
          options={STATUS_OPTIONS}
          placeholder="请选择新状态"
        />
      </ProForm>
    </Modal>
  );
}

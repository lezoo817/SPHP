/**
 * 编辑医生资料弹窗。
 */
import { Modal } from 'antd';
import { ProForm, ProFormText, ProFormSelect } from '@ant-design/pro-components';
import { TITLE_OPTIONS } from './constants';

interface Props {
  open: boolean;
  doctor: API.Doctor;
  submitting: boolean;
  onCancel: () => void;
  onSubmit: (values: API.UpdateDoctorProfileReq) => void;
}

export default function EditProfileModal({
  open,
  doctor,
  submitting,
  onCancel,
  onSubmit,
}: Props) {
  return (
    <Modal
      title="编辑医生资料"
      open={open}
      footer={null}
      destroyOnClose
      onCancel={onCancel}
      width={520}
    >
      <ProForm<API.UpdateDoctorProfileReq>
        initialValues={{
          name: doctor.name,
          title: doctor.title,
          specialty: doctor.specialty,
        }}
        onFinish={onSubmit}
        submitter={{
          submitButtonProps: { loading: submitting },
        }}
      >
        <ProFormText
          name="name"
          label="姓名"
          rules={[
            { required: true, message: '请输入医生姓名' },
            { max: 50, message: '最多 50 个字符' },
          ]}
        />
        <ProFormSelect
          name="title"
          label="职称"
          rules={[{ required: true, message: '请选择职称' }]}
          options={TITLE_OPTIONS}
        />
        <ProFormText
          name="specialty"
          label="专长"
          rules={[{ max: 200, message: '最多 200 个字符' }]}
        />
        <ProFormText
          name="introduction"
          label="简介"
          rules={[{ max: 500, message: '最多 500 个字符' }]}
        />
      </ProForm>
    </Modal>
  );
}

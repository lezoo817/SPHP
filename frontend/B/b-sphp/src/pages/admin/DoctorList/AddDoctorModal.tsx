/**
 * 新增医生弹窗（自动开通登录账号）。
 */
import { Modal } from 'antd';
import { ProForm, ProFormText, ProFormSelect, ProFormDigit } from '@ant-design/pro-components';
import { TITLE_OPTIONS, fetchDepartmentOptions } from './constants';

interface Props {
  open: boolean;
  submitting: boolean;
  onCancel: () => void;
  onSubmit: (values: API.CreateDoctorReq) => void;
}

export default function AddDoctorModal({
  open,
  submitting,
  onCancel,
  onSubmit,
}: Props) {
  return (
    <Modal
      title="新增医生"
      open={open}
      footer={null}
      destroyOnClose
      onCancel={onCancel}
      width={600}
    >
      <ProForm<API.CreateDoctorReq>
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
          name="deptId"
          label="所属科室"
          rules={[{ required: true, message: '请选择科室' }]}
          request={fetchDepartmentOptions}
          placeholder="请选择科室"
        />
        <ProFormSelect
          name="title"
          label="职称"
          rules={[{ required: true, message: '请选择职称' }]}
          options={TITLE_OPTIONS}
          placeholder="请选择职称"
        />
        <ProFormText
          name="specialty"
          label="专长"
          rules={[{ max: 200, message: '最多 200 个字符' }]}
          placeholder="如：哮喘、慢阻肺"
        />
        <ProFormText
          name="licenseNo"
          label="执业证号"
          rules={[
            { required: true, message: '请输入执业证号' },
            { max: 50, message: '最多 50 个字符' },
          ]}
        />
        <ProFormText
          name="phone"
          label="手机号"
          rules={[
            {
              pattern: /^1\d{10}$/,
              message: '请输入正确的11位手机号',
            },
          ]}
          placeholder="请输入11位手机号"
        />
        <ProFormDigit
          name="registrationFeeCent"
          label="挂号费（分）"
          rules={[{ required: true, message: '请输入挂号费' }]}
          min={0}
          max={9999999}
          fieldProps={{
            addonAfter: '分（如 5000 分 = 50 元）',
          }}
        />
        <ProFormText
          name="account"
          label="登录账号"
          rules={[
            { required: true, message: '请输入登录账号' },
            { min: 4, max: 32, message: '账号长度为 4-32 位' },
            {
              pattern: /^[a-zA-Z0-9_]+$/,
              message: '账号只能包含字母、数字和下划线',
            },
          ]}
          placeholder="4-32位，字母、数字或下划线"
        />
        <ProFormText.Password
          name="password"
          label="登录密码"
          rules={[
            { required: true, message: '请输入密码' },
            { min: 6, max: 64, message: '密码长度为 6-64 位' },
          ]}
          placeholder="6-64位"
        />
        <ProFormSelect
          name="status"
          label="状态"
          rules={[{ required: true, message: '请选择状态' }]}
          options={[
            { label: '启用', value: 'ENABLED' },
            { label: '停用', value: 'DISABLED' },
          ]}
          initialValue="ENABLED"
        />
      </ProForm>
    </Modal>
  );
}

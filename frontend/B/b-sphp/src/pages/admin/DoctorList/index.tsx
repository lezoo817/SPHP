/**
 * 医生管理页
 * - ProTable 列表，支持科室过滤、名称/状态搜索
 * - ADMIN 角色可新增/编辑/修改账号/重置密码/切换状态
 * - DEPT_HEAD 角色仅可编辑资料
 */
import {
  Tag,
  Button,
  Modal,
  message,
  Dropdown,
  Space,
  Descriptions,
} from 'antd';
import {
  PlusOutlined,
  MoreOutlined,
  EditOutlined,
  KeyOutlined,
  UserOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import type { MenuProps } from 'antd';
import { ProTable, ProForm, ProFormText, ProFormSelect, ProFormDigit } from '@ant-design/pro-components';
import { useModel } from '@umijs/max';
import { useRef, useState } from 'react';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  getDoctors,
  createDoctor,
  updateDoctorProfile,
  updateDoctorAccount,
  resetDoctorPassword,
  updateDoctorStatus,
  getDepartments,
} from '@/services/admin';

/** 医生状态标签映射 */
const STATUS_MAP: Record<string, { text: string; color: string }> = {
  ENABLED: { text: '启用', color: 'green' },
  DISABLED: { text: '停用', color: 'red' },
  SUSPENDED: { text: '暂停', color: 'orange' },
};

/** 医生职称选项 */
const TITLE_OPTIONS = [
  { label: '主任医师', value: '主任医师' },
  { label: '副主任医师', value: '副主任医师' },
  { label: '主治医师', value: '主治医师' },
  { label: '住院医师', value: '住院医师' },
  { label: '医士', value: '医士' },
];

export default function DoctorList() {
  const { initialState } = useModel('@@initialState');
  const currentUser = initialState?.currentUser;
  const roles = currentUser?.roles ?? [];
  const isAdmin = roles.includes('ADMIN');
  const isDeptHead = roles.includes('DEPT_HEAD');
  const actionRef = useRef<ActionType>();

  // 弹窗状态
  const [addModalOpen, setAddModalOpen] = useState(false);
  const [editProfileOpen, setEditProfileOpen] = useState(false);
  const [editAccountOpen, setEditAccountOpen] = useState(false);
  const [resetPwdOpen, setResetPwdOpen] = useState(false);
  const [changeStatusOpen, setChangeStatusOpen] = useState(false);
  const [selectedDoctor, setSelectedDoctor] = useState<API.Doctor | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // ========== 操作按钮 ==========

  /** 打开新增弹窗 */
  const handleAdd = () => {
    setSelectedDoctor(null);
    setAddModalOpen(true);
  };

  /** 打开编辑资料弹窗 */
  const handleEditProfile = (record: API.Doctor) => {
    setSelectedDoctor(record);
    setEditProfileOpen(true);
  };

  /** 打开修改账号弹窗 */
  const handleEditAccount = (record: API.Doctor) => {
    setSelectedDoctor(record);
    setEditAccountOpen(true);
  };

  /** 打开重置密码弹窗 */
  const handleResetPwd = (record: API.Doctor) => {
    setSelectedDoctor(record);
    setResetPwdOpen(true);
  };

  /** 打开切换状态弹窗 */
  const handleChangeStatus = (record: API.Doctor) => {
    setSelectedDoctor(record);
    setChangeStatusOpen(true);
  };

  // ========== 提交逻辑 ==========

  /** 新增医生 */
  const handleAddSubmit = async (values: API.CreateDoctorReq) => {
    setSubmitting(true);
    try {
      await createDoctor(values);
      message.success('医生新增成功');
      setAddModalOpen(false);
      actionRef.current?.reload();
    } catch (err: any) {
      message.error(err?.message || '新增失败，请重试');
    } finally {
      setSubmitting(false);
    }
  };

  /** 编辑资料 */
  const handleEditProfileSubmit = async (values: API.UpdateDoctorProfileReq) => {
    if (!selectedDoctor) return;
    setSubmitting(true);
    try {
      await updateDoctorProfile(selectedDoctor.id, values);
      message.success('资料更新成功');
      setEditProfileOpen(false);
      actionRef.current?.reload();
    } catch (err: any) {
      message.error(err?.message || '更新失败，请重试');
    } finally {
      setSubmitting(false);
    }
  };

  /** 修改账号 */
  const handleEditAccountSubmit = async (values: API.UpdateDoctorAccountReq) => {
    if (!selectedDoctor) return;
    setSubmitting(true);
    try {
      await updateDoctorAccount(selectedDoctor.id, values.account);
      message.success('账号修改成功');
      setEditAccountOpen(false);
      actionRef.current?.reload();
    } catch (err: any) {
      message.error(err?.message || '修改失败，请重试');
    } finally {
      setSubmitting(false);
    }
  };

  /** 重置密码（二次确认在弹窗中通过 Modal.confirm 处理） */
  const handleResetPwdSubmit = async (values: API.ResetDoctorPasswordReq) => {
    if (!selectedDoctor) return;

    // 二次确认
    Modal.confirm({
      title: '确认重置密码',
      content: `确定要将医生「${selectedDoctor.name}」的密码重置为「${values.password}」吗？重置后用户将无法使用旧密码登录。`,
      okText: '确认重置',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        setSubmitting(true);
        try {
          await resetDoctorPassword(selectedDoctor.id, values.password);
          message.success('密码重置成功');
          setResetPwdOpen(false);
          actionRef.current?.reload();
        } catch (err: any) {
          message.error(err?.message || '重置失败，请重试');
        } finally {
          setSubmitting(false);
        }
      },
    });
  };

  /** 切换状态 */
  const handleChangeStatusSubmit = async (values: { status: string }) => {
    if (!selectedDoctor) return;
    setSubmitting(true);
    try {
      await updateDoctorStatus(
        selectedDoctor.id,
        values.status as 'ENABLED' | 'DISABLED' | 'SUSPENDED',
      );
      const label = STATUS_MAP[values.status]?.text || values.status;
      message.success(`状态已切换为「${label}」`);
      setChangeStatusOpen(false);
      actionRef.current?.reload();
    } catch (err: any) {
      message.error(err?.message || '状态切换失败');
    } finally {
      setSubmitting(false);
    }
  };

  // ========== 操作列菜单 ==========

  /** 生成操作列 Dropdown 菜单 */
  const getActionMenu = (record: API.Doctor): MenuProps['items'] => {
    const items: MenuProps['items'] = [];

    // ADMIN: 编辑资料 / 修改账号 / 重置密码 / 切换状态
    if (isAdmin) {
      items.push(
        {
          key: 'editProfile',
          icon: <EditOutlined />,
          label: '编辑资料',
          onClick: () => handleEditProfile(record),
        },
        {
          key: 'editAccount',
          icon: <UserOutlined />,
          label: '修改账号',
          onClick: () => handleEditAccount(record),
        },
        {
          key: 'resetPwd',
          icon: <KeyOutlined />,
          label: '重置密码',
          onClick: () => handleResetPwd(record),
        },
        { type: 'divider' },
        {
          key: 'changeStatus',
          icon: <SwapOutlined />,
          label: '切换状态',
          onClick: () => handleChangeStatus(record),
        },
      );
    } else if (isDeptHead) {
      // DEPT_HEAD: 仅编辑资料
      items.push({
        key: 'editProfile',
        icon: <EditOutlined />,
        label: '编辑资料',
        onClick: () => handleEditProfile(record),
      });
    }

    return items.length > 0 ? items : undefined;
  };

  // ========== 获取科室列表（供搜索筛选） ==========

  const fetchDepartments = async () => {
    try {
      const res = await getDepartments({ page: 1, size: 200 });
      return (res.list ?? []).map((dept) => ({
        label: dept.name,
        value: dept.id,
      }));
    } catch {
      return [];
    }
  };

  // ========== 列定义 ==========

  const columns: ProColumns<API.Doctor>[] = [
    {
      title: '姓名',
      dataIndex: 'name',
      width: 100,
      ellipsis: true,
    },
    {
      title: '科室',
      dataIndex: 'deptName',
      width: 120,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '所属科室',
      dataIndex: 'deptId',
      hideInTable: true,
      hideInSearch: false,
      renderFormItem: () => (
        <ProFormSelect
          name="deptId"
          noStyle
          request={fetchDepartments}
          placeholder="请选择科室"
          allowClear
        />
      ),
    },
    {
      title: '职称',
      dataIndex: 'title',
      width: 100,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '专长',
      dataIndex: 'specialty',
      width: 160,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '电话',
      dataIndex: 'phone',
      width: 130,
      copyable: true,
      hideInSearch: true,
    },
    {
      title: '挂号费',
      dataIndex: 'registrationFeeCent',
      width: 100,
      hideInSearch: true,
      render: (_, record) => {
        const yuan = (record.registrationFeeCent / 100).toFixed(2);
        return `${yuan} 元`;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      valueEnum: {
        ENABLED: { text: '启用', status: 'Success' },
        DISABLED: { text: '停用', status: 'Error' },
        SUSPENDED: { text: '暂停', status: 'Warning' },
      },
      render: (_, record) => {
        const s = STATUS_MAP[record.status];
        return <Tag color={s?.color}>{s?.text || record.status}</Tag>;
      },
    },
    {
      title: '操作',
      width: 100,
      hideInSearch: true,
      render: (_, record) => {
        const menu = getActionMenu(record);
        if (!menu) return <span style={{ color: '#999' }}>-</span>;
        return (
          <Dropdown menu={{ items: menu }} trigger={['click']}>
            <Button type="link" size="small">
              <Space>
                操作
                <MoreOutlined />
              </Space>
            </Button>
          </Dropdown>
        );
      },
    },
  ];

  return (
    <>
      <ProTable<API.Doctor, API.DoctorListParams>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={async (params) => {
          const { current, pageSize, ...rest } = params;
          const res = await getDoctors({
            page: current,
            size: pageSize,
            deptId: rest.deptId,
            name: rest.name,
            status: rest.status,
          });
          return {
            data: res.list,
            total: res.total,
            success: true,
          };
        }}
        search={{
          labelWidth: 'auto',
          defaultCollapsed: true,
          optionRender: (searchConfig, formProps, dom) => [...dom.reverse()],
        }}
        toolBarRender={() =>
          isAdmin
            ? [
                <Button
                  key="add"
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={handleAdd}
                >
                  新增医生
                </Button>,
              ]
            : []
        }
        pagination={{ pageSize: 10 }}
      />

      {/* ====== 新增医生弹窗 ====== */}
      <Modal
        title="新增医生"
        open={addModalOpen}
        footer={null}
        destroyOnClose
        onCancel={() => setAddModalOpen(false)}
        width={600}
      >
        <ProForm<API.CreateDoctorReq>
          onFinish={handleAddSubmit}
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
            request={fetchDepartments}
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
          <ProFormText
            name="password"
            label="登录密码"
            rules={[
              { required: true, message: '请输入密码' },
              { min: 6, max: 64, message: '密码长度为 6-64 位' },
            ]}
            fieldProps={{ type: 'password' }}
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

      {/* ====== 编辑资料弹窗 ====== */}
      <Modal
        title="编辑医生资料"
        open={editProfileOpen}
        footer={null}
        destroyOnClose
        onCancel={() => setEditProfileOpen(false)}
        width={520}
      >
        <ProForm<API.UpdateDoctorProfileReq>
          initialValues={
            selectedDoctor
              ? {
                  name: selectedDoctor.name,
                  title: selectedDoctor.title,
                  specialty: selectedDoctor.specialty,
                }
              : undefined
          }
          onFinish={handleEditProfileSubmit}
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

      {/* ====== 修改账号弹窗 ====== */}
      <Modal
        title="修改登录账号"
        open={editAccountOpen}
        footer={null}
        destroyOnClose
        onCancel={() => setEditAccountOpen(false)}
        width={480}
      >
        {selectedDoctor && (
          <Descriptions size="small" column={1} style={{ marginBottom: 16 }}>
            <Descriptions.Item label="医生">{selectedDoctor.name}</Descriptions.Item>
            <Descriptions.Item label="当前账号">
              {selectedDoctor.licenseNo || '-'}
            </Descriptions.Item>
          </Descriptions>
        )}
        <ProForm<API.UpdateDoctorAccountReq>
          onFinish={handleEditAccountSubmit}
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

      {/* ====== 重置密码弹窗 ====== */}
      <Modal
        title="重置密码"
        open={resetPwdOpen}
        footer={null}
        destroyOnClose
        onCancel={() => setResetPwdOpen(false)}
        width={480}
      >
        {selectedDoctor && (
          <Descriptions size="small" column={1} style={{ marginBottom: 16 }}>
            <Descriptions.Item label="医生">{selectedDoctor.name}</Descriptions.Item>
          </Descriptions>
        )}
        <ProForm<API.ResetDoctorPasswordReq>
          onFinish={handleResetPwdSubmit}
          submitter={{
            submitButtonProps: { loading: submitting },
          }}
        >
          <ProFormText
            name="password"
            label="新密码"
            rules={[
              { required: true, message: '请输入新密码' },
              { min: 6, max: 64, message: '密码长度为 6-64 位' },
            ]}
            fieldProps={{ type: 'password' }}
            placeholder="6-64位"
          />
        </ProForm>
      </Modal>

      {/* ====== 切换状态弹窗 ====== */}
      <Modal
        title="切换医生状态"
        open={changeStatusOpen}
        footer={null}
        destroyOnClose
        onCancel={() => setChangeStatusOpen(false)}
        width={480}
      >
        {selectedDoctor && (
          <Descriptions size="small" column={1} style={{ marginBottom: 16 }}>
            <Descriptions.Item label="医生">{selectedDoctor.name}</Descriptions.Item>
            <Descriptions.Item label="当前状态">
              <Tag color={STATUS_MAP[selectedDoctor.status]?.color}>
                {STATUS_MAP[selectedDoctor.status]?.text}
              </Tag>
            </Descriptions.Item>
          </Descriptions>
        )}
        <ProForm<{ status: string }>
          initialValues={{ status: selectedDoctor?.status }}
          onFinish={handleChangeStatusSubmit}
          submitter={{
            submitButtonProps: { loading: submitting },
          }}
        >
          <ProFormSelect
            name="status"
            label="新状态"
            rules={[{ required: true, message: '请选择新状态' }]}
            options={[
              { label: '启用', value: 'ENABLED' },
              { label: '停用', value: 'DISABLED' },
              { label: '暂停', value: 'SUSPENDED' },
            ]}
            placeholder="请选择新状态"
          />
        </ProForm>
      </Modal>
    </>
  );
}
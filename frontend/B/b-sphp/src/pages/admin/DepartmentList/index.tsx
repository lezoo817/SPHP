/**
 * 科室管理页
 * - ProTable 列表，支持名称模糊搜索、状态筛选
 * - ADMIN 角色可新增/编辑/启用停用
 */
import { Tag, Button, Modal, message, Switch } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { ProTable, ProForm, ProFormText, ProFormSelect } from '@ant-design/pro-components';
import { useModel } from '@umijs/max';
import { useRef, useState } from 'react';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  getDepartments,
  createDepartment,
  updateDepartment,
  updateDepartmentStatus,
  getDoctors,
} from '@/services/admin';

export default function DepartmentList() {
  const { initialState } = useModel('@@initialState');
  const isAdmin = initialState?.currentUser?.roles?.includes('ADMIN') ?? false;
  const actionRef = useRef<ActionType>();

  const [modalOpen, setModalOpen] = useState(false);
  const [editingDept, setEditingDept] = useState<API.Department | null>(null);
  const [submitting, setSubmitting] = useState(false);

  /** 打开新增弹窗 */
  const handleAdd = () => {
    setEditingDept(null);
    setModalOpen(true);
  };

  /** 打开编辑弹窗 */
  const handleEdit = (record: API.Department) => {
    setEditingDept(record);
    setModalOpen(true);
  };

  /** 提交新增/编辑 */
  const handleSubmit = async (values: API.UpsertDepartmentReq) => {
    setSubmitting(true);
    try {
      if (editingDept) {
        await updateDepartment(editingDept.id, values);
        message.success('科室更新成功');
      } else {
        await createDepartment(values);
        message.success('科室新增成功');
      }
      setModalOpen(false);
      actionRef.current?.reload();
    } catch (err: any) {
      message.error(err?.message || '操作失败，请重试');
    } finally {
      setSubmitting(false);
    }
  };

  /** 切换科室状态 */
  const handleToggleStatus = async (record: API.Department) => {
    const newStatus = record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    const actionLabel = newStatus === 'ENABLED' ? '启用' : '停用';

    Modal.confirm({
      title: `${actionLabel}科室`,
      content: `确定要${actionLabel}科室「${record.name}」吗？${
        newStatus === 'DISABLED'
          ? '停用后，该科室下的医生将无法接诊，已发布的排班将受影响。'
          : ''
      }`,
      onOk: async () => {
        try {
          await updateDepartmentStatus(record.id, newStatus);
          message.success(`${actionLabel}成功`);
          actionRef.current?.reload();
        } catch (err: any) {
          message.error(err?.message || `${actionLabel}失败`);
        }
      },
    });
  };

  /** 获取医生列表（用于科室主任选择） */
  const fetchDoctors = async (name?: string) => {
    try {
      const res = await getDoctors({ name, page: 1, size: 100 });
      return (res.list ?? []).map((doc) => ({
        label: `${doc.name}（${doc.title}）`,
        value: doc.id,
      }));
    } catch {
      return [];
    }
  };

  const columns: ProColumns<API.Department>[] = [
    {
      title: '科室名称',
      dataIndex: 'name',
      ellipsis: true,
      width: 200,
    },
    {
      title: '科室主任',
      dataIndex: 'headDoctorName',
      width: 150,
      render: (text) => text || '-',
    },
    {
      title: '描述',
      dataIndex: 'description',
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      valueEnum: {
        ENABLED: { text: '启用', status: 'Success' },
        DISABLED: { text: '停用', status: 'Error' },
      },
      render: (_, record) => (
        <Tag color={record.status === 'ENABLED' ? 'green' : 'red'}>
          {record.status === 'ENABLED' ? '启用' : '停用'}
        </Tag>
      ),
    },
    {
      title: '操作',
      width: 180,
      hideInSearch: true,
      render: (_, record) => (
        <>
          {isAdmin && (
            <Button
              type="link"
              size="small"
              onClick={() => handleEdit(record)}
            >
              编辑
            </Button>
          )}
          {isAdmin && (
            <Switch
              checked={record.status === 'ENABLED'}
              checkedChildren="启用"
              unCheckedChildren="停用"
              size="small"
              style={{ marginLeft: 8 }}
              onChange={() => handleToggleStatus(record)}
            />
          )}
        </>
      ),
    },
  ];

  return (
    <>
      <ProTable<API.Department, API.DepartmentListParams>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={async (params) => {
          const { current, pageSize, ...rest } = params;
          const res = await getDepartments({
            page: current,
            size: pageSize,
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
                  新增科室
                </Button>,
              ]
            : []
        }
        pagination={{ pageSize: 5 }}
      />

      <Modal
        title={editingDept ? '编辑科室' : '新增科室'}
        open={modalOpen}
        footer={null}
        destroyOnClose
        onCancel={() => setModalOpen(false)}
        width={520}
      >
        <ProForm<API.UpsertDepartmentReq>
          initialValues={
            editingDept
              ? {
                  name: editingDept.name,
                  headDoctorId: editingDept.headDoctorId,
                  description: editingDept.description,
                }
              : undefined
          }
          onFinish={handleSubmit}
          submitter={{
            submitButtonProps: { loading: submitting },
          }}
        >
          <ProFormText
            name="name"
            label="科室名称"
            rules={[
              { required: true, message: '请输入科室名称' },
              { max: 100, message: '最多 100 个字符' },
            ]}
          />
          <ProFormSelect
            name="headDoctorId"
            label="科室主任"
            placeholder="请选择科室主任（可选）"
            showSearch
            request={(input) => fetchDoctors(input?.key ?? '')}
            debounceTime={300}
          />
          <ProFormText
            name="description"
            label="科室描述"
            rules={[{ max: 500, message: '最多 500 个字符' }]}
          />
        </ProForm>
      </Modal>
    </>
  );
}
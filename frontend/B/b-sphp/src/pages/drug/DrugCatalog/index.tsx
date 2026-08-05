/**
 * 药品目录页
 * - ProTable 列表，支持名称模糊搜索、状态筛选
 * - ADMIN 角色可新增/编辑/启用停用
 */
import { Button, Modal, message, Switch, Select, Badge } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { ProTable, ProForm, ProFormText, ProFormSelect } from '@ant-design/pro-components';
import { useModel } from '@umijs/max';
import { useRef, useState } from 'react';
import type { ActionType, ProColumns, ProFormInstance } from '@ant-design/pro-components';
import { getDrugs, createDrug, updateDrug, updateDrugStatus } from '@/services/admin';

export default function DrugCatalog() {
  const { initialState } = useModel('@@initialState');
  const isAdmin = initialState?.currentUser?.roles?.includes('ADMIN') ?? false;
  const actionRef = useRef<ActionType>();
  const formRef = useRef<ProFormInstance>();
  const searchParamsRef = useRef<Record<string, any>>({});

  const [modalOpen, setModalOpen] = useState(false);
  const [editingDrug, setEditingDrug] = useState<API.Drug | null>(null);
  const [submitting, setSubmitting] = useState(false);

  /** 打开新增弹窗 */
  const handleAdd = () => {
    setEditingDrug(null);
    setModalOpen(true);
  };

  /** 打开编辑弹窗 */
  const handleEdit = (record: API.Drug) => {
    setEditingDrug(record);
    setModalOpen(true);
  };

  /** 提交新增/编辑 */
  const handleSubmit = async (values: API.CreateDrugReq) => {
    setSubmitting(true);
    try {
      if (editingDrug) {
        await updateDrug(editingDrug.id, values);
        message.success('药品更新成功');
      } else {
        await createDrug(values);
        message.success('药品新增成功');
      }
      setModalOpen(false);
      actionRef.current?.reload();
    } catch (err: any) {
      message.error(err?.message || '操作失败，请重试');
    } finally {
      setSubmitting(false);
    }
  };

  /** 切换药品状态 */
  const handleToggleStatus = (record: API.Drug) => {
    const newStatus = record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    const actionLabel = newStatus === 'ENABLED' ? '启用' : '停用';

    Modal.confirm({
      title: `${actionLabel}药品`,
      content: `确定要${actionLabel}药品「${record.name}」吗？`,
      onOk: async () => {
        try {
          await updateDrugStatus(record.id, newStatus);
          message.success(`${actionLabel}成功`);
          actionRef.current?.reload();
        } catch (err: any) {
          message.error(err?.message || `${actionLabel}失败`);
        }
      },
    });
  };

  const columns: ProColumns<API.Drug>[] = [
    {
      title: '药品名称',
      dataIndex: 'name',
      ellipsis: true,
      width: 200,
    },
    {
      title: '规格',
      dataIndex: 'specification',
      width: 140,
      hideInSearch: true,
    },
    {
      title: '单位',
      dataIndex: 'unit',
      width: 80,
      hideInSearch: true,
    },
    {
      title: '生产厂家',
      dataIndex: 'manufacturer',
      width: 180,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '批准文号',
      dataIndex: 'approvalNumber',
      width: 170,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '适应症',
      dataIndex: 'indication',
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
      renderFormItem: () => (
        <Select
          allowClear
          placeholder="全部"
          options={[
            { label: '启用', value: 'ENABLED' },
            { label: '停用', value: 'DISABLED' },
          ]}
        />
      ),
      render: (_, record) => (
        <Badge
          status={record.status === 'ENABLED' ? 'success' : 'error'}
          text={record.status === 'ENABLED' ? '启用' : '停用'}
        />
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
      <ProTable<API.Drug, API.DrugListParams>
        actionRef={actionRef}
        formRef={formRef}
        rowKey="id"
        columns={columns}
        request={async (params) => {
          const { current, pageSize } = params;
          const sp = searchParamsRef.current;
          try {
            const res = await getDrugs({
              page: current,
              size: pageSize,
              name: sp.name,
              status: sp.status || undefined,
            });
            return {
              data: res.list,
              total: res.total,
              success: true,
            };
          } catch (err: any) {
            message.error(err?.message || '查询失败，请重试');
            return { data: [], total: 0, success: true };
          }
        }}
        search={{
          labelWidth: 'auto',
          defaultCollapsed: true,
        }}
        beforeSearchSubmit={(values) => {
          const used: Record<string, any> = {};
          if (values.name) used.name = values.name;
          if (values.status) used.status = values.status;
          searchParamsRef.current = used;

          const cleared: Record<string, undefined> = {};
          if (!values.name) cleared.name = undefined;
          if (!values.status) cleared.status = undefined;
          formRef.current?.setFieldsValue(cleared);
          return values;
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
                  新增药品
                </Button>,
              ]
            : []
        }
        pagination={{ pageSize: 10 }}
      />

      <Modal
        title={editingDrug ? '编辑药品' : '新增药品'}
        open={modalOpen}
        footer={null}
        destroyOnClose
        onCancel={() => setModalOpen(false)}
        width={560}
      >
        <ProForm<API.CreateDrugReq>
          initialValues={
            editingDrug
              ? {
                  name: editingDrug.name,
                  specification: editingDrug.specification,
                  unit: editingDrug.unit,
                  indication: editingDrug.indication,
                  manufacturer: editingDrug.manufacturer,
                  approvalNumber: editingDrug.approvalNumber,
                  status: editingDrug.status,
                }
              : { status: 'ENABLED' }
          }
          onFinish={handleSubmit}
          submitter={{
            submitButtonProps: { loading: submitting },
          }}
        >
          <ProFormText
            name="name"
            label="药品名称"
            rules={[
              { required: true, message: '请输入药品名称' },
              { max: 100, message: '最多 100 个字符' },
            ]}
          />
          <ProFormText
            name="specification"
            label="规格"
            rules={[
              { required: true, message: '请输入规格' },
              { max: 50, message: '最多 50 个字符' },
            ]}
            placeholder="如：0.25g×24粒"
          />
          <ProFormText
            name="unit"
            label="单位"
            rules={[
              { required: true, message: '请输入单位' },
              { max: 10, message: '最多 10 个字符' },
            ]}
            placeholder="如：盒、瓶"
          />
          <ProFormText
            name="manufacturer"
            label="生产厂家"
            rules={[{ max: 200, message: '最多 200 个字符' }]}
          />
          <ProFormText
            name="approvalNumber"
            label="批准文号"
            rules={[
              { required: true, message: '请输入批准文号' },
              { max: 50, message: '最多 50 个字符' },
            ]}
            placeholder="如：国药准字H12345678"
          />
          <ProFormText
            name="indication"
            label="适应症"
            rules={[{ max: 500, message: '最多 500 个字符' }]}
          />
          <ProFormSelect
            name="status"
            label="状态"
            rules={[{ required: true, message: '请选择状态' }]}
            options={[
              { label: '启用', value: 'ENABLED' },
              { label: '停用', value: 'DISABLED' },
            ]}
          />
        </ProForm>
      </Modal>
    </>
  );
}
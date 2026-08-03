/**
 * 处方模板页
 * - ProTable 列表，支持模板名称模糊搜索、科室筛选
 * - 新建模板弹窗（填写名称、科室、药品明细）
 * - 查看详情弹窗
 * - 删除模板
 */
import {
  Button,
  Modal,
  message,
  Space,
  Typography,
  Descriptions,
  Table,
  Form,
  Select,
  Input,
  InputNumber,
  Popconfirm,
} from 'antd';
import {
  PlusOutlined,
  EyeOutlined,
  DeleteOutlined,
  MinusCircleOutlined,
} from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import { useModel } from '@umijs/max';
import { useRef, useState, useEffect } from 'react';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  getTemplates,
  saveTemplate,
  deleteTemplate,
  getDepartments,
} from '@/services/admin';
import dayjs from 'dayjs';

const { Text } = Typography;

export default function PrescriptionTemplates() {
  const { initialState } = useModel('@@initialState');
  const currentUser = initialState?.currentUser;
  const actionRef = useRef<ActionType>();

  // 详情弹窗
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailData, setDetailData] = useState<API.PrescriptionTemplate | null>(null);

  // 新建弹窗
  const [createOpen, setCreateOpen] = useState(false);
  const [createForm] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);

  // 科室选项
  const [deptOptions, setDeptOptions] = useState<{ label: string; value: number }[]>([]);

  useEffect(() => {
    getDepartments({ page: 1, size: 200 }).then((res) => {
      setDeptOptions(res.list.map((d) => ({ label: d.name, value: d.id })));
    }).catch(() => {});
  }, []);

  /** 查看详情 */
  const handleViewDetail = (record: API.PrescriptionTemplate) => {
    setDetailData(record);
    setDetailOpen(true);
  };

  /** 新建模板 */
  const handleCreate = () => {
    createForm.resetFields();
    // 默认当前用户科室
    if (currentUser?.deptId) {
      createForm.setFieldsValue({ deptId: currentUser.deptId });
    }
    setCreateOpen(true);
  };

  /** 提交新建 */
  const handleCreateSubmit = async () => {
    try {
      const values = await createForm.validateFields();
      setSubmitting(true);
      await saveTemplate({
        name: values.name,
        deptId: values.deptId || undefined,
        items: (values.items ?? []).map((item: any) => ({
          drugId: item.drugId,
          dosage: item.dosage,
          frequency: item.frequency,
          usageMethod: item.usageMethod,
          days: item.days,
          quantity: item.quantity,
        })),
      });
      message.success('模板创建成功');
      setCreateOpen(false);
      actionRef.current?.reload();
    } catch (err: any) {
      if (err?.message) message.error(err.message);
    } finally {
      setSubmitting(false);
    }
  };

  /** 删除模板 */
  const handleDelete = async (id: number) => {
    try {
      await deleteTemplate(id);
      message.success('模板已删除');
      actionRef.current?.reload();
    } catch (err: any) {
      message.error(err?.message || '删除失败');
    }
  };

  const columns: ProColumns<API.PrescriptionTemplate>[] = [
    {
      title: '模板名称',
      dataIndex: 'name',
      width: 160,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '科室',
      dataIndex: 'deptName',
      width: 120,
      ellipsis: true,
      hideInSearch: true,
      render: (_, record) => record.deptName ?? '全院通用',
    },
    {
      title: '创建人',
      dataIndex: 'doctorName',
      width: 100,
      hideInSearch: true,
    },
    {
      title: '药品数',
      dataIndex: 'itemCount',
      width: 70,
      align: 'right',
      hideInSearch: true,
      render: (_, record) => `${record.itemCount} 项`,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 160,
      hideInSearch: true,
      render: (_, record) =>
        record.createdAt ? dayjs(record.createdAt).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: '操作',
      width: 160,
      hideInSearch: true,
      render: (_, record) => (
        <Space size={0}>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleViewDetail(record)}
          >
            查看
          </Button>
          <Popconfirm
            title="确认删除"
            description="删除后不可恢复，确定删除该模板吗？"
            onConfirm={() => handleDelete(record.id)}
            okText="确认删除"
            cancelText="取消"
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // 搜索项：名称、科室
  columns.splice(0, 0, {
    title: '科室',
    dataIndex: 'deptId',
    valueType: 'select',
    hideInTable: true,
    fieldProps: { allowClear: true, placeholder: '全部科室', options: deptOptions },
  });
  columns.splice(0, 0, {
    title: '模板名称',
    dataIndex: 'name',
    valueType: 'text',
    hideInTable: true,
    fieldProps: { placeholder: '输入模板名称搜索' },
  });

  return (
    <>
      <ProTable<API.PrescriptionTemplate, API.TemplateListParams>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={async (params) => {
          const { current, pageSize, ...rest } = params;
          try {
            const res = await getTemplates({
              page: current,
              size: pageSize,
              name: rest.name,
              deptId: rest.deptId,
            });
            return { data: res.list, total: res.total, success: true };
          } catch (err: any) {
            message.error(err?.message || '查询失败');
            return { data: [], total: 0, success: true };
          }
        }}
        search={{
          labelWidth: 'auto',
          span: 8,
          defaultFormItemsNumber: 2,
        }}
        toolBarRender={() => [
          <Button key="add" type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
            新建模板
          </Button>,
        ]}
        pagination={{ pageSize: 10, showSizeChanger: true }}
      />

      {/* 详情弹窗 */}
      <Modal
        title={`模板详情：${detailData?.name ?? ''}`}
        open={detailOpen}
        footer={null}
        onCancel={() => setDetailOpen(false)}
        width={640}
        destroyOnClose
      >
        {detailData && (
          <>
            <Descriptions size="small" column={2} bordered style={{ marginBottom: 16 }}>
              <Descriptions.Item label="模板名称">{detailData.name}</Descriptions.Item>
              <Descriptions.Item label="科室">
                {detailData.deptName ?? '全院通用'}
              </Descriptions.Item>
              <Descriptions.Item label="创建人">{detailData.doctorName}</Descriptions.Item>
              <Descriptions.Item label="创建时间">
                {detailData.createdAt
                  ? dayjs(detailData.createdAt).format('YYYY-MM-DD HH:mm')
                  : '-'}
              </Descriptions.Item>
            </Descriptions>

            <Text strong style={{ display: 'block', marginBottom: 8 }}>
              药品明细（{detailData.itemCount} 项）
            </Text>
            <Table
              dataSource={detailData.items ?? []}
              rowKey="drugId"
              size="small"
              pagination={false}
              columns={[
                { title: '药品', dataIndex: 'drugName', width: 140 },
                { title: '用量', dataIndex: 'dosage', width: 80 },
                { title: '频次', dataIndex: 'frequency', width: 100 },
                { title: '用法', dataIndex: 'usageMethod', width: 80 },
                { title: '天数', dataIndex: 'days', width: 60, align: 'right' },
                { title: '数量', dataIndex: 'quantity', width: 60, align: 'right' },
              ]}
            />
          </>
        )}
      </Modal>

      {/* 新建模板弹窗 */}
      <Modal
        title="新建处方模板"
        open={createOpen}
        onOk={handleCreateSubmit}
        onCancel={() => setCreateOpen(false)}
        okText="保存"
        okButtonProps={{ loading: submitting }}
        width={700}
        destroyOnClose
      >
        <Form
          form={createForm}
          layout="vertical"
          initialValues={{ items: [{}] }}
        >
          <Space style={{ width: '100%' }} size={16}>
            <Form.Item
              name="name"
              label="模板名称"
              rules={[{ required: true, message: '请输入模板名称' }]}
              style={{ width: 280 }}
            >
              <Input placeholder="如：高血压常规用药" />
            </Form.Item>
            <Form.Item name="deptId" label="所属科室" style={{ width: 240 }}>
              <Select
                allowClear
                placeholder="全院通用（空）"
                options={deptOptions}
              />
            </Form.Item>
          </Space>

          <Text strong style={{ display: 'block', marginBottom: 8 }}>
            药品明细
          </Text>
          <Form.List name="items" initialValue={[{}]}>
            {(fields, { add, remove }) => (
              <div style={{ maxHeight: 360, overflowY: 'auto' }}>
                {fields.map(({ key, name, ...restField }, index) => (
                  <Space
                    key={key}
                    style={{ display: 'flex', marginBottom: 8, alignItems: 'flex-start' }}
                    align="baseline"
                  >
                    <Form.Item
                      {...restField}
                      name={[name, 'drugId']}
                      rules={[
                        { required: true, message: '必填' },
                        { type: 'number', min: 1, message: '请输入有效药品ID' },
                      ]}
                    >
                      <InputNumber placeholder="药品ID" min={1} style={{ width: 90 }} />
                    </Form.Item>
                    <Form.Item
                      {...restField}
                      name={[name, 'dosage']}
                      rules={[{ required: true, message: '必填' }]}
                    >
                      <Input placeholder="用量" style={{ width: 80 }} />
                    </Form.Item>
                    <Form.Item
                      {...restField}
                      name={[name, 'frequency']}
                    >
                      <Input placeholder="频次" style={{ width: 100 }} />
                    </Form.Item>
                    <Form.Item
                      {...restField}
                      name={[name, 'usageMethod']}
                    >
                      <Input placeholder="用法" style={{ width: 80 }} />
                    </Form.Item>
                    <Form.Item
                      {...restField}
                      name={[name, 'days']}
                    >
                      <InputNumber placeholder="天数" min={1} style={{ width: 80 }} />
                    </Form.Item>
                    <Form.Item
                      {...restField}
                      name={[name, 'quantity']}
                    >
                      <InputNumber placeholder="数量" min={1} style={{ width: 80 }} />
                    </Form.Item>
                    {fields.length > 1 && (
                      <MinusCircleOutlined onClick={() => remove(name)} />
                    )}
                  </Space>
                ))}
                <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
                  添加药品
                </Button>
              </div>
            )}
          </Form.List>
        </Form>
      </Modal>
    </>
  );
}
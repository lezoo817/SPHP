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
  getDrugById,
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
  // 药品ID → 药品信息缓存（输入ID后自动带出名称/规格，并支撑存在性与数量校验）
  // state 驱动渲染；ref 与 state 同步维护，供校验器在 fetch 回调内立即读取最新值（避免 setState 未生效的竞态）
  const [drugInfoMap, setDrugInfoMap] = useState<Record<number, API.Drug>>({});
  const drugInfoMapRef = useRef<Record<number, API.Drug>>({});
  // 记录各行列最近一次自动回填的数量值：数量仍等于该值（未被手改）时跟随剂量变化更新；手改后不再覆盖
  const lastAutoFillRef = useRef<Record<number, number>>({});
  // 监听 items 数组变化（含数量/剂量编辑），用于实时计算库存超限提示
  const itemsWatch = Form.useWatch('items', createForm);

  /** 写入/删除药品信息缓存（state 驱动渲染，ref 供校验器即时读取） */
  const setDrugInfo = (value: number, drug?: API.Drug) => {
    setDrugInfoMap((m) => {
      const next = { ...m };
      if (drug) {
        next[value] = drug;
      } else {
        delete next[value];
      }
      return next;
    });
    if (drug) {
      drugInfoMapRef.current[value] = drug;
    } else {
      delete drugInfoMapRef.current[value];
    }
  };

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
    // 清空自动回填记录，避免上次会话残留影响新会话
    lastAutoFillRef.current = {};
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
          // 序列化：用量=数字+单位（如 "2粒"），频次=每日N次（如 "每日3次"）
          dosage: item.dosage && item.dosageUnit ? `${item.dosage}${item.dosageUnit}` : item.dosage,
          frequency: item.frequency ? `每日${item.frequency}次` : item.frequency,
          usageMethod: item.usageMethod,
          days: item.days,
          quantity: item.quantity,
          quantityUnit: item.quantityUnit || '盒',
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

  /** 从药品规格解析单盒/单瓶数量，如 "0.25g*24粒" → 24；无法解析返回 null */
  const parseSpecCount = (spec?: string): number | null => {
    if (!spec) return null;
    const m = /[×*xX]\s*(\d+(?:\.\d+)?)/.exec(spec);
    return m ? Number(m[1]) : null;
  };

  /** 药品ID变更：自动查询并缓存药品（带出名称/规格），同时触发该行 ID 存在性校验 */
  const handleDrugIdChange = (index: number, value: number | null) => {
    if (!value) return;
    getDrugById(value)
      .then((drug) => {
        setDrugInfo(value, drug);
        // 药品规格已就绪：若天数/频次/用量已填则回填数量
        autoFillQuantity(index);
        createForm.validateFields([['items', index, 'drugId']]).catch(() => {});
      })
      .catch(() => {
        setDrugInfo(value);
        createForm.validateFields([['items', index, 'drugId']]).catch(() => {});
      });
  };

  /**
   * 数量校验器：需求(天数×频次×用量) ≤ 发放(数量×规格单盒数)。
   * 任一项缺失或规格无法解析时通过（交由必填/后端兜底）。
   */
  const buildQuantityValidator = (index: number) => ({
    validator: (_: unknown, value?: number) => {
      if (!value) return Promise.resolve();
      const drugId: number | undefined = createForm.getFieldValue(['items', index, 'drugId']);
      const days: number | undefined = createForm.getFieldValue(['items', index, 'days']);
      const frequency: number | undefined = createForm.getFieldValue(['items', index, 'frequency']);
      const dosage: number | undefined = createForm.getFieldValue(['items', index, 'dosage']);
      const drug = drugId ? drugInfoMapRef.current[drugId] : undefined;
      const specCount = drug ? parseSpecCount(drug.specification) : null;
      if (days && frequency && dosage && specCount && specCount > 0) {
        const needed = days * frequency * dosage;
        const dispensed = value * specCount;
        if (needed > dispensed) {
          return Promise.reject(
            new Error(
              `数量不足：天数×频次×用量=${needed}，需 ≤ 数量×规格=${dispensed}（${value}×${specCount}）`,
            ),
          );
        }
      }
      return Promise.resolve();
    },
  });

  /** 根据天数/频次/用量与药品规格自动回填最小充足数量：ceil(需求 / 单盒数量) */
  const autoFillQuantity = (index: number) => {
    const drugId: number | undefined = createForm.getFieldValue(['items', index, 'drugId']);
    const days: number | undefined = createForm.getFieldValue(['items', index, 'days']);
    const frequency: number | undefined = createForm.getFieldValue(['items', index, 'frequency']);
    const dosage: number | undefined = createForm.getFieldValue(['items', index, 'dosage']);
    const drug = drugId ? drugInfoMapRef.current[drugId] : undefined;
    const specCount = drug ? parseSpecCount(drug.specification) : null;
    if (days && frequency && dosage && specCount && specCount > 0) {
      const computed = Math.ceil((days * frequency * dosage) / specCount);
      const current = createForm.getFieldValue(['items', index, 'quantity']);
      const lastAuto = lastAutoFillRef.current[index];
      // 数量为空，或仍等于上次自动回填值（用户未手动改）→ 跟随剂量变化更新；
      // 用户手动改过（≠上次自动值）→ 尊重手填值，不再覆盖（可自由调低）
      if (!current || current === lastAuto) {
        createForm.setFieldValue(['items', index, 'quantity'], computed);
        lastAutoFillRef.current[index] = computed;
      }
    }
  };

  /** 天数/频次/用量变化：自动回填数量并重校验该行数量（回填后满足需求则清除"数量不足"提示） */
  const handleDoseChange = (index: number) => {
    autoFillQuantity(index);
    if (createForm.getFieldValue(['items', index, 'quantity'])) {
      createForm.validateFields([['items', index, 'quantity']]).catch(() => {});
    }
  };

  /** 计算该行数量是否超出药品可用库存（超出返回提示信息，否则 null） */
  const renderStockWarn = (index: number): { quantity: number; stock: number } | null => {
    const item = itemsWatch?.[index];
    const quantity = item?.quantity;
    const drug = item?.drugId ? drugInfoMap[item.drugId] : undefined;
    if (quantity && drug?.availableStock != null && quantity > drug.availableStock) {
      return { quantity, stock: drug.availableStock };
    }
    return null;
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
                {
                  title: '数量',
                  dataIndex: 'quantity',
                  width: 70,
                  align: 'right',
                  render: (_, item) => (item.quantityUnit ? `${item.quantity}${item.quantityUnit}` : item.quantity),
                },
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
                {fields.map(({ key, name, ...restField }, index) => {
                  const stockWarn = renderStockWarn(index);
                  return (
                  <div
                    key={key}
                    style={{
                      border: '1px solid #f0f0f0',
                      borderRadius: 4,
                      padding: '8px 8px 0',
                      marginBottom: 8,
                    }}
                  >
                    {/* 第 1 行：药品ID + 药品名称 + 天数 + 频次 */}
                    <Space style={{ display: 'flex', marginBottom: 8 }} align="baseline" wrap>
                      <Form.Item
                        {...restField}
                        name={[name, 'drugId']}
                        // 关闭 onChange 自动校验：存在性须等 fetch 结果落地后再由 handleDrugIdChange 手动校验，避免输入瞬间误报
                        validateTrigger={false}
                        rules={[
                          { required: true, message: '请输入药品ID' },
                          {
                            validator: (_, value?: number) => {
                              if (!value) return Promise.resolve();
                              return drugInfoMapRef.current[value]
                                ? Promise.resolve()
                                : Promise.reject(new Error('药品不存在或已停用'));
                            },
                          },
                        ]}
                      >
                        <InputNumber
                          placeholder="药品ID"
                          min={1}
                          style={{ width: 90 }}
                          onChange={(v) => handleDrugIdChange(index, v)}
                        />
                      </Form.Item>
                      <Form.Item
                        noStyle
                        shouldUpdate={(prev, cur) =>
                          prev.items?.[index]?.drugId !== cur.items?.[index]?.drugId
                        }
                      >
                        {({ getFieldValue }) => {
                          const drugId: number | undefined = getFieldValue([
                            'items',
                            index,
                            'drugId',
                          ]);
                          const drug = drugId ? drugInfoMap[drugId] : undefined;
                          return (
                            <Input
                              readOnly
                              value={drug ? `${drug.name}（${drug.specification}）` : ''}
                              placeholder="自动获取药品名称"
                              style={{ width: 240 }}
                            />
                          );
                        }}
                      </Form.Item>
                      <Form.Item
                        {...restField}
                        name={[name, 'days']}
                        rules={[{ required: true, message: '天数必填' }]}
                      >
                        <InputNumber
                          addonAfter="天"
                          min={1}
                          placeholder="天数"
                          style={{ width: 100 }}
                          onChange={() => handleDoseChange(index)}
                        />
                      </Form.Item>
                      <Form.Item
                        {...restField}
                        name={[name, 'frequency']}
                        rules={[{ required: true, message: '频次必填' }]}
                      >
                        <InputNumber
                          addonBefore="每日"
                          addonAfter="次"
                          min={1}
                          placeholder="次数"
                          style={{ width: 130 }}
                          onChange={() => handleDoseChange(index)}
                        />
                      </Form.Item>
                      {fields.length > 1 && (
                        <MinusCircleOutlined onClick={() => remove(name)} />
                      )}
                    </Space>
                    {/* 第 2 行：用量(数值+单位) / 用法 / 数量(数值+单位) */}
                    <Space style={{ display: 'flex', marginBottom: 8 }} align="baseline" wrap>
                      <Form.Item
                        {...restField}
                        name={[name, 'dosage']}
                        rules={[{ required: true, message: '用量必填' }]}
                      >
                        <InputNumber
                          min={0}
                          placeholder="用量"
                          style={{ width: 80 }}
                          onChange={() => handleDoseChange(index)}
                        />
                      </Form.Item>
                      <Form.Item {...restField} name={[name, 'dosageUnit']} initialValue="粒">
                        <Select
                          style={{ width: 72 }}
                          options={[
                            { value: '粒', label: '粒' },
                            { value: '克', label: '克' },
                            { value: '剂', label: '剂' },
                            { value: '毫升', label: '毫升' },
                          ]}
                        />
                      </Form.Item>
                      <Form.Item
                        {...restField}
                        name={[name, 'usageMethod']}
                        rules={[{ required: true, message: '用法必填' }]}
                      >
                        <Select
                          placeholder="用法"
                          style={{ width: 90 }}
                          options={[
                            { value: '口服', label: '口服' },
                            { value: '外用', label: '外用' },
                          ]}
                        />
                      </Form.Item>
                      <Form.Item
                        {...restField}
                        name={[name, 'quantity']}
                        rules={[
                          { required: true, message: '数量必填' },
                          buildQuantityValidator(index),
                        ]}
                      >
                        <InputNumber
                          min={1}
                          placeholder="数量"
                          style={{ width: 80 }}
                        />
                      </Form.Item>
                      <Form.Item {...restField} name={[name, 'quantityUnit']} initialValue="盒">
                        <Select
                          style={{ width: 72 }}
                          options={[
                            { value: '盒', label: '盒' },
                            { value: '瓶', label: '瓶' },
                            { value: '剂', label: '剂' },
                          ]}
                        />
                      </Form.Item>
                    </Space>
                    {stockWarn && (
                      <div style={{ color: '#faad14', fontSize: 12, marginBottom: 8 }}>
                        ⚠ 药品数量 {stockWarn.quantity} 超出可用库存 {stockWarn.stock}
                      </div>
                    )}
                  </div>
                  );
                })}
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
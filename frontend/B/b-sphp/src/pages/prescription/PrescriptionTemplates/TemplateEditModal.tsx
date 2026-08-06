/**
 * 编辑处方模板弹窗。
 *
 * 预填现有模板数据，名称只读，仅允许编辑科室与药品明细。
 * 表单逻辑由 useTemplateEditForm 封装。
 */
import { Modal, Form, Button, Space, Select, Input, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useTemplateEditForm } from './useTemplateEditForm';
import type { TemplateFormValues } from './useTemplateCreateForm';
import TemplateItemRow from './TemplateItemRow';

const { Text } = Typography;

interface Props {
  open: boolean;
  record: API.PrescriptionTemplate | null;
  deptOptions: { label: string; value: number }[];
  onCancel: () => void;
  onSuccess: () => void;
}

export default function TemplateEditModal({
  open,
  record,
  deptOptions,
  onCancel,
  onSuccess,
}: Props) {
  const {
    form,
    submitting,
    drugInfoMap,
    isDrugKnown,
    handleEditSubmit,
    buildQuantityValidator,
    renderStockWarn,
    handleDrugIdChange,
    handleDoseChange,
  } = useTemplateEditForm({ open, record, onSuccess });

  return (
    <Modal
      title="编辑处方模板"
      open={open}
      onOk={handleEditSubmit}
      onCancel={onCancel}
      okText="保存"
      okButtonProps={{ loading: submitting }}
      width={700}
      destroyOnClose
    >
      <Form<TemplateFormValues> form={form} layout="vertical" initialValues={{ items: [{}] }}>
        <Space style={{ width: '100%' }} size={16}>
          <Form.Item name="name" label="模板名称" style={{ width: 280 }}>
            <Input disabled placeholder="名称不可修改" />
          </Form.Item>
          <Form.Item name="deptId" label="所属科室" style={{ width: 240 }}>
            <Select allowClear placeholder="全院通用（空）" options={deptOptions} />
          </Form.Item>
        </Space>

        <Text strong style={{ display: 'block', marginBottom: 8 }}>
          药品明细
        </Text>
        <Form.List name="items">
          {(fields, { add, remove }) => (
            <div style={{ maxHeight: 360, overflowY: 'auto' }}>
              {fields.map(({ key, name }, index) => (
                <TemplateItemRow
                  key={key}
                  name={name}
                  index={index}
                  showRemove={fields.length > 1}
                  drugInfoMap={drugInfoMap}
                  isDrugKnown={isDrugKnown}
                  stockWarn={renderStockWarn(index)}
                  quantityRule={buildQuantityValidator(index)}
                  onDrugIdChange={handleDrugIdChange}
                  onDoseChange={handleDoseChange}
                  onRemove={() => remove(name)}
                />
              ))}
              <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
                添加药品
              </Button>
            </div>
          )}
        </Form.List>
      </Form>
    </Modal>
  );
}
/**
 * 新建处方模板弹窗。
 *
 * 表单逻辑（状态/校验/提交）由 useTemplateCreateForm 封装，本组件仅负责 JSX 渲染。
 */
import { Modal, Form, Button, Space, Select, Input, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useTemplateCreateForm } from './useTemplateCreateForm';
import type { TemplateFormValues } from './useTemplateCreateForm';
import TemplateItemRow from './TemplateItemRow';

const { Text } = Typography;

interface Props {
  open: boolean;
  defaultDeptId?: number;
  deptOptions: { label: string; value: number }[];
  /** 是否可选「全院通用」（清空科室）：ADMIN/DEPT_HEAD 可；DOCTOR 固定本科室 */
  allowHospitalWide: boolean;
  onCancel: () => void;
  onSuccess: () => void;
}

export default function TemplateCreateModal({
  open,
  defaultDeptId,
  deptOptions,
  allowHospitalWide,
  onCancel,
  onSuccess,
}: Props) {
  const {
    form,
    submitting,
    drugInfoMap,
    isDrugKnown,
    handleCreateSubmit,
    buildQuantityValidator,
    renderStockWarn,
    handleDrugIdChange,
    handleDoseChange,
  } = useTemplateCreateForm({ open, defaultDeptId, onSuccess });

  return (
    <Modal
      title="新建处方模板"
      open={open}
      onOk={handleCreateSubmit}
      onCancel={onCancel}
      okText="保存"
      okButtonProps={{ loading: submitting }}
      width={700}
      destroyOnHidden
    >
      <Form<TemplateFormValues> form={form} layout="vertical" initialValues={{ items: [{}] }}>
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
              allowClear={allowHospitalWide}
              disabled={!allowHospitalWide}
              placeholder={allowHospitalWide ? '全院通用（空）' : undefined}
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

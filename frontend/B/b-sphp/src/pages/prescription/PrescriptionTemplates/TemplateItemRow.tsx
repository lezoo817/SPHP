/**
 * 新建模板弹窗中的单行药品明细（Form.List 行）。
 *
 * 纯展示组件：受控路径/校验规则/回调均由父组件传入，自身不持有业务状态。
 * 含两行输入：药品ID+名称+天数+频次 / 用量+单位+用法+数量+单位，底部展示库存超限提示。
 */
import { Form, Input, InputNumber, Select, Space } from 'antd';
import { MinusCircleOutlined } from '@ant-design/icons';
import type { Rule } from 'antd/es/form';

interface TemplateItemRowProps {
  /** Form.List 行名（数组下标，用于 Form.Item 的 name 路径） */
  name: number;
  /** 行序号（用于回调定位与库存提示计算） */
  index: number;
  /** 是否显示删除按钮（多行时） */
  showRemove: boolean;
  /** 药品信息缓存（state，驱动药品名称/规格自动带出） */
  drugInfoMap: Record<number, API.Drug>;
  /** 药品 ID 是否已知（读 ref 的最新缓存，供存在性校验即时判断） */
  isDrugKnown: (value: number) => boolean;
  /** 库存超限提示（null=未超限） */
  stockWarn: { quantity: number; stock: number } | null;
  /** 数量校验规则（需求 ≤ 发放） */
  quantityRule: Rule;
  /** 药品 ID 变更回调 */
  onDrugIdChange: (index: number, value: number | null) => void;
  /** 天数/频次/用量变化回调（自动回填数量） */
  onDoseChange: (index: number) => void;
  /** 删除本行回调 */
  onRemove: () => void;
}

export default function TemplateItemRow({
  name,
  index,
  showRemove,
  drugInfoMap,
  isDrugKnown,
  stockWarn,
  quantityRule,
  onDrugIdChange,
  onDoseChange,
  onRemove,
}: TemplateItemRowProps) {
  return (
    <div
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
          name={[name, 'drugId']}
          // 关闭 onChange 自动校验：存在性须等 fetch 结果落地后再由 onDrugIdChange 手动校验，避免输入瞬间误报
          validateTrigger={false}
          rules={[
            { required: true, message: '请输入药品ID' },
            {
              validator: (_: unknown, value?: number) => {
                if (!value) return Promise.resolve();
                return isDrugKnown(value)
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
            onChange={(v) => onDrugIdChange(index, v)}
          />
        </Form.Item>
        <Form.Item
          noStyle
          shouldUpdate={(prev, cur) => prev.items?.[index]?.drugId !== cur.items?.[index]?.drugId}
        >
          {({ getFieldValue }) => {
            const drugId: number | undefined = getFieldValue(['items', index, 'drugId']);
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
        <Form.Item name={[name, 'days']} rules={[{ required: true, message: '天数必填' }]}>
          <InputNumber
            addonAfter="天"
            min={1}
            placeholder="天数"
            style={{ width: 100 }}
            onChange={() => onDoseChange(index)}
          />
        </Form.Item>
        <Form.Item
          name={[name, 'frequency']}
          rules={[{ required: true, message: '频次必填' }]}
        >
          <InputNumber
            addonBefore="每日"
            addonAfter="次"
            min={1}
            placeholder="次数"
            style={{ width: 130 }}
            onChange={() => onDoseChange(index)}
          />
        </Form.Item>
        {showRemove && <MinusCircleOutlined onClick={onRemove} />}
      </Space>
      {/* 第 2 行：用量(数值+单位) / 用法 / 数量(数值+单位) */}
      <Space style={{ display: 'flex', marginBottom: 8 }} align="baseline" wrap>
        <Form.Item name={[name, 'dosage']} rules={[{ required: true, message: '用量必填' }]}>
          <InputNumber
            min={0}
            placeholder="用量"
            style={{ width: 80 }}
            onChange={() => onDoseChange(index)}
          />
        </Form.Item>
        <Form.Item name={[name, 'dosageUnit']} initialValue="粒">
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
          name={[name, 'quantity']}
          rules={[{ required: true, message: '数量必填' }, quantityRule]}
        >
          <InputNumber min={1} placeholder="数量" style={{ width: 80 }} />
        </Form.Item>
        <Form.Item name={[name, 'quantityUnit']} initialValue="盒">
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
}

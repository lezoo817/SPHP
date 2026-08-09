/**
 * 接诊台开处方弹窗。
 *
 * - Form.List 多行药品明细：药品搜索下拉（getDrugs）+ 用量/频次/用法/天数/数量；
 * - 提交后展示后端风险拦截结果（WARNING 提示 / AUDIT 待审核 / 无风险直接通过）；
 * - 成功后调用方刷新已开处方列表，本弹窗关闭。
 */
import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Form, Input, InputNumber, Modal, Select, Space, Spin, Tag, message } from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { getDrugs } from '@/services/admin';
import { getErrorMessage } from '@/utils/error';

/** 单行药品明细表单值（对齐 PrescriptionSubmitRequest.ItemDTO） */
interface PrescriptionItemFormValue {
  drugId?: number;
  dosage?: string;
  frequency?: string;
  usageMethod?: string;
  days?: number;
  quantity?: number;
}

interface Props {
  open: boolean;
  submitting: boolean;
  onCancel: () => void;
  onSubmit: (
    items: API.PrescriptionSubmitReq['items'],
  ) => Promise<API.PrescriptionSubmitResult>;
}

/** 用法选项 */
const USAGE_METHODS = [
  { value: '口服', label: '口服' },
  { value: '外用', label: '外用' },
  { value: '注射', label: '注射' },
  { value: '含服', label: '含服' },
];

/** 搜索结果行转为 Select 选项（label 带规格，便于医生区分同名药品） */
function toDrugOptions(list: API.Drug[]): { label: string; value: number }[] {
  return list.map((d) => ({
    label: d.specification ? `${d.name}（${d.specification}）` : d.name,
    value: d.id,
  }));
}

export default function PrescriptionFormModal({
  open,
  submitting,
  onCancel,
  onSubmit,
}: Props) {
  const [form] = Form.useForm();
  /** 药品搜索结果缓存（避免每次展开下拉重复请求） */
  const [drugOptions, setDrugOptions] = useState<{ label: string; value: number }[]>([]);
  const drugSearchLoadingRef = useRef(false);
  /** 提交成功后的风险拦截结果（展示后再关闭） */
  const [result, setResult] = useState<API.PrescriptionSubmitResult | null>(null);

  /** 打开弹窗时重置表单与结果 */
  useEffect(() => {
    if (open) {
      form.resetFields();
      setResult(null);
      // 预拉一页启用药品供下拉首屏使用
      if (drugOptions.length === 0) {
        getDrugs({ page: 1, size: 50, status: 'ENABLED' })
          .then((res) => setDrugOptions(toDrugOptions(res.list ?? [])))
          .catch(() => {});
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  /** 药品远程搜索（按名称模糊；静默失败保留已有选项） */
  const handleDrugSearch = async (keyword: string) => {
    if (drugSearchLoadingRef.current) return;
    drugSearchLoadingRef.current = true;
    try {
      const res = await getDrugs({
        page: 1,
        size: 50,
        status: 'ENABLED',
        name: keyword || undefined,
      });
      setDrugOptions(toDrugOptions(res.list ?? []));
    } catch {
      // 搜索失败保持原选项，避免打断录入
    } finally {
      drugSearchLoadingRef.current = false;
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      const items = (values.items ?? []) as PrescriptionItemFormValue[];
      const payload = items.map((it) => ({
        drugId: it.drugId as number,
        dosage: (it.dosage ?? '').trim(),
        frequency: (it.frequency ?? '').trim(),
        usageMethod: (it.usageMethod ?? '').trim(),
        days: it.days as number,
        quantity: it.quantity as number,
      }));
      const res = await onSubmit(payload);
      setResult(res);
    } catch (err: unknown) {
      const errMsg = getErrorMessage(err, '');
      if (errMsg) message.error(errMsg);
    }
  };

  /** 提交成功且无风险 / 已进审核队列：可关闭弹窗 */
  const handleDone = () => {
    setResult(null);
    onCancel();
  };

  return (
    <Modal
      title="开处方"
      open={open}
      onCancel={onCancel}
      width={720}
      footer={[
        <Button key="cancel" onClick={handleDone}>
          关闭
        </Button>,
        <Button
          key="submit"
          type="primary"
          loading={submitting}
          disabled={Boolean(result)}
          onClick={handleSubmit}
        >
          提交处方
        </Button>,
      ]}
    >
      <Spin spinning={submitting}>
        <Form form={form} layout="vertical" disabled={Boolean(result)}>
          <Form.List name="items">
            {(fields, { add, remove }) => (
              <>
                {fields.map((field) => (
                  <div
                    key={field.key}
                    style={{
                      border: '1px solid #f0f0f0',
                      borderRadius: 4,
                      padding: '8px 12px 0',
                      marginBottom: 8,
                    }}
                  >
                    <Space style={{ display: 'flex', marginBottom: 8 }} align="baseline" wrap>
                      <Form.Item
                        name={[field.name, 'drugId']}
                        rules={[{ required: true, message: '请选择药品' }]}
                        style={{ marginBottom: 8 }}
                      >
                        <Select
                          showSearch
                          placeholder="搜索药品名称"
                          style={{ width: 260 }}
                          options={drugOptions}
                          onSearch={handleDrugSearch}
                          filterOption={(input, option) =>
                            String(option?.label ?? '').toLowerCase().includes(input.toLowerCase())
                          }
                        />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, 'usageMethod']}
                        rules={[{ required: true, message: '用法必填' }]}
                        style={{ marginBottom: 8 }}
                      >
                        <Select
                          placeholder="用法"
                          style={{ width: 90 }}
                          options={USAGE_METHODS}
                        />
                      </Form.Item>
                      <Form.Item name={[field.name, 'days']} rules={[{ required: true, message: '天数必填' }]} style={{ marginBottom: 8 }}>
                        <InputNumber addonAfter="天" min={1} placeholder="天数" style={{ width: 100 }} />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, 'quantity']}
                        rules={[{ required: true, message: '数量必填' }]}
                        style={{ marginBottom: 8 }}
                      >
                        <InputNumber addonAfter="盒/瓶" min={1} placeholder="数量" style={{ width: 120 }} />
                      </Form.Item>
                      {fields.length > 1 && (
                        <MinusCircleOutlined onClick={() => remove(field.name)} />
                      )}
                    </Space>
                    <Space style={{ display: 'flex', marginBottom: 8 }} align="baseline" wrap>
                      <Form.Item
                        name={[field.name, 'dosage']}
                        rules={[{ required: true, message: '用量必填' }]}
                        style={{ marginBottom: 8 }}
                      >
                        <Input placeholder="用量（如 1片 / 5ml）" style={{ width: 160 }} />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, 'frequency']}
                        rules={[{ required: true, message: '频次必填' }]}
                        style={{ marginBottom: 8 }}
                      >
                        <Input placeholder="频次（如 每日3次 / QD）" style={{ width: 200 }} />
                      </Form.Item>
                    </Space>
                  </div>
                ))}
                <Button
                  type="dashed"
                  block
                  icon={<PlusOutlined />}
                  onClick={() => add()}
                  disabled={Boolean(result)}
                >
                  添加药品
                </Button>
              </>
            )}
          </Form.List>
        </Form>

        {result && (
          <Alert
            style={{ marginTop: 12 }}
            type={result.riskWarnings.some((w) => w.level === 'ERROR') ? 'error' : 'success'}
            showIcon
            message={
              <Space direction="vertical" size={2}>
                <span>
                  处方已提交 ·{' '}
                  <Tag color={result.status === 'APPROVED' ? 'green' : 'orange'}>
                    {result.status === 'APPROVED'
                      ? '已生效'
                      : result.status === 'SUBMITTED'
                        ? '待审核'
                        : result.status}
                  </Tag>
                </span>
                {result.riskWarnings.length > 0 && (
                  <Space direction="vertical" size={2} style={{ marginTop: 4 }}>
                    {result.riskWarnings.map((w, i) => (
                      <div key={i} style={{ fontSize: 12 }}>
                        {w.level === 'ERROR' ? '❌' : w.level === 'AUDIT' ? '⚠️ 待审核' : 'ℹ️'}{' '}
                        {w.message}
                      </div>
                    ))}
                  </Space>
                )}
              </Space>
            }
          />
        )}
      </Spin>
    </Modal>
  );
}

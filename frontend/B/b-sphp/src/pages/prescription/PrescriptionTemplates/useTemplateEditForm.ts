/**
 * 编辑处方模板弹窗的表单逻辑 Hook。
 *
 * 预填现有模板数据，提交时调用 updateTemplate API。
 * 模板名称只读不可改，仅允许编辑科室与药品明细。
 */
import { useEffect, useRef, useState } from 'react';
import { Form, message } from 'antd';
import type { Rule } from 'antd/es/form';
import { updateTemplate, getDrugById } from '@/services/admin';
import { getErrorMessage } from '@/utils/error';
import type { TemplateFormValues, TemplateItemFormValue } from './useTemplateCreateForm';

/** 校验通过后的行数据 */
interface ValidatedTemplateItem
  extends Required<
    Pick<
      TemplateItemFormValue,
      'drugId' | 'days' | 'frequency' | 'dosage' | 'usageMethod' | 'quantity'
    >
  > {
  dosageUnit?: string;
  quantityUnit?: string;
}

interface Options {
  open: boolean;
  /** 待编辑的模板数据 */
  record: API.PrescriptionTemplate | null;
  onSuccess: () => void;
}

export function useTemplateEditForm({ open, record, onSuccess }: Options) {
  const [form] = Form.useForm<TemplateFormValues>();
  const [submitting, setSubmitting] = useState(false);
  /** 药品ID → 药品信息缓存 */
  const [drugInfoMap, setDrugInfoMap] = useState<Record<number, API.Drug>>({});
  const drugInfoMapRef = useRef<Record<number, API.Drug>>({});
  /** 记录各行列最近一次自动回填的数量值 */
  const lastAutoFillRef = useRef<Record<number, number>>({});
  /** 监听 items 数组变化，用于实时计算库存超限提示 */
  const itemsWatch = Form.useWatch<TemplateItemFormValue[]>('items', form);

  /** 打开弹窗时预填表单数据 */
  useEffect(() => {
    if (open && record) {
      form.resetFields();
      lastAutoFillRef.current = {};
      // 预填模板名称、科室
      form.setFieldsValue({
        name: record.name,
        deptId: record.deptId || undefined,
      });
      // 预填药品明细：将后端字符串 dosage/frequency 反解析为数值+单位
      const items = (record.items ?? []).map((item) => ({
        drugId: item.drugId,
        days: item.days,
        frequency: parseFrequencyNum(item.frequency),
        dosage: parseDosageNum(item.dosage),
        dosageUnit: parseDosageUnit(item.dosage),
        usageMethod: item.usageMethod,
        quantity: item.quantity,
        quantityUnit: item.quantityUnit || '盒',
      }));
      form.setFieldValue('items', items.length > 0 ? items : [{}]);

      // 预加载各药品信息
      const drugIds = record.items.map((i) => i.drugId).filter(Boolean);
      drugIds.forEach((id) => {
        getDrugById(id)
          .then((drug) => {
            setDrugInfoMap((m) => ({ ...m, [id]: drug }));
            drugInfoMapRef.current[id] = drug;
          })
          .catch(() => {});
      });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  /** 从用量字符串解析数值，如 "2粒" → 2，无法解析返回 undefined */
  const parseDosageNum = (dosage?: string): number | undefined => {
    if (!dosage) return undefined;
    const m = /^(\d+(?:\.\d+)?)/.exec(dosage);
    return m ? Number(m[1]) : undefined;
  };

  /** 从用量字符串解析单位，如 "2粒" → "粒"，无法解析返回 undefined */
  const parseDosageUnit = (dosage?: string): string | undefined => {
    if (!dosage) return undefined;
    const m = /^\d+(?:\.\d+)?(.+)$/.exec(dosage);
    return m ? m[1] : undefined;
  };

  /** 从频次解析每日次数，如 "每日3次" → 3 */
  const parseFrequencyNum = (frequency?: string): number | undefined => {
    if (!frequency) return undefined;
    const m = /(\d+)/.exec(frequency);
    return m ? Number(m[1]) : undefined;
  };

  /** 写入/删除药品信息缓存 */
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

  /** 药品 ID 是否已知 */
  const isDrugKnown = (value: number): boolean => Boolean(drugInfoMapRef.current[value]);

  /** 从药品规格解析单盒/单瓶数量 */
  const parseSpecCount = (spec?: string): number | null => {
    if (!spec) return null;
    const m = /[×*xX]\s*(\d+(?:\.\d+)?)/.exec(spec);
    return m ? Number(m[1]) : null;
  };

  /** 自动回填最小充足数量 */
  const autoFillQuantity = (index: number) => {
    const drugId: number | undefined = form.getFieldValue(['items', index, 'drugId']);
    const days: number | undefined = form.getFieldValue(['items', index, 'days']);
    const frequency: number | undefined = form.getFieldValue(['items', index, 'frequency']);
    const dosage: number | undefined = form.getFieldValue(['items', index, 'dosage']);
    const drug = drugId ? drugInfoMapRef.current[drugId] : undefined;
    const specCount = drug ? parseSpecCount(drug.specification) : null;
    if (days && frequency && dosage && specCount && specCount > 0) {
      const computed = Math.ceil((days * frequency * dosage) / specCount);
      const current = form.getFieldValue(['items', index, 'quantity']);
      const lastAuto = lastAutoFillRef.current[index];
      if (!current || current === lastAuto) {
        form.setFieldValue(['items', index, 'quantity'], computed);
        lastAutoFillRef.current[index] = computed;
      }
    }
  };

  /** 药品ID变更 */
  const handleDrugIdChange = (index: number, value: number | null) => {
    if (!value) return;
    getDrugById(value)
      .then((drug) => {
        setDrugInfo(value, drug);
        autoFillQuantity(index);
        form.validateFields([['items', index, 'drugId']]).catch(() => {});
      })
      .catch(() => {
        setDrugInfo(value);
        form.validateFields([['items', index, 'drugId']]).catch(() => {});
      });
  };

  /** 数量校验规则 */
  const buildQuantityValidator = (index: number): Rule => ({
    validator: (_: unknown, value?: number) => {
      if (!value) return Promise.resolve();
      const drugId: number | undefined = form.getFieldValue(['items', index, 'drugId']);
      const days: number | undefined = form.getFieldValue(['items', index, 'days']);
      const frequency: number | undefined = form.getFieldValue(['items', index, 'frequency']);
      const dosage: number | undefined = form.getFieldValue(['items', index, 'dosage']);
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

  /** 天数/频次/用量变化 */
  const handleDoseChange = (index: number) => {
    autoFillQuantity(index);
    if (form.getFieldValue(['items', index, 'quantity'])) {
      form.validateFields([['items', index, 'quantity']]).catch(() => {});
    }
  };

  /** 计算库存超限提示 */
  const renderStockWarn = (index: number): { quantity: number; stock: number } | null => {
    const item = itemsWatch?.[index];
    const quantity = item?.quantity;
    const drug = item?.drugId ? drugInfoMap[item.drugId] : undefined;
    if (
      quantity &&
      drug?.availableStock !== undefined &&
      drug?.availableStock !== null &&
      quantity > drug.availableStock
    ) {
      return { quantity, stock: drug.availableStock };
    }
    return null;
  };

  /** 提交更新 */
  const handleEditSubmit = async () => {
    if (!record) return;
    try {
      const values = await form.validateFields();
      const items = (values.items ?? []) as ValidatedTemplateItem[];
      setSubmitting(true);
      await updateTemplate(record.id, {
        // 名称仅用于满足 SaveTemplateReq 类型，后端更新时忽略
        name: record.name,
        deptId: values.deptId || undefined,
        items: items.map((item) => ({
          drugId: item.drugId,
          dosage:
            item.dosage && item.dosageUnit
              ? `${item.dosage}${item.dosageUnit}`
              : String(item.dosage),
          frequency: `每日${item.frequency}次`,
          usageMethod: item.usageMethod,
          days: item.days,
          quantity: item.quantity,
          quantityUnit: item.quantityUnit || '盒',
        })),
      });
      message.success('模板更新成功');
      onSuccess();
    } catch (err: unknown) {
      const errMsg = getErrorMessage(err, '');
      if (errMsg) message.error(errMsg);
    } finally {
      setSubmitting(false);
    }
  };

  return {
    form,
    submitting,
    drugInfoMap,
    isDrugKnown,
    handleEditSubmit,
    buildQuantityValidator,
    renderStockWarn,
    handleDrugIdChange,
    handleDoseChange,
  };
}
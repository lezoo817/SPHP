/**
 * 新建处方模板弹窗的表单逻辑 Hook。
 *
 * 封装表单实例、药品信息缓存（state 驱动渲染 + ref 即时校验）、
 * 数量自动回填与「需求 ≤ 发放」校验、库存超限提示，以及提交序列化；
 * JSX 渲染交由 TemplateCreateModal 完成。
 */
import { useEffect, useRef, useState } from 'react';
import { Form, message } from 'antd';
import type { Rule } from 'antd/es/form';
import { saveTemplate, getDrugById } from '@/services/admin';
import { getErrorMessage } from '@/utils/error';

/** 单行药品明细（表单原始值：数值型字段与单位分离，提交时序列化） */
export interface TemplateItemFormValue {
  drugId?: number;
  days?: number;
  frequency?: number;
  dosage?: number;
  dosageUnit?: string;
  usageMethod?: string;
  quantity?: number;
  quantityUnit?: string;
}

/** 模板表单值 */
export interface TemplateFormValues {
  name: string;
  deptId?: number;
  items?: TemplateItemFormValue[];
}

/** 校验通过后的行数据：必填字段已由表单规则保证存在 */
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
  defaultDeptId?: number;
  onSuccess: () => void;
}

export function useTemplateCreateForm({ open, defaultDeptId, onSuccess }: Options) {
  const [form] = Form.useForm<TemplateFormValues>();
  const [submitting, setSubmitting] = useState(false);
  /** 药品ID → 药品信息缓存（state 驱动渲染；ref 供校验器即时读取，避免 setState 未生效的竞态） */
  const [drugInfoMap, setDrugInfoMap] = useState<Record<number, API.Drug>>({});
  const drugInfoMapRef = useRef<Record<number, API.Drug>>({});
  /** 记录各行列最近一次自动回填的数量值：数量仍等于该值（未被手改）时跟随剂量变化更新；手改后不再覆盖 */
  const lastAutoFillRef = useRef<Record<number, number>>({});
  /** 监听 items 数组变化（含数量/剂量编辑），用于实时计算库存超限提示 */
  const itemsWatch = Form.useWatch<TemplateItemFormValue[]>('items', form);

  /** 打开弹窗时重置表单并默认当前科室（destroyOnClose 下仍保险） */
  useEffect(() => {
    if (open) {
      form.resetFields();
      lastAutoFillRef.current = {};
      if (defaultDeptId) {
        form.setFieldsValue({ deptId: defaultDeptId });
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

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

  /** 药品 ID 是否已知（读 ref 的最新缓存，供存在性校验即时判断） */
  const isDrugKnown = (value: number): boolean => Boolean(drugInfoMapRef.current[value]);

  /** 从药品规格解析单盒/单瓶数量，如 "0.25g*24粒" → 24；无法解析返回 null */
  const parseSpecCount = (spec?: string): number | null => {
    if (!spec) return null;
    const m = /[×*xX]\s*(\d+(?:\.\d+)?)/.exec(spec);
    return m ? Number(m[1]) : null;
  };

  /** 根据天数/频次/用量与药品规格自动回填最小充足数量：ceil(需求 / 单盒数量) */
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
      // 数量为空，或仍等于上次自动回填值（用户未手动改）→ 跟随剂量变化更新；
      // 用户手动改过（≠上次自动值）→ 尊重手填值，不再覆盖（可自由调低）
      if (!current || current === lastAuto) {
        form.setFieldValue(['items', index, 'quantity'], computed);
        lastAutoFillRef.current[index] = computed;
      }
    }
  };

  /** 药品ID变更：自动查询并缓存药品（带出名称/规格），同时触发该行 ID 存在性校验 */
  const handleDrugIdChange = (index: number, value: number | null) => {
    if (!value) return;
    getDrugById(value)
      .then((drug) => {
        setDrugInfo(value, drug);
        // 药品规格已就绪：若天数/频次/用量已填则回填数量
        autoFillQuantity(index);
        form.validateFields([['items', index, 'drugId']]).catch(() => {});
      })
      .catch(() => {
        setDrugInfo(value);
        form.validateFields([['items', index, 'drugId']]).catch(() => {});
      });
  };

  /**
   * 数量校验规则：需求(天数×频次×用量) ≤ 发放(数量×规格单盒数)。
   * 任一项缺失或规格无法解析时通过（交由必填/后端兜底）。
   */
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

  /** 天数/频次/用量变化：自动回填数量并重校验该行数量（回填后满足需求则清除"数量不足"提示） */
  const handleDoseChange = (index: number) => {
    autoFillQuantity(index);
    if (form.getFieldValue(['items', index, 'quantity'])) {
      form.validateFields([['items', index, 'quantity']]).catch(() => {});
    }
  };

  /** 计算该行数量是否超出药品可用库存（超出返回提示信息，否则 null） */
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

  /** 提交：校验通过后序列化为 SaveTemplateReq（用量/频次拼接单位文本） */
  const handleCreateSubmit = async () => {
    try {
      const values = await form.validateFields();
      const items = (values.items ?? []) as ValidatedTemplateItem[];
      setSubmitting(true);
      await saveTemplate({
        name: values.name,
        deptId: values.deptId || undefined,
        items: items.map((item) => ({
          drugId: item.drugId,
          // 序列化：用量=数字+单位（如 "2粒"），频次=每日N次（如 "每日3次"）；单位缺失时按数字转字符串
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
      message.success('模板创建成功');
      onSuccess();
    } catch (err: unknown) {
      // 校验失败（无 message）静默；业务失败展示后端消息
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
    handleCreateSubmit,
    buildQuantityValidator,
    renderStockWarn,
    handleDrugIdChange,
    handleDoseChange,
  };
}

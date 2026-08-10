/**
 * 处方药品明细编辑表单（公共组件）。
 *
 * 接诊挂号（PrescriptionFormModal）与在线问诊（OnlineConsultation）复用的开方明细编辑区：
 * - 顶部「处方模板」下拉：带入模板明细到表单，医生可增删改后提交；
 * - Form.List 两行式药品明细：一行 = 药品搜索下拉 + 用法下拉 + 天数 + 数量（带单位后缀），
 *   二行 = 用量 + 频次，删除行用 MinusCircleOutlined；
 * - 药品搜索接口经 {@code fetchDrugs} 注入，区分通用药品目录（getDrugs）与
 *   医生开方可用药品（getDoctorDrugs）；
 * - {@code onItemsChange} 对外上报明细变化（接诊挂号用于实时风险预检），
 *   {@code renderRowWarnings} 提供单行预警渲染槽（预检命中按 drugId 归属到对应药品行下方）。
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Button,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Spin,
  message,
} from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import type { FormInstance } from 'antd';
import { PAGE_SIZE_50 } from '@/constants/pageSize';
import { STATUS_ENABLED } from '@/constants/businessStatus';
import styles from './PrescriptionItemsForm.module.less';

/** 单行药品明细表单值（对齐 API.PrescriptionSubmitReq['items']，录入中字段可为空） */
export interface PrescriptionItemFormValue {
  drugId?: number;
  dosage?: string;
  frequency?: string;
  usageMethod?: string;
  days?: number;
  quantity?: number;
}

/** 可带入表单的药品明细（来源：处方模板 / 被驳回处方详情），drugName 用于下拉回显 */
export interface PrescriptionPrefillItem {
  drugId: number;
  drugName?: string;
  dosage: string;
  frequency?: string;
  usageMethod: string;
  days: number;
  quantity: number;
}

/** 空明细兜底（固定引用，避免每次渲染新建数组导致 onItemsChange 误触发） */
const EMPTY_ITEMS: PrescriptionItemFormValue[] = [];

/** 用法选项（医生开方常用给药途径） */
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

interface PrescriptionItemsFormProps {
  /** 表单实例（父级创建，用于提交校验 / 重置 / 预填） */
  form: FormInstance;
  /** 表单是否锁定（提交中 / 提交成功后禁编辑） */
  disabled?: boolean;
  /** 药品搜索接口：医生开方用 getDoctorDrugs，接诊挂号用 getDrugs */
  fetchDrugs: (params: API.DrugListParams) => Promise<API.PageResult<API.Drug>>;
  /** 处方模板列表（父级已加载） */
  templates?: API.PrescriptionTemplate[];
  /** 模板加载中 */
  templateLoading?: boolean;
  /** 表单初始值（如在线问诊默认一行空明细）；仅首次挂载生效 */
  initialValues?: { items?: PrescriptionItemFormValue[] };
  /** 弹窗类场景传入：打开时重置表单，有预填明细（驳回重开）则重置后带入 */
  open?: boolean;
  /** 打开时预填的药品明细（驳回重开场景），空数组表示不预填 */
  initialItems?: PrescriptionPrefillItem[];
  /** 模板区与明细之间插入的内容（如重复用药置顶汇总预警） */
  topExtra?: React.ReactNode;
  /** 药品明细变化回调（实时风险预检用） */
  onItemsChange?: (items: PrescriptionItemFormValue[]) => void;
  /** 单行预警渲染槽：按行名给出命中预警节点（未命中返回 null） */
  renderRowWarnings?: (fieldName: number) => React.ReactNode;
}

export default function PrescriptionItemsForm({
  form,
  disabled = false,
  fetchDrugs,
  templates = [],
  templateLoading = false,
  initialValues,
  open = false,
  initialItems,
  topExtra,
  onItemsChange,
  renderRowWarnings,
}: PrescriptionItemsFormProps) {
  /** 药品搜索结果缓存（避免每次展开下拉重复请求） */
  const [drugOptions, setDrugOptions] = useState<{ label: string; value: number }[]>([]);
  const drugSearchLoadingRef = useRef(false);

  /** 实时监听药品明细，变化后通过 onItemsChange 上报（父级驱动风险预检等） */
  const itemsWatch = Form.useWatch('items', form);

  useEffect(() => {
    onItemsChange?.(itemsWatch ?? EMPTY_ITEMS);
  }, [itemsWatch, onItemsChange]);

  /** 首屏预拉一页启用药品供下拉使用；组件卸载（离开开方区）时丢弃慢响应结果 */
  useEffect(() => {
    let cancelled = false;
    fetchDrugs({ page: 1, size: PAGE_SIZE_50, status: STATUS_ENABLED })
      .then((res) => {
        if (!cancelled) setDrugOptions(toDrugOptions(res.list ?? []));
      })
      .catch(() => {
        // 首屏药品加载失败不阻塞开方，下拉为空可手动搜索
      });
    return () => {
      cancelled = true;
    };
  }, [fetchDrugs]);

  /** 将模板 / 驳回处方的药品明细填入表单（合并药品选项，保证 Select 正常回显药名） */
  const applyItemsToForm = useCallback(
    (items: PrescriptionPrefillItem[]) => {
      const rows: PrescriptionItemFormValue[] = items.map((it) => ({
        drugId: it.drugId,
        dosage: it.dosage ?? '',
        frequency: it.frequency ?? '',
        usageMethod: it.usageMethod ?? '',
        days: it.days,
        quantity: it.quantity,
      }));
      // 把模板/驳回处方中不在药品选项里的药品补进 options，避免下拉只显示数字 id
      setDrugOptions((prev) => {
        const existingIds = new Set(prev.map((o) => o.value));
        const extra = items
          .filter((it) => it.drugName && !existingIds.has(it.drugId))
          .map((it) => ({ label: it.drugName as string, value: it.drugId }));
        return extra.length ? [...prev, ...extra] : prev;
      });
      form.setFieldsValue({ items: rows });
    },
    [form],
  );

  /** 弹窗打开时重置表单；有预填明细（驳回重开）则重置后带入。
   *  重置与预填在同一 effect 内保证顺序（先清空再填充），避免上次开方明细残留。 */
  useEffect(() => {
    if (!open) return;
    form.resetFields();
    if (initialItems && initialItems.length > 0) {
      applyItemsToForm(initialItems);
    }
  }, [open, form, initialItems, applyItemsToForm]);

  /** 药品远程搜索（按名称模糊；静默失败保留已有选项） */
  const handleDrugSearch = async (keyword: string) => {
    if (drugSearchLoadingRef.current) return;
    drugSearchLoadingRef.current = true;
    try {
      const res = await fetchDrugs({
        page: 1,
        size: PAGE_SIZE_50,
        status: STATUS_ENABLED,
        name: keyword || undefined,
      });
      setDrugOptions(toDrugOptions(res.list ?? []));
    } catch {
      // 搜索失败保持原选项，避免打断录入
    } finally {
      drugSearchLoadingRef.current = false;
    }
  };

  /** 选择模板：带入明细到表单（可编辑后提交） */
  const handleTemplateSelect = (templateId: number) => {
    const template = templates.find((t) => t.id === templateId);
    if (template?.items?.length) {
      applyItemsToForm(template.items);
      message.success(`已带入模板「${template.name}」，可修改后提交`);
    }
  };

  /** 模板下拉选项：纯字符串标签（名称 + 项数 + 科室），保证 showSearch 可按文本过滤 */
  const templateOptions: { label: string; value: number }[] = templates.map((t) => ({
    label: `${t.name}（${t.itemCount ?? t.items?.length ?? 0} 项${t.deptName ? ` · ${t.deptName}` : ' · 全院通用'}）`,
    value: t.id,
  }));

  return (
    <>
      {/* 处方模板带入入口（可编辑后提交） */}
      <div className={styles.templateArea}>
        <Select
          allowClear
          showSearch
          placeholder="从处方模板带入明细（可修改）"
          style={{ width: 340 }}
          loading={templateLoading}
          disabled={disabled}
          options={templateOptions}
          optionFilterProp="label"
          onChange={handleTemplateSelect}
          notFoundContent={templateLoading ? <Spin size="small" /> : '暂无可用模板'}
        />
      </div>

      {topExtra}

      <Form form={form} layout="vertical" initialValues={initialValues} disabled={disabled}>
        <Form.List name="items">
          {(fields, { add, remove }) => (
            <>
              {fields.map((field) => (
                <div key={field.key} className={styles.itemRow}>
                  <Space className={styles.itemLine} align="baseline" wrap>
                    <Form.Item
                      name={[field.name, 'drugId']}
                      rules={[{ required: true, message: '请选择药品' }]}
                      className={styles.formItem}
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
                      className={styles.formItem}
                    >
                      <Select placeholder="用法" style={{ width: 90 }} options={USAGE_METHODS} />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'days']}
                      rules={[{ required: true, message: '天数必填' }]}
                      className={styles.formItem}
                    >
                      <InputNumber addonAfter="天" min={1} placeholder="天数" style={{ width: 100 }} />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'quantity']}
                      rules={[{ required: true, message: '数量必填' }]}
                      className={styles.formItem}
                    >
                      <InputNumber addonAfter="盒/瓶" min={1} placeholder="数量" style={{ width: 120 }} />
                    </Form.Item>
                    {fields.length > 1 && (
                      <MinusCircleOutlined onClick={() => remove(field.name)} />
                    )}
                  </Space>
                  <Space className={styles.itemLine} align="baseline" wrap>
                    <Form.Item
                      name={[field.name, 'dosage']}
                      rules={[{ required: true, message: '用量必填' }]}
                      className={styles.formItem}
                    >
                      <Input placeholder="用量（如 1片 / 5ml）" style={{ width: 160 }} />
                    </Form.Item>
                    <Form.Item
                      name={[field.name, 'frequency']}
                      rules={[{ required: true, message: '频次必填' }]}
                      className={styles.formItem}
                    >
                      <Input placeholder="频次（如 每日3次 / QD）" style={{ width: 200 }} />
                    </Form.Item>
                  </Space>
                  {/* 单药规则实时预警（过敏/禁忌 ERROR 红 / 高危 AUDIT 橙），由父级按行注入 */}
                  {!disabled &&
                    renderRowWarnings &&
                    (() => {
                      const node = renderRowWarnings(field.name);
                      return node ? <div className={styles.rowWarnings}>{node}</div> : null;
                    })()}
                </div>
              ))}
              <Button
                type="dashed"
                block
                icon={<PlusOutlined />}
                onClick={() => add()}
                disabled={disabled}
              >
                添加药品
              </Button>
            </>
          )}
        </Form.List>
      </Form>
    </>
  );
}

/**
 * 接诊台开处方弹窗。
 *
 * - 顶部「处方模板」下拉：按医生所属科室（deptId 为空返回全院通用+全部）拉取模板，
 *   选中后把模板药品明细带入 Form.List，医生可增删改后再提交（复用手工开方流程与风险拦截）；
 * - initialItems：支持「驳回重开」把被驳回处方明细预填进表单；
 * - Form.List 多行药品明细：药品搜索下拉（getDrugs）+ 用法/天数/数量/用量/频次；
 * - 提交后展示后端风险拦截结果（WARNING 提示 / AUDIT 待审核 / 无风险直接通过）。
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Spin,
  Tag,
  message,
} from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { getDrugs, getTemplates, precheckPrescription } from '@/services/admin';
import { getErrorMessage } from '@/utils/error';
import { DEBOUNCE_PRECheck_MS } from '@/constants/timing';
import styles from './PrescriptionFormModal.module.less';
import { PAGE_SIZE_50 } from '@/constants/pageSize';

/** 单行药品明细表单值（对齐 PrescriptionSubmitRequest.ItemDTO） */
interface PrescriptionItemFormValue {
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

interface Props {
  open: boolean;
  submitting: boolean;
  /** 打开时预填的药品明细（驳回重开场景），空数组表示不预填 */
  initialItems?: PrescriptionPrefillItem[];
  /** 医生所属科室 ID（模板列表过滤；为空时返回全院通用+全部模板） */
  doctorDeptId?: number | null;
  /** 当前接诊的问诊记录 ID（实时风险预检用；为空时不触发预检） */
  consultId?: number | null;
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
  initialItems,
  doctorDeptId,
  consultId,
  onCancel,
  onSubmit,
}: Props) {
  const [form] = Form.useForm();
  /** 药品搜索结果缓存（避免每次展开下拉重复请求） */
  const [drugOptions, setDrugOptions] = useState<{ label: string; value: number }[]>([]);
  const drugSearchLoadingRef = useRef(false);
  /** 提交成功后的风险拦截结果（展示后再关闭） */
  const [result, setResult] = useState<API.PrescriptionSubmitResult | null>(null);

  /** 实时风险预检结果：单药规则（过敏/禁忌/高危）按 drugId 归属到药品行 */
  const [warningsByDrug, setWarningsByDrug] = useState<Record<number, API.RiskWarning[]>>({});
  /** 实时风险预检结果：跨药品规则（重复用药）置顶汇总 */
  const [summaryWarnings, setSummaryWarnings] = useState<API.RiskWarning[]>([]);
  const precheckTimerRef = useRef<number | undefined>(undefined);
  /** 预检请求序号：仅采纳最后一次结果，避免慢响应覆盖新明细的预警 */
  const precheckSeqRef = useRef(0);

  /** 处方模板列表（打开弹窗时按科室拉取一次） */
  const [templates, setTemplates] = useState<API.PrescriptionTemplate[]>([]);
  const [templateLoading, setTemplateLoading] = useState(false);

  /** 实时监听药品明细，变化后防抖触发预检 */
  const itemsWatch = Form.useWatch('items', form);

  /**
   * 实时风险预检：按当前已选药明细批量请求，返回预警按 drugId 归属 / 跨药汇总。
   * 预检失败静默降级（不阻塞手工开方），只保留最后一次请求结果。
   */
  const runPrecheck = useCallback(
    async (items: PrescriptionItemFormValue[]) => {
      if (!consultId) {
        setWarningsByDrug({});
        setSummaryWarnings([]);
        return;
      }
      const validItems = (items ?? [])
        .filter((it) => it.drugId !== undefined && it.drugId !== null)
        .map((it) => ({
          drugId: it.drugId as number,
          dosage: (it.dosage ?? '').trim(),
          frequency: (it.frequency ?? '').trim(),
          usageMethod: (it.usageMethod ?? '').trim(),
          days: it.days as number,
          quantity: it.quantity as number,
        }));
      if (validItems.length === 0) {
        setWarningsByDrug({});
        setSummaryWarnings([]);
        return;
      }
      const seq = ++precheckSeqRef.current;
      try {
        const res = await precheckPrescription({ consultId, items: validItems });
        if (seq !== precheckSeqRef.current) return;
        const byDrug: Record<number, API.RiskWarning[]> = {};
        const summary: API.RiskWarning[] = [];
        for (const w of res.warnings) {
          if (w.drugId !== undefined && w.drugId !== null) {
            (byDrug[w.drugId] ??= []).push(w);
          } else {
            summary.push(w);
          }
        }
        setWarningsByDrug(byDrug);
        setSummaryWarnings(summary);
      } catch {
        // 预检失败静默降级：保留无预警态，不打断录入
        if (seq === precheckSeqRef.current) {
          setWarningsByDrug({});
          setSummaryWarnings([]);
        }
      }
    },
    [consultId],
  );

  /** 将模板/驳回处方的药品明细填入表单（合并药品选项，保证 Select 正常回显药名） */
  const applyItemsToForm = (items: PrescriptionPrefillItem[]) => {
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
  };

  /** 拉取处方模板（deptId 过滤：本部门 + 全院通用） */
  const loadTemplates = async () => {
    setTemplateLoading(true);
    try {
      const res = await getTemplates({
        page: 1,
        size: PAGE_SIZE_50,
        deptId: doctorDeptId ?? undefined,
      });
      setTemplates(res.list ?? []);
    } catch {
      // 模板加载失败不阻塞手工开方，下拉为空
    } finally {
      setTemplateLoading(false);
    }
  };

  /** 打开弹窗时重置表单与结果；有预填明细（驳回重开）则带入 */
  useEffect(() => {
    if (open) {
      form.resetFields();
      setResult(null);
      // 清空上次开方的实时预检结果，避免残留旧预警
      setWarningsByDrug({});
      setSummaryWarnings([]);
      // 预拉一页启用药品供下拉首屏使用
      if (drugOptions.length === 0) {
        getDrugs({ page: 1, size: PAGE_SIZE_50, status: 'ENABLED' })
          .then((res) => setDrugOptions(toDrugOptions(res.list ?? [])))
          .catch(() => {});
      }
      loadTemplates();
      if (initialItems && initialItems.length > 0) {
        applyItemsToForm(initialItems);
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  /** 明细变化后防抖触发预检（避免每次输入/选择都请求），弹窗关闭时取消未发请求 */
  useEffect(() => {
    if (!open) return;
    if (precheckTimerRef.current) window.clearTimeout(precheckTimerRef.current);
    precheckTimerRef.current = window.setTimeout(() => {
      runPrecheck(itemsWatch ?? []);
    }, DEBOUNCE_PRECheck_MS);
    return () => {
      if (precheckTimerRef.current) window.clearTimeout(precheckTimerRef.current);
    };
  }, [itemsWatch, open, runPrecheck]);

  /** 药品远程搜索（按名称模糊；静默失败保留已有选项） */
  const handleDrugSearch = async (keyword: string) => {
    if (drugSearchLoadingRef.current) return;
    drugSearchLoadingRef.current = true;
    try {
      const res = await getDrugs({
        page: 1,
        size: PAGE_SIZE_50,
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

  /** 选择模板：带入明细到表单（可编辑），下拉随即复位供再次选择 */
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
        {/* 处方模板带入入口（可编辑后提交） */}
        <div className={styles.templateArea}>
          <Space>
            <Select
              allowClear
              showSearch
              placeholder="从处方模板带入明细（可修改）"
              style={{ width: 340 }}
              loading={templateLoading}
              disabled={Boolean(result)}
              options={templateOptions}
              optionFilterProp="label"
              onChange={handleTemplateSelect}
              notFoundContent={templateLoading ? <Spin size="small" /> : '暂无可用模板'}
            />
          </Space>
        </div>

        {/* 跨药品风险（重复用药）：涉及两行，置顶汇总 */}
        {!result && summaryWarnings.length > 0 && (
          <Alert
            className={styles.summaryAlert}
            type="warning"
            showIcon
            message="重复用药提示"
            description={
              <ul className={styles.summaryList}>
                {summaryWarnings.map((w, i) => (
                  <li key={i} className={styles.summaryItem}>
                    {w.message}
                  </li>
                ))}
              </ul>
            }
          />
        )}

        <Form form={form} layout="vertical" disabled={Boolean(result)}>
          <Form.List name="items">
            {(fields, { add, remove }) => (
              <>
                {fields.map((field) => {
                  // 当前行的药品 ID：用于把预检命中（按 drugId 归属）渲染到对应药品行下
                  const rowDrugId = (itemsWatch?.[field.name] as
                    | PrescriptionItemFormValue
                    | undefined)?.drugId;
                  const rowWarnings =
                    rowDrugId !== undefined && rowDrugId !== null
                      ? warningsByDrug[rowDrugId]
                      : undefined;
                  return (
                    <div key={field.key} className={styles.itemRow}>
                      <Space
                        className={styles.itemLine}
                        align="baseline"
                        wrap
                      >
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
                          <Select
                            placeholder="用法"
                            style={{ width: 90 }}
                            options={USAGE_METHODS}
                          />
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
                      {/* 单药规则实时预警：过敏/禁忌（ERROR 红）/ 高危药品（AUDIT 橙），附命中来源 */}
                      {!result && rowWarnings && rowWarnings.length > 0 && (
                        <div className={styles.rowWarnings}>
                          {rowWarnings.map((w, wi) => (
                            <Alert
                              key={wi}
                              type={
                                w.level === 'ERROR'
                                  ? 'error'
                                  : w.level === 'AUDIT'
                                    ? 'warning'
                                    : 'info'
                              }
                              showIcon
                              className={styles.rowWarning}
                              message={
                                <span className={styles.warningMessage}>
                                  {w.message}
                                  {w.source ? (
                                    <span className={styles.warningSource}>
                                      （{w.source}）
                                    </span>
                                  ) : null}
                                </span>
                              }
                            />
                          ))}
                        </div>
                      )}
                    </div>
                  );
                })}
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
            className={styles.resultAlert}
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
                      <div key={i} className={styles.resultItem}>
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

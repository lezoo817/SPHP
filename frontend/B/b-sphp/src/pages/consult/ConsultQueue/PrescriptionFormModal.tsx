/**
 * 接诊台开处方弹窗。
 *
 * - 顶部「处方模板」下拉：按医生所属科室（deptId 为空返回全院通用+全部）拉取模板，
 *   选中后把模板药品明细带入 Form.List，医生可增删改后再提交（复用手工开方流程与风险拦截）；
 * - initialItems：支持「驳回重开」把被驳回处方明细预填进表单；
 * - 药品明细编辑区复用公共组件 PrescriptionItemsForm（两行式行布局 + 用法下拉 + 实时预检）；
 * - 提交后展示后端风险拦截结果（WARNING 提示 / AUDIT 待审核 / 无风险直接通过）。
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Form, Modal, Space, Spin, Tag, message } from 'antd';
import { getDrugs, getTemplates, precheckPrescription } from '@/services/admin';
import { getErrorMessage } from '@/utils/error';
import { DEBOUNCE_PRECheck_MS } from '@/constants/timing';
import PrescriptionItemsForm, {
  type PrescriptionItemFormValue,
  type PrescriptionPrefillItem,
} from '@/components/prescription/PrescriptionItemsForm';
import styles from './PrescriptionFormModal.module.less';
import { PAGE_SIZE_50 } from '@/constants/pageSize';
import { STATUS_APPROVED, STATUS_SUBMITTED } from '@/constants/businessStatus';

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
  /** 提交成功后的风险拦截结果（展示后再关闭） */
  const [result, setResult] = useState<API.PrescriptionSubmitResult | null>(null);

  /** 实时风险预检结果：单药规则（过敏/禁忌/高危）按 drugId 归属到药品行 */
  const [warningsByDrug, setWarningsByDrug] = useState<Record<number, API.RiskWarning[]>>({});
  /** 实时风险预检结果：跨药品规则（重复用药）置顶汇总 */
  const [summaryWarnings, setSummaryWarnings] = useState<API.RiskWarning[]>([]);
  /** 当前药品明细（公共组件上报），驱动防抖预检与按行归位预警 */
  const [itemsWatch, setItemsWatch] = useState<PrescriptionItemFormValue[]>([]);
  const precheckTimerRef = useRef<number | undefined>(undefined);
  /** 预检请求序号：仅采纳最后一次结果，避免慢响应覆盖新明细的预警 */
  const precheckSeqRef = useRef(0);

  /** 处方模板列表（打开弹窗时按科室拉取一次） */
  const [templates, setTemplates] = useState<API.PrescriptionTemplate[]>([]);
  const [templateLoading, setTemplateLoading] = useState(false);

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

  /** 公共组件上报明细变化：写入本地状态，驱动下方防抖预检 */
  const handleItemsChange = useCallback((next: PrescriptionItemFormValue[]) => {
    setItemsWatch(next);
  }, []);

  /** 单药规则预警渲染：把预检命中（按 drugId 归属）渲染到对应药品行下 */
  const renderRowWarnings = useCallback(
    (fieldName: number) => {
      const row = itemsWatch?.[fieldName] as PrescriptionItemFormValue | undefined;
      const drugId = row?.drugId;
      const warnings =
        drugId !== undefined && drugId !== null ? warningsByDrug[drugId] : undefined;
      if (!warnings || warnings.length === 0) return null;
      return (
        <>
          {warnings.map((w, wi) => (
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
                    <span className={styles.warningSource}>（{w.source}）</span>
                  ) : null}
                </span>
              }
            />
          ))}
        </>
      );
    },
    [itemsWatch, warningsByDrug],
  );

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

  /** 打开弹窗时清空结果与实时预检（表单重置与预填由公共组件 open 驱动，保证先清空再填充） */
  useEffect(() => {
    if (open) {
      setResult(null);
      // 清空上次开方的实时预检结果，避免残留旧预警
      setWarningsByDrug({});
      setSummaryWarnings([]);
      loadTemplates();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  /** 明细变化后防抖触发预检（避免每次输入/选择都请求），弹窗关闭时取消未发请求 */
  useEffect(() => {
    if (!open) return;
    if (precheckTimerRef.current) window.clearTimeout(precheckTimerRef.current);
    precheckTimerRef.current = window.setTimeout(() => {
      void runPrecheck(itemsWatch ?? []);
    }, DEBOUNCE_PRECheck_MS);
    return () => {
      if (precheckTimerRef.current) window.clearTimeout(precheckTimerRef.current);
    };
  }, [itemsWatch, open, runPrecheck]);

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
        <PrescriptionItemsForm
          form={form}
          open={open}
          disabled={Boolean(result)}
          fetchDrugs={getDrugs}
          templates={templates}
          templateLoading={templateLoading}
          initialItems={initialItems}
          onItemsChange={handleItemsChange}
          renderRowWarnings={renderRowWarnings}
          topExtra={
            !result && summaryWarnings.length > 0 ? (
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
            ) : undefined
          }
        />

        {result && (
          <Alert
            className={styles.resultAlert}
            type={result.riskWarnings.some((w) => w.level === 'ERROR') ? 'error' : 'success'}
            showIcon
            message={
              <Space direction="vertical" size={2}>
                <span>
                  处方已提交 ·{' '}
                  <Tag color={result.status === STATUS_APPROVED ? 'green' : 'orange'}>
                    {result.status === STATUS_APPROVED
                      ? '已生效'
                      : result.status === STATUS_SUBMITTED
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

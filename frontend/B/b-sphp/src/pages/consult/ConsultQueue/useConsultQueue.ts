/**
 * 接诊台核心数据与操作逻辑 Hook。
 *
 * 左栏三块队列（待接诊 / 接诊中 / 接诊历史）各自独立分页查询：
 * - PENDING / IN_PROGRESS 走 getQueue({ status, page, size })（15s 轮询）
 * - HISTORY 走 getConsultHistory({ page, size })
 * 患者详情 / 历史 / 处方均经 React Query 拉取；
 * 开始/结束接诊、保存病历、提交处方为写操作，成功后由查询键自动刷新或本地更新缓存。
 */
import { useCallback, useEffect, useState } from 'react';
import { Modal, message } from 'antd';
import { useModel } from '@umijs/max';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getQueue,
  getPatientDetail,
  addPatientAllergy,
  startConsult,
  endConsult,
  saveNote,
  getConsultHistory,
  getConsultHistoryDetail,
  getPrescriptions,
  getPrescriptionDetail,
  submitPrescription,
} from '@/services/admin';
import { getErrorMessage } from '@/utils/error';
import { useCurrentUser, useHasRole } from '@/hooks/useCurrentUser';
import { QUERY_KEYS, STALE_TIME } from '@/constants/queryKeys';
import { POLL_INTERVAL_CONSULT } from '@/constants/timing';
import dayjs from 'dayjs';
import type { SelectedStatus } from './constants';
import type { NoteField } from './NoteForm';
import type { PrescriptionPrefillItem } from '@/components/prescription/PrescriptionItemsForm';
import { PAGE_SIZE_20 } from '@/constants/pageSize';
import { ROLE_ADMIN, STATUS_COMPLETED, STATUS_IN_PROGRESS, STATUS_PENDING } from '@/constants/businessStatus';

/** 待接诊 / 接诊中队列每页条数 */
const QUEUE_PAGE_SIZE = 10;
/** 接诊历史每页条数 */
const HISTORY_PAGE_SIZE = 10;
/** 稳定的空数组引用：避免 `?? []` 每次 render 新建引用，破坏子组件 useMemo/useEffect 依赖稳定性 */
const EMPTY_ARRAY: never[] = [];

export function useConsultQueue() {
  const currentUser = useCurrentUser();
  const isAdmin = useHasRole(ROLE_ADMIN);
  const queryClient = useQueryClient();
  // 全局接诊上下文：选中患者时写入 patient_id，供 MainLayout 悬浮 AI 抽屉
  // 构建对话上下文携带，避免 AI 反问"患者是谁"（后端 5 个 B 端工具必填 patient_id）
  const { setCurrentConsult, clear: clearConsultContext } = useModel('consultContext');

  // ==================== 待接诊队列（15s 轮询，独立分页） ====================

  const [pendingPage, setPendingPage] = useState(1);
  const { data: pendingRes, isLoading: pendingLoading } = useQuery({
    queryKey: [...QUERY_KEYS.consultQueue(STATUS_PENDING), pendingPage] as const,
    queryFn: () =>
      getQueue({ status: STATUS_PENDING, page: pendingPage, size: QUEUE_PAGE_SIZE }),
    refetchInterval: POLL_INTERVAL_CONSULT,
  });
  const pendingItems = pendingRes?.list ?? EMPTY_ARRAY;
  const pendingTotal = pendingRes?.total ?? 0;

  // ==================== 接诊中队列（15s 轮询，独立分页） ====================

  const [inProgressPage, setInProgressPage] = useState(1);
  const { data: inProgressRes, isLoading: inProgressLoading } = useQuery({
    queryKey: [...QUERY_KEYS.consultQueue(STATUS_IN_PROGRESS), inProgressPage] as const,
    queryFn: () =>
      getQueue({ status: STATUS_IN_PROGRESS, page: inProgressPage, size: QUEUE_PAGE_SIZE }),
    refetchInterval: POLL_INTERVAL_CONSULT,
  });
  const inProgressItems = inProgressRes?.list ?? EMPTY_ARRAY;
  const inProgressTotal = inProgressRes?.total ?? 0;

  // ==================== 接诊历史（独立分页） ====================

  const [historyPage, setHistoryPage] = useState(1);
  const { data: historyRes, isLoading: historyLoading } = useQuery({
    queryKey: [...QUERY_KEYS.consultHistory, historyPage] as const,
    queryFn: () =>
      getConsultHistory({ page: historyPage, size: HISTORY_PAGE_SIZE }),
  });
  const historyItems = historyRes?.list ?? EMPTY_ARRAY;
  const historyTotal = historyRes?.total ?? 0;

  // ==================== 选中接诊 ====================

  const [selectedConsultId, setSelectedConsultId] = useState<number | null>(null);
  const [selectedStatus, setSelectedStatus] = useState<SelectedStatus | null>(null);

  /** 患者详情（缓存 60s；无效 ID 时不发请求） */
  const { data: patientDetail, isLoading: detailLoading } = useQuery({
    queryKey: QUERY_KEYS.patientDetail(selectedConsultId ?? -1),
    queryFn: () => getPatientDetail(selectedConsultId as number),
    enabled: Boolean(selectedConsultId),
    staleTime: STALE_TIME.patientDetail,
  });

  /** 历史接诊详情（仅选中已完成历史时加载，用于右栏历史详情面板） */
  const { data: historyDetail, isLoading: historyDetailLoading } = useQuery({
    queryKey: QUERY_KEYS.consultHistoryDetail(selectedConsultId ?? -1),
    queryFn: () => getConsultHistoryDetail(selectedConsultId as number),
    enabled: Boolean(selectedConsultId) && selectedStatus === STATUS_COMPLETED,
  });

  /** 当前问诊的处方列表 */
  const { data: consultPrescriptions } = useQuery({
    queryKey: QUERY_KEYS.consultPrescriptions(selectedConsultId ?? -1),
    queryFn: () =>
      getPrescriptions({ consultId: selectedConsultId as number, page: 1, size: PAGE_SIZE_20 }).then(
        (res) => res.list ?? [],
      ),
    enabled: Boolean(selectedConsultId),
  });

  // ==================== 病历表单 ====================

  const [noteChanged, setNoteChanged] = useState(false);
  const [reportChiefComplaint, setReportChiefComplaint] = useState('');
  const [reportPresentIllness, setReportPresentIllness] = useState('');
  const [reportPhysicalExam, setReportPhysicalExam] = useState('');
  const [reportDiagnosis, setReportDiagnosis] = useState('');
  const [reportTreatmentPlan, setReportTreatmentPlan] = useState('');
  const [reportGeneratedAt, setReportGeneratedAt] = useState<string>('');
  const [savingNote, setSavingNote] = useState(false);

  /** 患者变化时重置病历表单；有已保存的纯文本病历则按"字段名：值"格式回显 */
  useEffect(() => {
    setNoteChanged(false);
    setReportChiefComplaint('');
    setReportPresentIllness('');
    setReportPhysicalExam('');
    setReportDiagnosis('');
    setReportTreatmentPlan('');
    setReportGeneratedAt('');
    if (patientDetail?.doctorNote) {
      // 按"字段名：值"格式逐行解析（与 saveNote 输出对齐）
      const fieldMap: Record<string, NoteField> = {
        主诉: 'chiefComplaint',
        现病史: 'presentIllness',
        查体: 'physicalExam',
        诊断: 'diagnosis',
        治疗方案: 'treatmentPlan',
      };
      const parsed: Partial<Record<NoteField, string>> = {};
      patientDetail.doctorNote.split('\n').forEach((line) => {
        for (const [label, field] of Object.entries(fieldMap)) {
          const prefix = `${label}：`;
          if (line.startsWith(prefix)) {
            parsed[field] = line.substring(prefix.length);
            break;
          }
        }
      });
      if (parsed.chiefComplaint) setReportChiefComplaint(parsed.chiefComplaint);
      if (parsed.presentIllness) setReportPresentIllness(parsed.presentIllness);
      if (parsed.physicalExam) setReportPhysicalExam(parsed.physicalExam);
      if (parsed.diagnosis) setReportDiagnosis(parsed.diagnosis);
      if (parsed.treatmentPlan) setReportTreatmentPlan(parsed.treatmentPlan);
    }
  }, [patientDetail]);

  // ==================== 补录过敏史 ====================

  /** 接诊台补录患者过敏史：成功后失效患者详情缓存，过敏标签与拦截数据即时生效 */
  const handleAddAllergy = useCallback(async (data: API.AllergyCreateReq) => {
    if (!selectedConsultId) return;
    await addPatientAllergy(selectedConsultId, data);
    await queryClient.invalidateQueries({
      queryKey: QUERY_KEYS.patientDetail(selectedConsultId),
    });
  }, [selectedConsultId, queryClient]);

  /** 病历字段变更：标记未保存 + 写入对应字段 */
  const handleFieldChange = useCallback((field: NoteField, value: string) => {
    setNoteChanged(true);
    switch (field) {
      case 'chiefComplaint':
        setReportChiefComplaint(value);
        break;
      case 'presentIllness':
        setReportPresentIllness(value);
        break;
      case 'physicalExam':
        setReportPhysicalExam(value);
        break;
      case 'diagnosis':
        setReportDiagnosis(value);
        break;
      case 'treatmentPlan':
        setReportTreatmentPlan(value);
        break;
    }
  }, []);

  // 离开接诊页时清除全局接诊上下文：避免医生跳转他处后悬浮 AI 仍持有旧患者 ID
  useEffect(() => () => clearConsultContext(), [clearConsultContext]);

  // ==================== 接诊操作 ====================

  const [startingConsult, setStartingConsult] = useState(false);
  const [endingConsult, setEndingConsult] = useState(false);

  // ==================== 选中患者 ====================

  /** 选择待接诊/接诊中患者 */
  const handleSelectItem = useCallback((item: API.QueueItem) => {
    setSelectedConsultId(item.consultId);
    setSelectedStatus(item.status);
    // 桥接当前接诊患者给全局 AI 助手上下文（MainLayout 悬浮抽屉消费）
    setCurrentConsult(item.patientId, item.consultId);
  }, [setCurrentConsult]);

  /** 选择历史接诊记录 */
  const handleSelectHistoryItem = useCallback((item: API.ConsultHistoryItem) => {
    setSelectedConsultId(item.consultId);
    setSelectedStatus(item.status as SelectedStatus);
    // 桥接当前接诊患者给全局 AI 助手上下文（MainLayout 悬浮抽屉消费）
    setCurrentConsult(item.patientId, item.consultId);
  }, [setCurrentConsult]);

  // ==================== 开始/结束接诊 ====================

  /** 刷新三块队列（开始/结束接诊后患者状态迁移，列表需同步） */
  const refreshQueues = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: QUERY_KEYS.consultQueue(STATUS_PENDING) });
    queryClient.invalidateQueries({ queryKey: QUERY_KEYS.consultQueue(STATUS_IN_PROGRESS) });
    queryClient.invalidateQueries({ queryKey: QUERY_KEYS.consultHistory });
  }, [queryClient]);

  /** 开始接诊：号源时段校验 + 状态迁移 PENDING→IN_PROGRESS + 清理 AI 上下文（医生从接诊中队列重选时再写入） */
  const handleStartConsult = useCallback(async () => {
    if (!selectedConsultId) return;

    // 前端时段校验：从待接诊队列中找到当前患者，检查当前时间是否在号源时段内
    const selectedItem = pendingItems.find((i) => i.consultId === selectedConsultId);
    if (selectedItem?.slotStartTime && selectedItem?.slotEndTime) {
      const now = dayjs();
      const start = dayjs(selectedItem.slotStartTime, 'HH:mm');
      const end = dayjs(selectedItem.slotEndTime, 'HH:mm');
      const currentTime = dayjs(`${now.format('HH:mm')}`, 'HH:mm');
      if (currentTime.isBefore(start) || currentTime.isAfter(end)) {
        await message.warning(
            `当前不在接诊时间内（${selectedItem.slotStartTime}~${selectedItem.slotEndTime}）`,
        );
        return;
      }
    }

    setStartingConsult(true);
    try {
      await startConsult(selectedConsultId);
      await message.success('开始接诊');
      setSelectedStatus(STATUS_IN_PROGRESS);
      setSelectedConsultId(null);
      refreshQueues();
      // 开始接诊后重置选中，AI 上下文随之清除；医生从接诊中队列重新选中时再写入
      clearConsultContext();
    } catch (err: unknown) {
      await message.error(getErrorMessage(err, '开始接诊失败'));
    } finally {
      setStartingConsult(false);
    }
  }, [selectedConsultId, pendingItems, refreshQueues, clearConsultContext]);

  /** 结束接诊：Modal 二次确认后迁移 IN_PROGRESS→COMPLETED 并清理 AI 上下文 */
  const handleEndConsult = useCallback(() => {
    if (!selectedConsultId) return;
    Modal.confirm({
      title: '结束问诊',
      content: '确定结束当前问诊吗？结束前请确认已保存病历并无未签名的处方草稿。',
      okText: '确认结束',
      okButtonProps: { danger: true },
      onOk: async () => {
        setEndingConsult(true);
        try {
          await endConsult(selectedConsultId);
          await message.success('问诊已结束');
          setSelectedStatus(STATUS_COMPLETED);
          setSelectedConsultId(null);
          refreshQueues();
          // 结束问诊清除当前接诊上下文，AI 助手不再关联已结束的患者
          clearConsultContext();
        } catch (err: unknown) {
          await message.error(getErrorMessage(err, '结束问诊失败'));
        } finally {
          setEndingConsult(false);
        }
      },
    });
  }, [selectedConsultId, refreshQueues, clearConsultContext]);

  // ==================== 病历保存 ====================

  /** 保存病历：把 5 个字段按"字段名：值"换行拼成纯文本（与 patientDetail 回显解析对齐） */
  const handleSaveNote = useCallback(async () => {
    if (!selectedConsultId) return;
    if (!reportChiefComplaint.trim() && !reportDiagnosis.trim()) {
      await message.warning('请至少填写主诉或诊断');
      return;
    }
    // 拼接为纯文本（按"字段名：值"换行分隔），不再使用 JSON 格式
    const lines: string[] = [];
    if (reportChiefComplaint.trim()) lines.push(`主诉：${reportChiefComplaint.trim()}`);
    if (reportPresentIllness.trim()) lines.push(`现病史：${reportPresentIllness.trim()}`);
    if (reportPhysicalExam.trim()) lines.push(`查体：${reportPhysicalExam.trim()}`);
    if (reportDiagnosis.trim()) lines.push(`诊断：${reportDiagnosis.trim()}`);
    if (reportTreatmentPlan.trim()) lines.push(`治疗方案：${reportTreatmentPlan.trim()}`);
    const noteText = lines.join('\n');
    setSavingNote(true);
    try {
      await saveNote(selectedConsultId, { doctorNote: noteText });
      await message.success('病历已保存');
      setNoteChanged(false);
      setReportGeneratedAt(dayjs().format('YYYY-MM-DD HH:mm'));
    } catch (err: unknown) {
      await message.error(getErrorMessage(err, '保存病历失败'));
    } finally {
      setSavingNote(false);
    }
  }, [
    selectedConsultId,
    reportChiefComplaint,
    reportPresentIllness,
    reportPhysicalExam,
    reportDiagnosis,
    reportTreatmentPlan,
  ]);

  // ==================== 开处方 ====================

  const [prescriptionModalOpen, setPrescriptionModalOpen] = useState(false);
  const [submittingPrescription, setSubmittingPrescription] = useState(false);
  /** 驳回重开时预填进开方弹窗的明细（无预填为 null） */
  const [prescriptionPrefill, setPrescriptionPrefill] = useState<PrescriptionPrefillItem[] | null>(null);

  // ==================== 处方详情 / 驳回重开 ====================

  const [prescriptionDetailOpen, setPrescriptionDetailOpen] = useState(false);
  const [prescriptionDetailLoading, setPrescriptionDetailLoading] = useState(false);
  const [prescriptionDetailData, setPrescriptionDetailData] =
    useState<API.PrescriptionDetail | null>(null);

  /** 查看处方详情（含风险快照 / 驳回原因） */
  const handleViewPrescription = useCallback(async (id: number) => {
    setPrescriptionDetailLoading(true);
    setPrescriptionDetailOpen(true);
    setPrescriptionDetailData(null);
    try {
      const data = await getPrescriptionDetail(id);
      setPrescriptionDetailData(data);
    } catch (err: unknown) {
      await message.error(getErrorMessage(err, '加载处方详情失败'));
      setPrescriptionDetailOpen(false);
    } finally {
      setPrescriptionDetailLoading(false);
    }
  }, []);

  /** 关闭处方详情弹窗 */
  const handleClosePrescriptionDetail = useCallback(() => {
    setPrescriptionDetailOpen(false);
    setPrescriptionDetailData(null);
  }, []);

  /** 驳回重开：取被驳回处方明细预填进开方弹窗，医生修改后重新提交 */
  const handleReopenPrescription = useCallback(async (prescriptionId: number) => {
    try {
      const detail = await getPrescriptionDetail(prescriptionId);
      const items: PrescriptionPrefillItem[] = detail.items.map((it) => ({
        drugId: it.drugId,
        drugName: it.drugName,
        dosage: it.dosage,
        frequency: it.frequency,
        usageMethod: it.usageMethod,
        days: it.days,
        quantity: it.quantity,
      }));
      setPrescriptionPrefill(items);
      setPrescriptionModalOpen(true);
    } catch (err: unknown) {
      await message.error(getErrorMessage(err, '加载处方失败，无法重新开方'));
    }
  }, []);

  /** 关闭开方弹窗（同时清除驳回重开预填，避免下次开方残留旧明细） */
  const closePrescriptionModal = useCallback(() => {
    setPrescriptionPrefill(null);
    setPrescriptionModalOpen(false);
  }, []);

  /** 提交处方：成功后在弹窗内展示风险拦截结果，并刷新已开处方列表 */
  const handleSubmitPrescription = useCallback(
    async (
      items: API.PrescriptionSubmitReq['items'],
    ): Promise<API.PrescriptionSubmitResult> => {
      if (!selectedConsultId) {
        throw new Error('请先选择患者');
      }
      setSubmittingPrescription(true);
      try {
        const result = await submitPrescription({ consultId: selectedConsultId, items });
        await queryClient.invalidateQueries({
          queryKey: QUERY_KEYS.consultPrescriptions(selectedConsultId),
        });
        return result;
      } finally {
        setSubmittingPrescription(false);
      }
    },
    [selectedConsultId, queryClient],
  );

  return {
    currentUser,
    isAdmin,
    // 待接诊队列
    pendingItems,
    pendingLoading,
    pendingTotal,
    pendingPage,
    setPendingPage,
    // 接诊中队列
    inProgressItems,
    inProgressLoading,
    inProgressTotal,
    inProgressPage,
    setInProgressPage,
    // 接诊历史
    historyItems,
    historyLoading,
    historyTotal,
    historyPage,
    setHistoryPage,
    // 选中
    selectedConsultId,
    selectedStatus,
    handleSelectItem,
    handleSelectHistoryItem,
    // 患者详情
    patientDetail,
    detailLoading,
    historyDetail,
    historyDetailLoading,
    handleAddAllergy,
    // 接诊操作
    startingConsult,
    endingConsult,
    handleStartConsult,
    handleEndConsult,
    // 病历表单
    noteChanged,
    savingNote,
    reportChiefComplaint,
    reportPresentIllness,
    reportPhysicalExam,
    reportDiagnosis,
    reportTreatmentPlan,
    reportGeneratedAt,
    handleFieldChange,
    handleSaveNote,
    // 处方
    consultPrescriptions: consultPrescriptions ?? EMPTY_ARRAY,
    /** 医生所属科室（模板列表过滤用） */
    doctorDeptId: currentUser?.deptId ?? null,
    prescriptionModalOpen,
    closePrescriptionModal,
    setPrescriptionModalOpen,
    submittingPrescription,
    handleSubmitPrescription,
    // 处方详情 / 驳回重开
    prescriptionPrefill,
    prescriptionDetailOpen,
    prescriptionDetailLoading,
    prescriptionDetailData,
    handleViewPrescription,
    handleClosePrescriptionDetail,
    handleReopenPrescription,
  };
}

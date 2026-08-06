/**
 * 接诊台核心数据与操作逻辑 Hook。
 *
 * 队列 / 患者详情 / 历史 / 消息 / 处方均经 React Query 拉取（队列 15s 轮询）；
 * 开始/结束接诊、保存病历、发送消息为写操作，成功后由查询键自动刷新或本地更新缓存。
 */
import { useEffect, useRef, useState } from 'react';
import type { KeyboardEvent } from 'react';
import { Modal, message } from 'antd';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getQueue,
  getPatientDetail,
  startConsult,
  endConsult,
  saveNote,
  getMessages,
  sendMessage,
  getConsultHistory,
  getConsultHistoryDetail,
  getPrescriptions,
} from '@/services/admin';
import { getErrorMessage } from '@/utils/error';
import { useCurrentUser, useHasRole } from '@/hooks/useCurrentUser';
import { QUERY_KEYS, STALE_TIME } from '@/constants/queryKeys';
import dayjs from 'dayjs';
import type { QueueTab, SelectedStatus } from './constants';
import type { NoteField } from './NoteForm';

export function useConsultQueue() {
  const currentUser = useCurrentUser();
  const isAdmin = useHasRole('ADMIN');
  const queryClient = useQueryClient();

  // ==================== 队列（15s 轮询，按 Tab 区分） ====================

  const [queueTab, setQueueTab] = useState<QueueTab>('PENDING');
  const { data: queueRes, isLoading: queueLoading } = useQuery({
    queryKey: QUERY_KEYS.consultQueue(queueTab),
    queryFn: () => getQueue({ status: queueTab, page: 1, size: 20 }),
    enabled: queueTab !== 'HISTORY',
    refetchInterval: 15_000,
  });
  const queueItems = queueRes?.list ?? [];
  const queueTotal = queueRes?.total ?? 0;

  // ==================== 接诊历史 ====================

  const { data: historyRes, isLoading: historyLoading } = useQuery({
    queryKey: QUERY_KEYS.consultHistory,
    queryFn: () => getConsultHistory({ page: 1, size: 10 }),
    enabled: queueTab === 'HISTORY',
  });
  const historyItems = historyRes?.list ?? [];

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

  /** 历史接诊详情（仅历史 Tab 加载） */
  const { data: historyDetail, isLoading: historyDetailLoading } = useQuery({
    queryKey: QUERY_KEYS.consultHistoryDetail(selectedConsultId ?? -1),
    queryFn: () => getConsultHistoryDetail(selectedConsultId as number),
    enabled: Boolean(selectedConsultId) && queueTab === 'HISTORY',
  });

  /** 留言板消息（仅接诊中加载） */
  const { data: messages, isLoading: messagesLoading } = useQuery({
    queryKey: QUERY_KEYS.consultMessages(selectedConsultId ?? -1),
    queryFn: () =>
      getMessages(selectedConsultId as number, { page: 1, size: 100 }).then(
        (res) => res.list ?? [],
      ),
    enabled: Boolean(selectedConsultId) && selectedStatus === 'IN_PROGRESS',
  });

  /** 当前问诊的处方列表 */
  const { data: consultPrescriptions } = useQuery({
    queryKey: QUERY_KEYS.consultPrescriptions(selectedConsultId ?? -1),
    queryFn: () =>
      getPrescriptions({ consultId: selectedConsultId as number, page: 1, size: 20 }).then(
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

  /** 患者变化时重置病历表单；有已保存的结构化病历则回显 */
  useEffect(() => {
    setNoteChanged(false);
    setReportChiefComplaint('');
    setReportPresentIllness('');
    setReportPhysicalExam('');
    setReportDiagnosis('');
    setReportTreatmentPlan('');
    setReportGeneratedAt('');
    if (patientDetail?.doctorNote) {
      try {
        const parsedNote: unknown = JSON.parse(patientDetail.doctorNote);
        if (parsedNote && typeof parsedNote === 'object') {
          const n = parsedNote as Record<string, string>;
          setReportChiefComplaint(n.chiefComplaint ?? '');
          setReportPresentIllness(n.presentIllness ?? '');
          setReportPhysicalExam(n.physicalExamination ?? '');
          setReportDiagnosis(n.diagnosis ?? '');
          setReportTreatmentPlan(n.treatmentPlan ?? '');
          setReportGeneratedAt(n.generatedAt ?? '');
        }
      } catch {
        // 旧版纯文本病历不回显到结构化表单
      }
    }
  }, [patientDetail]);

  /** 病历字段变更：标记未保存 + 写入对应字段 */
  const handleFieldChange = (field: NoteField, value: string) => {
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
  };

  // ==================== 留言板 ====================

  const [messageInput, setMessageInput] = useState('');
  const [sendingMessage, setSendingMessage] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  /** 消息列表滚动到底部 */
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // ==================== 接诊操作 ====================

  const [startingConsult, setStartingConsult] = useState(false);
  const [endingConsult, setEndingConsult] = useState(false);

  // ==================== 队列 Tab ====================

  /** 切换队列 Tab（对齐原行为：任何 Tab 切换都重置选中患者） */
  const handleTabChange = (key: string) => {
    setQueueTab(key as QueueTab);
    setSelectedConsultId(null);
    setSelectedStatus(null);
  };

  /** 选择待接诊/接诊中患者 */
  const handleSelectItem = (item: API.QueueItem) => {
    setSelectedConsultId(item.consultId);
    setSelectedStatus(item.status);
  };

  /** 选择历史接诊记录 */
  const handleSelectHistoryItem = (item: API.ConsultHistoryItem) => {
    setSelectedConsultId(item.consultId);
    setSelectedStatus(item.status as SelectedStatus);
  };

  // ==================== 开始/结束接诊 ====================

  const handleStartConsult = async () => {
    if (!selectedConsultId) return;

    // 前端时段校验：从队列中找到当前患者，检查当前时间是否在号源时段内
    const selectedItem = queueItems.find((i) => i.consultId === selectedConsultId);
    if (selectedItem?.slotStartTime && selectedItem?.slotEndTime) {
      const now = dayjs();
      const start = dayjs(selectedItem.slotStartTime, 'HH:mm');
      const end = dayjs(selectedItem.slotEndTime, 'HH:mm');
      const currentTime = dayjs(`${now.format('HH:mm')}`, 'HH:mm');
      if (currentTime.isBefore(start) || currentTime.isAfter(end)) {
        message.warning(
          `当前不在接诊时间内（${selectedItem.slotStartTime}~${selectedItem.slotEndTime}）`,
        );
        return;
      }
    }

    setStartingConsult(true);
    try {
      await startConsult(selectedConsultId);
      message.success('开始接诊');
      setSelectedStatus('IN_PROGRESS');
      // 切到接诊中 Tab 并重置选中（对齐原行为：队列 Tab 切换时重置选中患者）
      setQueueTab('IN_PROGRESS');
      setSelectedConsultId(null);
    } catch (err: unknown) {
      message.error(getErrorMessage(err, '开始接诊失败'));
    } finally {
      setStartingConsult(false);
    }
  };

  const handleEndConsult = () => {
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
          message.success('问诊已结束');
          setSelectedStatus('COMPLETED');
          setSelectedConsultId(null);
        } catch (err: unknown) {
          message.error(getErrorMessage(err, '结束问诊失败'));
        } finally {
          setEndingConsult(false);
        }
      },
    });
  };

  // ==================== 病历保存 ====================

  const handleSaveNote = async () => {
    if (!selectedConsultId) return;
    // 构建结构化病历 JSON
    const reportData: Record<string, string> = {
      chiefComplaint: reportChiefComplaint,
      presentIllness: reportPresentIllness,
      physicalExamination: reportPhysicalExam,
      diagnosis: reportDiagnosis,
      treatmentPlan: reportTreatmentPlan,
      doctorName: currentUser?.name ?? '',
      generatedAt: dayjs().format('YYYY-MM-DD HH:mm'),
    };
    if (!reportChiefComplaint.trim() && !reportDiagnosis.trim()) {
      message.warning('请至少填写主诉或诊断');
      return;
    }
    const noteJson = JSON.stringify(reportData, null, 2);
    setSavingNote(true);
    try {
      await saveNote(selectedConsultId, { doctorNote: noteJson });
      message.success('病历已保存');
      setNoteChanged(false);
      setReportGeneratedAt(reportData.generatedAt);
    } catch (err: unknown) {
      message.error(getErrorMessage(err, '保存病历失败'));
    } finally {
      setSavingNote(false);
    }
  };

  // ==================== 留言板操作 ====================

  const handleSendMessage = async () => {
    if (!selectedConsultId || !messageInput.trim()) return;
    setSendingMessage(true);
    try {
      const msg = await sendMessage(selectedConsultId, {
        content: messageInput.trim(),
      });
      // 追加到消息缓存，消息列表即时更新
      queryClient.setQueryData<API.MessageVO[]>(
        QUERY_KEYS.consultMessages(selectedConsultId),
        (old) => [...(old ?? []), msg],
      );
      setMessageInput('');
    } catch (err: unknown) {
      message.error(getErrorMessage(err, '发送消息失败'));
    } finally {
      setSendingMessage(false);
    }
  };

  const handleMessageKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  return {
    currentUser,
    isAdmin,
    // 队列
    queueTab,
    queueItems,
    queueLoading,
    queueTotal,
    historyItems,
    historyLoading,
    handleTabChange,
    handleSelectItem,
    handleSelectHistoryItem,
    // 选中
    selectedConsultId,
    selectedStatus,
    // 患者详情
    patientDetail,
    detailLoading,
    historyDetail,
    historyDetailLoading,
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
    // 留言板
    messages: messages ?? [],
    messagesLoading,
    messageInput,
    sendingMessage,
    messagesEndRef,
    setMessageInput,
    handleSendMessage,
    handleMessageKeyDown,
    // 处方
    consultPrescriptions: consultPrescriptions ?? [],
  };
}

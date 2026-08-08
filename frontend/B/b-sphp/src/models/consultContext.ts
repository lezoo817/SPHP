/**
 * 全局接诊上下文 model（Umi model，useModel('consultContext') 消费）。
 *
 * 职责：把 ConsultQueue 页面选中的当前接诊患者 / 问诊记录 ID 桥接给全局
 * MainLayout，使悬浮 AI 助手抽屉构建对话上下文时能携带 patient_id。
 *
 * 背景：MainLayout 是全局布局，无法直接读取 ConsultQueue 页面局部 state；
 * 后端 query_patient_history / check_drug_interaction / check_contraindication /
 * check_allergy_risk / check_duplicate_medication 等 5 个 B 端工具必填
 * patient_id，缺失时 LLM 会反问医生"患者是谁"而非直接查询（后端 tool_caller
 * 的 _build_patient_context 依赖 context.patient_id 注入）。通过此 model 在
 * 选中患者时写入、切换 Tab / 结束接诊 / 离开接诊页时清除，MainLayout 即可
 * 读到当前接诊患者。
 */
import { useCallback, useState } from 'react';

/** 全局接诊上下文状态：供 MainLayout 构建 Agent 对话上下文使用。 */
export interface ConsultContextState {
  /** 当前选中接诊的患者 ID；未选中时为 undefined */
  patientId: number | undefined;
  /** 当前选中的问诊记录 ID；未选中时为 undefined */
  consultationId: number | undefined;
  /**
   * 设置当前接诊患者。
   * @param patientId 患者 ID；传 undefined 表示清除
   * @param consultationId 问诊记录 ID；传 undefined 表示清除
   */
  setCurrentConsult: (patientId?: number, consultationId?: number) => void;
  /** 清除当前接诊上下文（切换 Tab / 结束接诊 / 离开接诊页时调用）。 */
  clear: () => void;
}

/**
 * 全局接诊上下文 Hook（单例，跨组件共享状态）。
 * @returns 当前接诊患者 / 问诊 ID 及设置、清除方法
 */
export default function useConsultContext(): ConsultContextState {
  const [patientId, setPatientId] = useState<number | undefined>(undefined);
  const [consultationId, setConsultationId] = useState<number | undefined>(undefined);

  const setCurrentConsult = useCallback(
    (nextPatientId?: number, nextConsultationId?: number) => {
      setPatientId(nextPatientId);
      setConsultationId(nextConsultationId);
    },
    [],
  );

  const clear = useCallback(() => {
    setPatientId(undefined);
    setConsultationId(undefined);
  }, []);

  return { patientId, consultationId, setCurrentConsult, clear };
}

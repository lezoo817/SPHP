import '@umijs/max/typings';

// 注意：本文件因上方 import 被 TS 视为模块，须用 declare global 声明全局命名空间，
// 否则 API.* 类型在其它文件中不可见（TS2503）。
declare global {
  namespace API {
    /** 后端统一返回格式 */
    interface Result<T> {
      code: string;
      message: string;
      data: T;
      traceId: string;
    }

    /** 用户信息（从 /api/b/auth/token/parse 返回） */
    interface User {
      id?: number;
      name?: string;
      roles: string[];
      deptId?: number;
      doctorId?: number;
      hospitalId: number;
    }

    /** Token 解析响应（/api/b/auth/token/parse） */
    interface TokenParseVO {
      userId: number;
      account: string;
      roles: string[];
      deptId: number | null;
      doctorId: number | null;
      hospitalId: number;
      tokenExpiresAt: string | null;
    }

    /** 通用分页参数 */
    interface PageParams {
      page?: number;
      size?: number;
    }

    /** 通用分页响应 */
    interface PageResult<T> {
      list: T[];
      total: number;
      page: number;
      size: number;
    }

    /** 医院信息 */
    interface HospitalInfo {
      id: number;
      name: string;
      level: string;
      address?: string;
      contact?: string;
      description?: string;
      status: 'ENABLED' | 'DISABLED';
    }

    /** 更新医院信息请求 */
    interface UpdateHospitalReq {
      name: string;
      level: string;
      description?: string;
      address?: string;
      contact?: string;
    }

    /** 科室 */
    interface Department {
      id: number;
      name: string;
      hospitalId: number;
      headDoctorId?: number;
      headDoctorName?: string;
      location?: string;
      status: 'ENABLED' | 'DISABLED';
    }

    /** 科室列表查询参数 */
    interface DepartmentListParams extends PageParams {
      name?: string;
      headDoctorName?: string;
      status?: string;
    }

    /** 新增/编辑科室请求 */
    interface UpsertDepartmentReq {
      name: string;
      headDoctorId?: number;
      location?: string;
    }

    /** 更新科室状态请求 */
    interface UpdateDepartmentStatusReq {
      status: 'ENABLED' | 'DISABLED';
    }

    /** 医生 */
    interface Doctor {
      id: number;
      name: string;
      deptName: string;
      title: string;
      specialty?: string;
      licenseNo?: string;
      phone?: string;
      registrationFeeCent: number;
      status: 'ENABLED' | 'DISABLED' | 'SUSPENDED';
    }

    /** 医生列表查询参数 */
    interface DoctorListParams extends PageParams {
      deptId?: number;
      name?: string;
      status?: string;
    }

    /** 新增医生请求 */
    interface CreateDoctorReq {
      name: string;
      deptId: number;
      title: string;
      specialty?: string;
      licenseNo?: string;
      phone?: string;
      registrationFeeCent: number;
      account: string;
      password: string;
      status: 'ENABLED' | 'DISABLED' | 'SUSPENDED';
    }

    /** 编辑医生资料请求 */
    interface UpdateDoctorProfileReq {
      name: string;
      title: string;
      specialty?: string;
      introduction?: string;
    }

    /** 修改医生账号请求 */
    interface UpdateDoctorAccountReq {
      account: string;
    }

    /** 重置医生密码请求 */
    interface ResetDoctorPasswordReq {
      password: string;
    }

    /** 更新医生状态请求 */
    interface UpdateDoctorStatusReq {
      status: 'ENABLED' | 'DISABLED' | 'SUSPENDED';
    }

    /** 排班列表项 */
    interface Schedule {
      id: number;
      doctorId: number;
      doctorName: string;
      deptId: number;
      deptName: string;
      scheduleDate: string; // yyyy-MM-dd
      shift: 'MORNING' | 'AFTERNOON';
      totalSlots: number;
      bookedCount: number;
      remainCount: number;
      lockedCount: number;
      status: 'DRAFT' | 'PUBLISHED' | 'CANCELLED';
      publishedAt?: string;
    }

    /** 排班列表查询参数 */
    interface ScheduleListParams extends PageParams {
      date?: string;
      deptId?: number;
      doctorId?: number;
      status?: string;
    }

    /** 创建排班请求 */
    interface CreateScheduleReq {
      doctorId: number;
      scheduleDate: string; // yyyy-MM-dd
      shift: 'MORNING' | 'AFTERNOON';
      totalSlots: number;
    }

    /** 号源时段配置项（后端返回） */
    interface SlotConfig {
      id: number;
      startTime: string; // HH:mm
      endTime: string; // HH:mm
      totalCount: number;
      remainCount: number;
    }

    /** 号源时段配置请求项 */
    interface SlotConfigItem {
      startTime: string; // HH:mm
      endTime: string; // HH:mm
      count: number;
    }

    /** 锁定号源看板查询参数 */
    interface LockedSlotsParams extends PageParams {
      date: string; // yyyy-MM-dd（必填）
      deptId?: number;
    }

    /** 锁定号源看板项 */
    interface LockedSlot {
      slotId: number;
      scheduleId: number;
      doctorName: string;
      patientName: string;
      lockedAt: string;
      status: string;
      expireAt: string; // lockedAt + 15min
    }

    /** 号源池查询参数 */
    interface SourcePoolParams extends PageParams {
      startDate?: string; // yyyy-MM-dd
      endDate?: string; // yyyy-MM-dd
      deptId?: number;
      doctorId?: number;
    }

    /** 号源池行（按已发布排班明细，每行=医生某天某班次） */
    interface SourcePoolVO {
      scheduleId: number;
      scheduleDate: string; // yyyy-MM-dd
      shift: 'MORNING' | 'AFTERNOON';
      deptName: string; // 科室（诊室）
      doctorName: string; // 医生
      totalSlots: number; // 总号源数
      remainSlots: number; // 剩余可约号源数
      soldSlots: number; // 已约号源数
      lockedSlots: number; // 锁定中号源数
    }

    // ===================== 接诊台 =====================

    /** 待接诊队列项 */
    interface QueueItem {
      consultId: number;
      patientId: number;
      patientName: string;
      patientGender: 'MALE' | 'FEMALE' | 'UNKNOWN';
      patientAge: number;
      aiSummary?: Record<string, any>;
      queueNumber: number;
      appointmentTime: string;
      status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';
    }

    /** 接诊历史项 */
    interface ConsultHistoryItem {
      consultId: number;
      patientId: number;
      patientName: string;
      patientGender: 'MALE' | 'FEMALE' | 'UNKNOWN';
      patientDateOfBirth: string;
      chiefComplaint?: string;
      noteSummary?: string;
      status: string;
      startedAt?: string;
      endedAt?: string;
      createdAt: string;
    }

    /** 接诊历史详情 */
    interface ConsultHistoryDetail {
      consultId: number;
      patientId: number;
      status: string;
      chiefComplaint?: string;
      doctorNote?: string;
      startedAt?: string;
      endedAt?: string;
      createdAt?: string;
      prescriptions: {
        id: number;
        status: string;
        itemCount: number;
        issuedAt?: string;
      }[];
    }

    /** 队列查询参数 */
    interface QueueListParams extends PageParams {
      deptId?: number;
      status?: string;
    }

    /** 患者详细信息 */
    interface PatientDetail {
      consultId: number;
      patient: {
        id: number;
        name: string;
        gender: string;
        dateOfBirth: string;
        phone: string;
        emergencyContact: string;
      };
      allergies: AllergyInfo[];
      medicalHistories: MedicalHistoryInfo[];
      aiSummary?: Record<string, any>;
      recentPrescriptions: RecentPrescription[];
      historyRecords: HistoryRecord[];
    }

    /** 过敏史 */
    interface AllergyInfo {
      id: number;
      allergen: string;
      reaction: string;
      severity: string;
    }

    /** 既往史 */
    interface MedicalHistoryInfo {
      id: number;
      content: string;
      occurredAt: string;
    }

    /** 近期处方摘要 */
    interface RecentPrescription {
      id: number;
      status: string;
      issuedAt: string;
    }

    /** 历史就诊记录 */
    interface HistoryRecord {
      date: string;
      type: string;
      summary: string;
      status: string;
    }

    /** 开始接诊响应 */
    interface ConsultStart {
      consultId: number;
      status: string;
      startedAt: string;
    }

    /** 结束问诊响应 */
    interface ConsultEnd {
      consultId: number;
      status: string;
      endedAt: string;
    }

    /** 保存病历请求 */
    interface NoteSaveReq {
      doctorNote: string;
    }

    /** 保存病历响应 */
    interface NoteSave {
      consultId: number;
      updatedAt: string;
    }

    /** 发送消息请求 */
    interface MessageSendReq {
      content: string;
    }

    /** 消息 VO */
    interface MessageVO {
      messageId: number;
      senderType: 'PATIENT' | 'DOCTOR' | 'SYSTEM';
      content: string;
      createdAt: string;
    }

    // ===================== 处方管理 =====================

    /** 处方列表项 */
    interface Prescription {
      id: number;
      consultId: number;
      patientId: number;
      patientName: string;
      doctorId: number;
      doctorName: string;
      deptId: number;
      deptName: string;
      status: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED';
      itemCount: number;
      issuedAt?: string;
      createdAt: string;
      updatedAt: string;
    }

    /** 处方明细项（列表返回） */
    interface PrescriptionItem {
      id: number;
      drugId: number;
      drugName: string;
      dosage: string;
      frequency: string;
      usageMethod: string;
      days: number;
      quantity: number;
    }

    /** 处方详情 */
    interface PrescriptionDetail {
      id: number;
      consultId: number;
      patientId: number;
      patientName: string;
      doctorId: number;
      doctorName: string;
      deptId: number;
      deptName: string;
      status: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED';
      auditRequired: boolean;
      rejectReason?: string;
      items: PrescriptionItem[];
      createdAt: string;
      updatedAt: string;
    }

    /** 处方列表查询参数 */
    interface PrescriptionListParams extends PageParams {
      consultId?: number;
      patientId?: number;
      status?: string;
    }

    /** 提交处方请求 */
    interface PrescriptionSubmitReq {
      consultId: number;
      items: {
        drugId: number;
        dosage: string;
        frequency: string;
        usageMethod: string;
        days: number;
        quantity: number;
      }[];
    }

    /** 提交处方结果 */
    interface PrescriptionSubmitResult {
      id: number;
      status: string;
      auditRequired: boolean;
      riskWarnings: RiskWarning[];
    }

    /** 风险预警 */
    interface RiskWarning {
      level: 'WARNING' | 'ERROR';
      rule: string;
      message: string;
    }

    /** 审核请求 */
    interface AuditReq {
      action: 'APPROVED' | 'REJECTED';
      rejectReason?: string;
    }

    /** 待审核列表项 */
    interface PendingAuditItem {
      id: number;
      consultId: number;
      patientId: number;
      patientName: string;
      doctorId: number;
      doctorName: string;
      deptId: number;
      deptName: string;
      itemCount: number;
      issuedAt?: string;
      createdAt: string;
    }

    /** 处方模板 */
    interface PrescriptionTemplate {
      id: number;
      name: string;
      deptId?: number;
      deptName?: string;
      items: {
        drugId: number;
        drugName: string;
        dosage: string;
        frequency?: string;
        usageMethod: string;
        days: number;
        quantity: number;
        quantityUnit?: string;
      }[];
      createdAt: string;
    }

    /** 模板列表查询参数 */
    interface TemplateListParams extends PageParams {
      name?: string;
      deptId?: number;
    }

    /** 保存模板请求 */
    interface SaveTemplateReq {
      name: string;
      deptId?: number;
      items: {
        drugId: number;
        dosage: string;
        frequency: string;
        usageMethod: string;
        days: number;
        quantity: number;
        quantityUnit?: string;
      }[];
    }

    // ===================== 药品库存管理 =====================

    /** 药品 */
    interface Drug {
      id: number;
      name: string;
      specification: string;
      unit: string;
      indication?: string;
      manufacturer?: string;
      approvalNumber?: string;
      status: 'ENABLED' | 'DISABLED';
      /** 可用库存（当前医院各药房合计；仅按ID查询时返回） */
      availableStock?: number;
    }

    /** 药品列表查询参数 */
    interface DrugListParams extends PageParams {
      name?: string;
      status?: string;
    }

    /** 新增/编辑药品请求 */
    interface CreateDrugReq {
      name: string;
      specification: string;
      unit: string;
      indication?: string;
      manufacturer?: string;
      approvalNumber?: string;
      status: 'ENABLED' | 'DISABLED';
    }

    /** 库存项 */
    interface InventoryItem {
      id: number | null;
      pharmacyId: number | null;
      pharmacyName: string | null;
      drugId: number;
      drugName: string;
      specification: string;
      availableCount: number;
      lockedCount: number;
      safetyStock: number;
      unitPriceCent: number;
      status: 'NORMAL' | 'LOW' | 'ALERT';
    }

    /** 库存列表查询参数 */
    interface InventoryListParams extends PageParams {
      pharmacyId?: number;
      drugId?: number;
    }

    /** 更新库存请求 */
    interface UpdateInventoryReq {
      availableCount: number;
      safetyStock: number;
      unitPriceCent: number;
    }

    /** 手动释放锁定库存请求 */
    interface UnlockInventoryReq {
      drugOrderId: number;
      reason: string;
    }

    /** 库存预警项 */
    type AlertItem = InventoryItem;

    /** 药房项 */
    interface PharmacyItem {
      id: number;
      hospitalId: number;
      name: string;
      address: string;
      phone: string;
      isDefault: boolean;
      status: string;
    }

    // ===================== 患者管理 =====================

    /** 患者列表项 */
    interface PatientListItem {
      id: number;
      name: string;
      gender: 'MALE' | 'FEMALE' | 'UNKNOWN';
      age: number;
      lastVisitDate: string;
    }

    /** 患者列表查询参数 */
    interface PatientListParams extends PageParams {
      name?: string;
    }

    /** 患者详情 - 基本信息 */
    interface PatientDetailInfo {
      id: number;
      name: string;
      gender: string;
      dateOfBirth: string;
      phone: string;
      emergencyContact: string;
      allergies: AllergyInfo[];
      medicalHistories: MedicalHistoryInfo[];
    }

    /** 就诊记录项 */
    interface PatientVisitItem {
      consultId: number;
      visitDate: string;
      doctorName: string;
      deptName: string;
      summary?: string;
      status: string;
      createdAt: string;
    }

    /** 历史处方项 */
    interface PatientPrescriptionItem {
      id: number;
      consultId: number;
      doctorName: string;
      status: string;
      itemCount: number;
      issuedAt?: string;
      createdAt: string;
    }

    /** 用药计划项 */
    interface MedicationPlanItem {
      id: number;
      drugName: string;
      dosage: string;
      frequency: string;
      usageMethod: string;
      status: 'ACTIVE' | 'PAUSED' | 'COMPLETED';
      nextRemindAt?: string;
      createdAt: string;
    }

    /** 随访计划项 */
    interface FollowUpPlanItem {
      id: number;
      followUpType: string;
      content: string;
      dueAt: string;
      status: 'PENDING_CONFIRM' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED';
      createdAt: string;
    }

    /** 当前用药与随访响应 */
    interface PatientMedicationResult {
      medicationPlans: MedicationPlanItem[];
      followUpPlans: FollowUpPlanItem[];
    }

    // ===================== 统计报表 =====================

    /** 运营总览 */
    interface StatisticsOverview {
      totalAppointments: number;
      completedRate: number;
      totalRevenueCent: number;
      totalPrescriptions: number;
      avgWaitTime: number;
    }

    /** 科室统计项 */
    interface DepartmentStatItem {
      deptId: number;
      deptName: string;
      appointmentCount: number;
      consultCount: number;
      prescriptionCount: number;
      slotUsageRate: number;
    }

    /** 日统计项 */
    interface DailyStatItem {
      date: string;
      appointmentCount: number;
      consultCount: number;
      prescriptionCount: number;
      revenueCent: number;
    }

    /** 统计查询参数 */
    interface StatisticsParams {
      startDate?: string;
      endDate?: string;
      deptId?: number;
    }
  }

  /**
   * ============ AI 辅助面板（Agent）相关类型 ============
   * 与 sphp-agent 的 `app/api/routes/chat.py` SSE 事件格式对齐，覆盖
   * Agent 模块系分 V2.1 §6.2 七类事件：message / thought / action / observation /
   * card / error / done，以及 L2 确认回调的请求与响应结构。
   */
  namespace Agent {
    /** B 端对话上下文：描述当前页面业务状态，辅助 Agent 决策。 */
    interface ChatContext {
      /** 当前页面标识 */
      page?: 'doctor_workbench' | 'consultation' | 'prescription' | 'pharmacy' | 'health' | 'triage';
      /** 当前医院 ID（数据隔离维度） */
      hospital_id?: number;
      /** 当前页面选中的医生 ID */
      doctor_id?: number;
      /** 当前接诊患者 ID（B 端医生接诊时传入） */
      patient_id?: number;
      /** 当前问诊记录 ID（接诊台场景必传） */
      consultation_id?: number;
    }

    /** 发起流式对话的请求体（POST /api/chat/stream）。 */
    interface ChatRequest {
      /** 用户输入文本，1 至 2000 字 */
      content: string;
      /** 固定为 b_end */
      scope: 'b_end';
      /** 会话 ID；为空时 Agent 创建新会话 */
      session_id?: string;
      /** 附加上下文 */
      context?: ChatContext;
    }

    /** message 事件：流式文本增量。 */
    interface MessageEvent {
      /** 本次增量文本片段，前端累加拼接 */
      delta: string;
    }

    /** thought 事件：推理模型的思考增量。 */
    interface ThoughtEvent {
      /** 推理增量文本片段，仅推理模型触发 */
      delta: string;
    }

    /** action 事件：工具调用开始。 */
    interface ActionEvent {
      /** 工具英文标识符，用于与 observation 配对 */
      tool: string;
      /** 传入工具的完整参数 */
      arguments: Record<string, unknown>;
    }

    /** observation 事件：工具调用结果。 */
    interface ObservationEvent {
      /** 工具英文标识符，与对应 action 配对 */
      tool: string;
      /** 执行状态：success 或 error（后端字段为 success 布尔） */
      status: 'success' | 'error';
      /** 工具完整返回数据 */
      result?: unknown;
      /** 结果一行摘要 */
      summary?: string;
      /** 工具执行耗时，单位毫秒 */
      duration_ms?: number;
      /** 失败原因 */
      error?: string;
    }

    /** L2 确认卡片类型，决定渲染样式和详情字段。 */
    type CardType =
      | 'confirm_draft_note'
      | 'confirm_send_message'
      | 'confirm_appointment'
      | 'confirm_cancel_appointment'
      | 'confirm_pre_consultation'
      | 'confirm_drug_order'
      | 'confirm_cancel_drug_order'
      | 'confirm_allergy'
      | 'confirm_medical_history'
      | 'confirm_report'
      | 'confirm_medication_plan'
      | 'confirm_follow_up'
      | 'confirm_generic';

    /** card 事件：L2 操作确认卡片。 */
    interface CardEvent {
      card_type: CardType;
      /** 确认令牌，确认时原样传回 */
      confirm_token: string;
      /** 当前会话 ID；确认请求必须与 confirm_token 一并传回 */
      session_id: string;
      title: string;
      summary: string;
      /** 结构化详情，字段随卡片类型变化 */
      details?: Record<string, unknown>;
      /** 令牌过期时间；到期后禁用确认 */
      expires_at?: string;
    }

    /** error 事件：对话或工具执行错误。 */
    interface ErrorEvent {
      code: string;
      message: string;
      trace_id?: string;
    }

    /** done 事件：本轮结束。 */
    interface DoneEvent {
      session_id: string;
      usage?: Record<string, number> | null;
      trace_id?: string;
    }

    /** 七类 SSE 事件的联合类型。 */
    type SseEvent =
      | { event: 'message'; data: MessageEvent }
      | { event: 'thought'; data: ThoughtEvent }
      | { event: 'action'; data: ActionEvent }
      | { event: 'observation'; data: ObservationEvent }
      | { event: 'card'; data: CardEvent }
      | { event: 'error'; data: ErrorEvent }
      | { event: 'done'; data: DoneEvent };

    /** L2 确认回调请求体（POST /api/chat/confirm）。 */
    interface ConfirmRequest {
      confirm_token: string;
      session_id: string;
    }

    /** L2 确认回调响应数据。 */
    interface ConfirmData {
      /** MCP 工具返回的业务执行结果 */
      action_result?: unknown;
      /** 面向医生的业务结果提示 */
      message?: string;
    }

    /** 确认卡片在 UI 中的运行时状态。 */
    type ConfirmCardStatus = 'pending' | 'confirming' | 'done' | 'error' | 'expired';

    /** 会话消息类型。 */
    type MessageRole = 'user' | 'assistant';

    /** 一条会话消息（AI 文本累加或用户输入）。 */
    interface Message {
      id: string;
      role: MessageRole;
      content: string;
      /** 是否仍在本轮流式输出中 */
      streaming?: boolean;
      createdAt: number;
    }

    /** 思考片段（thought.delta 累加）。 */
    interface Thought {
      id: string;
      content: string;
      streaming?: boolean;
      createdAt: number;
    }

    /** 工具调用卡片（action 与 observation 配对）。 */
    interface ToolCard {
      id: string;
      tool: string;
      label: string;
      arguments?: Record<string, unknown>;
      status: 'loading' | 'success' | 'error';
      summary?: string;
      error?: string;
      result?: unknown;
      durationMs?: number;
      createdAt: number;
    }

    /** L2 确认卡片运行时对象。 */
    interface ConfirmCard {
      id: string;
      cardType: CardType;
      confirmToken: string;
      sessionId: string;
      title: string;
      summary: string;
      details?: Record<string, unknown>;
      expiresAt?: string;
      status: ConfirmCardStatus;
      resultMessage?: string;
      errorCode?: string;
      errorMessage?: string;
      createdAt: number;
    }

    /** 会话条目类型：消息、思考、工具卡片、确认卡片按到达顺序排列。 */
    type Entry =
      | { kind: 'message'; data: Message }
      | { kind: 'thought'; data: Thought }
      | { kind: 'tool'; data: ToolCard }
      | { kind: 'card'; data: ConfirmCard };

    /** 流式连接状态。 */
    type ConnectionState = 'idle' | 'connecting' | 'streaming' | 'error';

    /** 历史会话条目（GET /api/chat/sessions 响应）。 */
    interface Session {
      session_id: string;
      title: string;
      last_message: string | null;
      message_count: number;
      updated_at: string;
    }

    /** 历史会话列表响应。 */
    interface SessionList {
      sessions: Session[];
    }

    /** 历史消息条目。 */
    interface HistoryMessage {
      role: 'user' | 'assistant';
      content: string;
    }
  }
}

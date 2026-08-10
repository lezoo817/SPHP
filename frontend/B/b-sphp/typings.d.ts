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
      /** 隐藏失效排班：true 时排除 CANCELLED + 已过期 PUBLISHED（与 status 过滤 AND 组合） */
      hideInvalid?: boolean;
    }

    /** 创建排班请求 */
    interface CreateScheduleReq {
      doctorId: number;
      scheduleDate: string; // yyyy-MM-dd
      shift: 'MORNING' | 'AFTERNOON';
      totalSlots: number;
      /** 创建成功后立即发布（后端按 1小时/段 自动配置号源时段并发布） */
      publishImmediately?: boolean;
    }

    /** 号源时段配置项（后端返回） */
    interface SlotConfig {
      id?: number; // 本地新增时段在保存前无 id
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

    /** 批量排班请求（1 医生 × 日期范围 × 星期模式 × 班次 + 号源 + 拆分方式） */
    interface BatchScheduleReq {
      doctorId: number;
      startDate: string; // yyyy-MM-dd
      endDate: string; // yyyy-MM-dd（含）
      /** 星期模式：1=周一, 7=周日（与 DayOfWeek 对齐） */
      weekdays: number[];
      shifts: ('MORNING' | 'AFTERNOON')[];
      totalSlots: number; // 1~99
      /** 时段拆分方式：HOURLY=1小时/段, HALF_HOUR=30分钟/段, FULL=整段 */
      slotSplitMode: 'HOURLY' | 'HALF_HOUR' | 'FULL';
    }

    /** 批量排班预览 - 单班次时段拆分预览 */
    interface BatchSlotSplitItem {
      startTime: string;
      endTime: string;
      count: number;
    }

    /** 批量排班预览 - 单个候选 */
    interface BatchPreviewItem {
      scheduleDate: string; // yyyy-MM-dd
      shift: 'MORNING' | 'AFTERNOON';
      /** 去向：CREATE=新建, REUSE=复用已作废, SKIP=跳过 */
      action: 'CREATE' | 'REUSE' | 'SKIP';
      skipReason?: string;
    }

    /** 批量排班预览响应 */
    interface BatchPreviewResp {
      doctorId: number;
      doctorName: string;
      startDate: string;
      endDate: string;
      weekdays: number[];
      shifts: ('MORNING' | 'AFTERNOON')[];
      totalSlots: number;
      slotSplitMode: 'HOURLY' | 'HALF_HOUR' | 'FULL';
      slotSplitPreview: API.BatchSlotSplitItem[];
      items: API.BatchPreviewItem[];
      toCreateCount: number;
      toSkipCount: number;
    }

    /** 批量排班提交 - 单条结果 */
    interface BatchItem {
      scheduleId: number;
      scheduleDate: string;
      shift: 'MORNING' | 'AFTERNOON';
    }

    /** 批量排班提交 - 跳过项 */
    interface BatchSkipItem {
      scheduleDate: string;
      shift: 'MORNING' | 'AFTERNOON';
      reason: string;
    }

    /** 批量排班提交报告 */
    interface BatchCreateReport {
      createdCount: number;
      reusedCount: number;
      skippedCount: number;
      createdItems: API.BatchItem[];
      reusedItems: API.BatchItem[];
      skippedItems: API.BatchSkipItem[];
    }

    /** 批量发布排班请求 */
    interface BatchPublishReq {
      scheduleIds: number[];
    }

    /** 批量发布排班 - 失败项 */
    interface BatchPublishFailedItem {
      scheduleId: number;
      reason: string;
    }

    /** 批量发布排班报告 */
    interface BatchPublishReport {
      publishedCount: number;
      failedCount: number;
      failedItems: API.BatchPublishFailedItem[];
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
      /** 是否已过期（PUBLISHED 且 schedule_date < today），与后端 EXPIRED 口径一致 */
      isExpired?: boolean;
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
      /** 号源时段开始时间（HH:mm），用于接诊时段校验 */
      slotStartTime?: string;
      /** 号源时段结束时间（HH:mm） */
      slotEndTime?: string;
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
      /** 接诊医生姓名（从 doctor 表加载，非登录账号） */
      doctorName?: string;
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
      /** 病历记录（结构化 JSON 或旧版纯文本），用于接诊中回显已保存病历 */
      doctorNote?: string;
      allergies: AllergyInfo[];
      medicalHistories: MedicalHistoryInfo[];
      aiSummary?: Record<string, any>;
      recentPrescriptions: RecentPrescription[];
      historyRecords: HistoryRecord[];
    }

    /** 接诊台补录过敏史请求 */
    interface AllergyCreateReq {
      allergen: string;
      reaction?: string;
      severity: 'MILD' | 'MODERATE' | 'SEVERE';
    }

    /** 在线问诊列表项 */
    interface OnlineConsultationItem {
      consultId: number;
      patientId: number;
      patientName: string;
      patientGender: string;
      status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';
      aiSummary?: Record<string, any>;
      chiefComplaint?: string;
      submittedAt?: string;
      doctorRepliedAt?: string;
      canStart: boolean;
      canReply: boolean;
    }

    /** 在线问诊详情 */
    interface OnlineConsultationDetail {
      consultId: number;
      appointmentId?: number;
      status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';
      chiefComplaint?: string;
      historyOfPresentIllness?: string;
      submittedAt?: string;
      doctorRepliedAt?: string;
      patientDetail: PatientDetail;
      messages: MessageVO[];
      prescriptions: Prescription[];
      canStart: boolean;
      canReply: boolean;
    }

    /** 在线问诊回复结果 */
    interface OnlineConsultationReplyResult {
      consultId: number;
      messageId: number;
      status: 'COMPLETED';
      repliedAt: string;
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
      doctorName?: string;
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
      /** 命中风险规则时的快照（重复用药 WARNING / 高危药品 AUDIT），SUBMITTED 态存在 */
      riskWarnings?: RiskWarning[];
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

    /** 处方详情（与后端 PrescriptionDetailVO 嵌套结构对齐） */
    interface PrescriptionDetail {
      id: number;
      consultId: number;
      /** 医生信息（嵌套） */
      doctor?: { id?: number; name?: string; title?: string; deptName?: string };
      /** 患者信息（嵌套） */
      patient?: { id?: number; name?: string; gender?: string; dateOfBirth?: string };
      status: 'DRAFT' | 'SUBMITTED' | 'APPROVED' | 'REJECTED';
      auditRequired: boolean;
      riskWarnings?: RiskWarning[];
      /** 驳回原因（仅 REJECTED 时存在） */
      rejectReason?: string;
      items: PrescriptionItem[];
      issuedAt?: string;
      auditedAt?: string;
      createdAt?: string;
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

    /** 风险预警（预检返回带 drugId/source；跨药品规则如重复用药 drugId 为空） */
    interface RiskWarning {
      level: 'WARNING' | 'ERROR' | 'AUDIT';
      rule: string;
      message: string;
      /** 命中药品 ID（重复用药等跨药品规则为空） */
      drugId?: number;
      /** 命中药品名称 */
      drugName?: string;
      /** 命中来源（如：患者过敏史「青霉素」/ 既往史「糖尿病」） */
      source?: string;
    }

    /** 处方风险预检请求（复用提交明细结构） */
    interface PrescriptionPrecheckReq {
      consultId: number;
      items: PrescriptionSubmitReq['items'];
    }

    /** 处方风险预检结果 */
    interface PrescriptionPrecheckResult {
      warnings: RiskWarning[];
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
      riskWarnings?: RiskWarning[];
      issuedAt?: string;
      createdAt: string;
    }

    /** 处方模板 */
    interface PrescriptionTemplate {
      id: number;
      name: string;
      deptId?: number;
      deptName?: string;
      doctorName?: string; // 创建人（列表接口返回）
      itemCount?: number; // 药品项数（列表接口返回）
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
      updatedAt?: string;
      updatedByName?: string;
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
      /** 禁忌症（供处方风险拦截器做禁忌匹配） */
      contraindication?: string;
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
      /** 禁忌症（供处方风险拦截器做禁忌匹配） */
      contraindication?: string;
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
}

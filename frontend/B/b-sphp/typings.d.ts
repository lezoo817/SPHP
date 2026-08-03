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
      description?: string;
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
      description?: string;
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
        usageMethod: string;
        days: number;
        quantity: number;
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
      id: number;
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
  }
}

/** 后端统一响应结构。 */
export interface ApiResponse<T> {
  code: string;
  message: string;
  data: T;
  traceId?: string;
}

/** 当前登录用户摘要。 */
export interface LoginUser {
  id: number;
  account: string;
}

/** C 端 Token 对与过期时长。 */
export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

/** 登录接口返回数据。 */
export interface LoginData extends TokenPair {
  user: LoginUser;
}

/** 图形验证码数据。 */
export interface CaptchaData {
  challengeId: string;
  imageBase64: string;
  expireSeconds: number;
}

/** 家庭关系编码。 */
export type FamilyRelation = 'SPOUSE' | 'PARENT' | 'CHILD' | 'OTHER' | 'SELF';

/** 家庭成员列表项。 */
export interface FamilyMember {
  patientId: number;
  name: string;
  relation: FamilyRelation;
  relationName: string;
  gender?: 'MALE' | 'FEMALE' | 'UNKNOWN';
  birthday?: string;
  phone?: string;
  isDefault: boolean;
}

/** 家庭成员编辑字段。 */
export interface FamilyMemberPayload {
  name: string;
  relation: Exclude<FamilyRelation, 'SELF'>;
  gender?: 'MALE' | 'FEMALE' | 'UNKNOWN';
  birthday?: string;
  phone?: string;
  idCardNo?: string;
  emergencyContact?: string;
}

/** 当前登录账号本人资料。 */
export interface Profile {
  id: number;
  name: string;
  gender?: 'MALE' | 'FEMALE' | 'UNKNOWN';
  birthday?: string;
  phone?: string;
  emergencyContact?: string;
}

/** 更新本人资料的可提交字段。 */
export interface ProfileUpdatePayload {
  name: string;
  gender?: 'MALE' | 'FEMALE' | 'UNKNOWN';
  birthday?: string;
  phone?: string;
  emergencyContact?: string;
}

/** 更新本人资料后的最小响应。 */
export interface ProfileUpdateResult {
  id: number;
  name: string;
  phone?: string;
  updatedAt: string;
}

/** 健康档案中的最小患者资料。 */
export interface HealthProfile {
  id: number;
  name: string;
  gender?: 'MALE' | 'FEMALE' | 'UNKNOWN';
}

/** 过敏史记录。 */
export interface Allergy {
  id: number;
  allergen: string;
  reaction?: string;
  updatedAt?: string;
}

/** 既往史记录。 */
export interface MedicalHistory {
  id: number;
  content: string;
  occurredAt?: string;
  updatedAt?: string;
}

/** 健康档案响应数据。 */
export interface HealthRecord {
  profile: HealthProfile;
  allergies: Allergy[];
  medicalHistories: MedicalHistory[];
  summary: string;
}

/** 新增或更新过敏史字段。 */
export interface AllergyPayload {
  patientId?: number;
  allergen: string;
  reaction?: string;
}

/** 新增或更新既往史字段。 */
export interface MedicalHistoryPayload {
  patientId?: number;
  content: string;
  occurredAt?: string;
}

/** 可供选择的医院。 */
export interface Hospital { hospitalId: number; name: string; level?: string; address?: string; contact?: string; }
/** 医院下的可预约科室。 */
export interface Department { id: number; name: string; description?: string; }
/** 医生及当天可用号源摘要。 */
export interface Doctor { id: number; name: string; title?: string; specialty?: string; registrationFeeCent: number; availableCount: number; departmentId?: number; departmentName?: string; }
/** 医生预约时段。 */
export interface AppointmentSlot { slotId: number; startTime: string; endTime: string; feeCent: number; availableCount: number; }
/** 挂号订单列表项。 */
export interface Appointment { id: number; doctorName: string; departmentName: string; startTime: string; status: 'UNPAID' | 'PAID' | 'COMPLETED' | 'CANCELLED'; amountCent: number; expireAt?: string; }
/** 挂号订单详情。 */
export interface AppointmentDetail extends Appointment { doctor: { id: number; name: string; departmentName: string }; slot: { id: number; startTime: string; endTime: string }; payment?: { id: number; status: string }; }
/** 分页响应。 */
export interface PageData<T> { pageNo: number; pageSize: number; total: number; records: T[]; }
/** 问诊记录列表项。 */
export interface Consultation { id: number; appointmentId: number; doctorName: string; status: 'DRAFT' | 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'NO_SHOW'; updatedAt: string; }
/** 问诊详情与文字消息。 */
export interface ConsultationDetail extends Consultation { doctor: { id: number; name: string; title?: string }; preConsultation?: { chiefComplaint: string; historyOfPresentIllness?: string; attachments?: { name: string; url: string }[]; savedAt?: string; submittedAt?: string }; messages: { id: number; senderType: string; content: string; createdAt: string }[]; prescriptionIds: number[]; }
/** 已批准处方列表项。 */
export interface Prescription { id: number; consultationId: number; doctorName: string; status: 'APPROVED'; issuedAt: string; }
/** 已批准处方详情。 */
export interface PrescriptionDetail extends Prescription { doctor: { id: number; name: string; title?: string }; items: { drugId: number; drugName: string; specification?: string; dosage?: string; frequency?: string; usage?: string; durationDays?: number }[]; }
/** 药房处方库存。 */ export interface PharmacyInventory { pharmacyId:number; name:string; isDefault:boolean; items:{drugId:number;availableCount:number;unitPriceCent:number}[]; }
/** 购药订单列表项。 */ export interface DrugOrder { id:number; orderName:string; pharmacyName:string; status:string; logisticsStatus?:string; latestLogisticsNode?:string; amountCent:number; expireAt?:string; }
/** 购药订单详情。 */ export interface DrugOrderDetail extends DrugOrder { pharmacy:{id:number;name:string}; delivery?:{address:string;logisticsStatus:string;traces:{node:string;occurredAt:string}[]}; payment?:{id:number;status:string}; items:{drugId:number;drugName:string;quantity:number;unitPriceCent:number}[]; }

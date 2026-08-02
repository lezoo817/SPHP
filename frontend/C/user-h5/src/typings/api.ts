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

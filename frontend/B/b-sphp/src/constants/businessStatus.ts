/**
 * B 端业务状态 / 角色 / 性别字符串常量。
 *
 * 与后端枚举（BUserStatusEnum / BRoleEnum / 问诊·处方·排班状态机）口径一致，
 * 统一在文件维护，避免散落魔法值导致前后端状态字符串漂移。
 * 全部以 {@code as const} 声明，保证在类型位置（联合类型成员）引用时仍保留字面量类型。
 */

/** 账号/医生状态：启用 */
export const STATUS_ENABLED = 'ENABLED' as const;
/** 账号/医生状态：停用 */
export const STATUS_DISABLED = 'DISABLED' as const;
/** 医生状态：暂停（doctor 表扩展态，b_user 无此状态） */
export const STATUS_SUSPENDED = 'SUSPENDED' as const;

/** 排班状态：草稿 */
export const STATUS_DRAFT = 'DRAFT' as const;
/** 排班状态：已发布 */
export const STATUS_PUBLISHED = 'PUBLISHED' as const;
/** 排班状态：已作废 */
export const STATUS_CANCELLED = 'CANCELLED' as const;

/** 问诊/业务状态：待接诊 */
export const STATUS_PENDING = 'PENDING' as const;
/** 问诊/业务状态：接诊中 */
export const STATUS_IN_PROGRESS = 'IN_PROGRESS' as const;
/** 问诊/业务状态：已完成 */
export const STATUS_COMPLETED = 'COMPLETED' as const;

/** 处方状态：审核通过 */
export const STATUS_APPROVED = 'APPROVED' as const;
/** 处方状态：已驳回 */
export const STATUS_REJECTED = 'REJECTED' as const;
/** 处方状态：已提交待审核 */
export const STATUS_SUBMITTED = 'SUBMITTED' as const;

/** 性别：男 */
export const GENDER_MALE = 'MALE' as const;
/** 性别：女 */
export const GENDER_FEMALE = 'FEMALE' as const;

/** 角色：医院管理员 */
export const ROLE_ADMIN = 'ADMIN' as const;
/** 角色：科室主任 */
export const ROLE_DEPT_HEAD = 'DEPT_HEAD' as const;
/** 角色：普通医生 */
export const ROLE_DOCTOR = 'DOCTOR' as const;

/** 消息发送方：患者 */
export const SENDER_PATIENT = 'PATIENT' as const;
/** 消息发送方：医生 */
export const SENDER_DOCTOR = 'DOCTOR' as const;
/** 消息发送方：系统 */
export const SENDER_SYSTEM = 'SYSTEM' as const;

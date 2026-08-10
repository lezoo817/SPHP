/**
 * B 端 API 路径常量。
 *
 * 与后端 REST 路由一一对应，集中维护便于统一改前缀 / 排查路径笔误；
 * 动态路径（含 ${id}）不使用常量，保持内联模板。
 */

export const API_URLS = {
  /** /api/b/admin/departments */
  ADMIN_DEPARTMENTS: '/api/b/admin/departments',
  /** /api/b/admin/doctors */
  ADMIN_DOCTORS: '/api/b/admin/doctors',
  /** /api/b/admin/drugs */
  ADMIN_DRUGS: '/api/b/admin/drugs',
  /** /api/b/admin/hospitals */
  ADMIN_HOSPITALS: '/api/b/admin/hospitals',
  /** /api/b/admin/inventory */
  ADMIN_INVENTORY: '/api/b/admin/inventory',
  /** /api/b/admin/inventory/alerts */
  ADMIN_INVENTORY_ALERTS: '/api/b/admin/inventory/alerts',
  /** /api/b/admin/patients */
  ADMIN_PATIENTS: '/api/b/admin/patients',
  /** /api/b/admin/pharmacies */
  ADMIN_PHARMACIES: '/api/b/admin/pharmacies',
  /** /api/b/admin/schedules */
  ADMIN_SCHEDULES: '/api/b/admin/schedules',
  /** /api/b/admin/schedules/batch */
  ADMIN_SCHEDULES_BATCH: '/api/b/admin/schedules/batch',
  /** /api/b/admin/schedules/batch-publish */
  ADMIN_SCHEDULES_BATCH_PUBLISH: '/api/b/admin/schedules/batch-publish',
  /** /api/b/admin/schedules/batch/preview */
  ADMIN_SCHEDULES_BATCH_PREVIEW: '/api/b/admin/schedules/batch/preview',
  /** /api/b/admin/slots/locked */
  ADMIN_SLOTS_LOCKED: '/api/b/admin/slots/locked',
  /** /api/b/admin/source-pool */
  ADMIN_SOURCE_POOL: '/api/b/admin/source-pool',
  /** /api/b/admin/statistics/daily */
  ADMIN_STATISTICS_DAILY: '/api/b/admin/statistics/daily',
  /** /api/b/admin/statistics/department */
  ADMIN_STATISTICS_DEPARTMENT: '/api/b/admin/statistics/department',
  /** /api/b/admin/statistics/overview */
  ADMIN_STATISTICS_OVERVIEW: '/api/b/admin/statistics/overview',
  /** /api/b/auth/login */
  AUTH_LOGIN: '/api/b/auth/login',
  /** /api/b/auth/logout */
  AUTH_LOGOUT: '/api/b/auth/logout',
  /** /api/b/auth/token/parse */
  AUTH_TOKEN_PARSE: '/api/b/auth/token/parse',
  /** /api/b/doctor/consult/history */
  DOCTOR_CONSULT_HISTORY: '/api/b/doctor/consult/history',
  /** /api/b/doctor/drugs */
  DOCTOR_DRUGS: '/api/b/doctor/drugs',
  /** /api/b/doctor/online-consultations */
  DOCTOR_ONLINE_CONSULTATIONS: '/api/b/doctor/online-consultations',
  /** /api/b/doctor/queue */
  DOCTOR_QUEUE: '/api/b/doctor/queue',
  /** /api/b/prescription-templates */
  PRESCRIPTION_TEMPLATES: '/api/b/prescription-templates',
  /** /api/b/prescriptions */
  PRESCRIPTIONS: '/api/b/prescriptions',
  /** /api/b/prescriptions/pending-audit */
  PRESCRIPTIONS_PENDING_AUDIT: '/api/b/prescriptions/pending-audit',
  /** /api/b/prescriptions/precheck */
  PRESCRIPTIONS_PRECHECK: '/api/b/prescriptions/precheck',
  /** /api/ws/consultation */
  WS_CONSULTATION: '/api/ws/consultation',
} as const;

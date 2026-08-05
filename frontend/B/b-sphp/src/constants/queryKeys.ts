/**
 * TanStack Query 查询键与缓存策略常量（对齐 B 端前端设计文档 §5.2 缓存策略表）。
 *
 * - QUERY_KEYS：useQuery / invalidateQueries 使用的查询键；
 * - STALE_TIME：数据在缓存中保持"新鲜"的时长，期间内重复访问不重新请求。
 *
 * 写操作成功后调用 queryClient.invalidateQueries({ queryKey }) 刷新对应列表。
 */
export const QUERY_KEYS = {
  /** 待接诊队列（15s） */
  queue: ['consult', 'queue'] as const,
  /** 患者详情（60s） */
  patientDetail: (consultId: number) =>
    ['consult', 'patient-detail', consultId] as const,
  /** 排班列表（30s） */
  schedules: ['schedule', 'list'] as const,
  /** 排班时段配置（30s） */
  scheduleSlots: (id: number) => ['schedule', 'slots', id] as const,
  /** 医院信息（5min） */
  hospital: ['admin', 'hospital'] as const,
  /** 科室列表（5min） */
  departments: ['admin', 'departments'] as const,
  /** 医生列表（5min） */
  doctors: ['admin', 'doctors'] as const,
  /** 处方列表（30s） */
  prescriptions: ['prescription', 'list'] as const,
  /** 药品目录（5min） */
  drugs: ['drug', 'catalog'] as const,
  /** 库存列表（30s） */
  inventory: ['drug', 'inventory'] as const,
  /** 药房列表（5min，供库存/预警页下拉筛选） */
  pharmacies: ['admin', 'pharmacies'] as const,
  /** 患者列表（30s） */
  patients: ['patient', 'list'] as const,
  /** 统计报表（5min） */
  statistics: ['statistics'] as const,
} as const;

/** 各查询的 staleTime（毫秒）。 */
export const STALE_TIME = {
  queue: 15_000,
  patientDetail: 60_000,
  schedules: 30_000,
  scheduleSlots: 30_000,
  hospital: 5 * 60_000,
  departments: 5 * 60_000,
  doctors: 5 * 60_000,
  prescriptions: 30_000,
  drugs: 5 * 60_000,
  inventory: 30_000,
  pharmacies: 5 * 60_000,
  patients: 30_000,
  statistics: 5 * 60_000,
} as const;

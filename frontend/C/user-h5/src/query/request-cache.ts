import { queryClient } from './client';

/** 低频、普通业务与高实时资源的缓存时长。 */
export const CACHE_STALE_TIME = {
  /** 家庭成员、本人资料和医院资源。 */
  STABLE: 5 * 60_000,
  /** 订单、处方、病历、通知和健康计划。 */
  BUSINESS: 30_000,
  /** 号源、支付、库存等状态变化频繁的资源。 */
  REALTIME: 15_000,
} as const;

/**
 * 根据请求路径识别缓存资源类型，供读缓存与写后失效共用。
 * @param path C 端 API 相对路径
 * @returns 缓存资源名称
 */
export function getRequestCacheResource(path: string): string {
  if (path.includes('/family-members')) return 'family-members';
  if (path.includes('/profile')) return 'profile';
  if (path.includes('/hospitals')) return 'hospitals';
  if (path.includes('/departments')) return 'departments';
  if (path.includes('/doctors/') && path.includes('/slots')) return 'slots';
  if (path.includes('/doctors')) return 'doctors';
  if (path.includes('/appointments/doctor-booking-status')) return 'doctor-booking-status';
  if (path.includes('/appointments')) return 'appointments';
  if (path.includes('/waitlists')) return 'waitlists';
  if (path.includes('/payments')) return 'payments';
  if (path.includes('/prescriptions')) return 'prescriptions';
  if (path.includes('/consultations')) return 'consultations';
  if (path.includes('/medical-records')) return 'medical-records';
  if (path.includes('/notifications')) return 'notifications';
  if (path.includes('/medication-plans')) return 'medication-plans';
  if (path.includes('/follow-ups')) return 'follow-ups';
  if (path.includes('/health-record')) return 'health-record';
  if (path.includes('/delivery-addresses')) return 'delivery-addresses';
  if (path.includes('/pharmacies/inventory')) return 'pharmacy-inventory';
  if (path.includes('/drug-orders')) return 'drug-orders';
  return 'other';
}

/**
 * 根据资源类型提供符合实时性要求的缓存时长。
 * @param resource 缓存资源名称
 * @returns 资源在后台刷新前可直接复用的时长
 */
export function getRequestCacheStaleTime(resource: string): number {
  if (['family-members', 'profile', 'hospitals', 'departments'].includes(resource)) return CACHE_STALE_TIME.STABLE;
  if (['slots', 'payments', 'pharmacy-inventory', 'doctor-booking-status'].includes(resource)) return CACHE_STALE_TIME.REALTIME;
  return CACHE_STALE_TIME.BUSINESS;
}

/**
 * 构建按当前账号、资源类型与完整请求路径隔离的缓存键。
 * @param userId 当前登录账号编号
 * @param path C 端 API 相对路径
 * @returns TanStack Query 缓存键
 */
export function buildRequestQueryKey(userId: number | undefined, path: string): readonly [string, number | string, string, string] {
  return ['cend', userId || 'anonymous', getRequestCacheResource(path), path];
}

/**
 * 优先返回内存中的缓存值，并在过期后发起不阻塞页面的后台刷新。
 * @param queryKey 当前账号范围内的查询键
 * @param queryFn 实际网络读取函数
 * @param staleTime 允许复用缓存的时长
 * @returns 首次读取的网络结果或已缓存数据
 */
export async function readWithStaleCache<T>(queryKey: readonly unknown[], queryFn: () => Promise<T>, staleTime: number): Promise<T> {
  const cached = queryClient.getQueryData<T>(queryKey);
  if (cached !== undefined) {
    const state = queryClient.getQueryState(queryKey);
    if (state?.isInvalidated) {
      // 支付等写操作后等待最新响应，避免物流页和购药首页短暂展示旧状态。
      return queryClient.fetchQuery({ queryKey, queryFn, staleTime, retry: false });
    }
    const isStale = !state?.dataUpdatedAt || Date.now() - state.dataUpdatedAt >= staleTime;
    if (isStale) {
      // 后台请求失败时保留页面已显示的缓存，避免切换页面时出现空白。
      void queryClient.fetchQuery({ queryKey, queryFn, staleTime, retry: false }).catch(() => undefined);
    }
    return cached;
  }
  return queryClient.fetchQuery({ queryKey, queryFn, staleTime, retry: false });
}

/**
 * 失效当前账号指定资源的全部查询，下一次读取会使用服务端最新结果。
 * @param userId 当前登录账号编号
 * @param resources 需要失效的缓存资源
 * @returns 所有失效操作完成后的 Promise
 */
export function invalidateRequestResources(userId: number | undefined, ...resources: string[]): Promise<void> {
  return Promise.all(resources.map((resource) => queryClient.invalidateQueries({ queryKey: ['cend', userId || 'anonymous', resource] }))).then(() => undefined);
}

/** 清空浏览器内存中的全部服务端缓存，用于退出登录和账号切换。 */
export function clearRequestCache(): void {
  queryClient.clear();
}

/**
 * 根据写接口路径失效关联的患者业务数据。
 * @param path 成功写入的 C 端 API 路径
 * @param userId 当前登录账号编号
 * @returns 关联缓存失效完成后的 Promise
 */
export function invalidateByMutationPath(path: string, userId: number | undefined): Promise<void> {
  const resource = getRequestCacheResource(path);
  const related = new Set([resource]);
  if (['family-members', 'profile'].includes(resource)) ['family-members', 'profile', 'health-record'].forEach((item) => related.add(item));
  if (['appointments', 'waitlists', 'payments'].includes(resource)) ['appointments', 'waitlists', 'payments', 'slots', 'doctor-booking-status', 'notifications', 'drug-orders', 'pharmacy-inventory'].forEach((item) => related.add(item));
  if (['drug-orders', 'pharmacy-inventory'].includes(resource)) ['drug-orders', 'pharmacy-inventory', 'payments', 'notifications'].forEach((item) => related.add(item));
  if (['medication-plans', 'follow-ups'].includes(resource)) ['medication-plans', 'follow-ups', 'notifications'].forEach((item) => related.add(item));
  return invalidateRequestResources(userId, ...related);
}

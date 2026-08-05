import { useQuery } from '@tanstack/react-query';
import type { FamilyMember } from '../typings/api';
import { getSession } from '../models/session';
import { CACHE_STALE_TIME, buildRequestQueryKey } from '../query/request-cache';
import { request } from '../services/request';

/**
 * 共享当前账号的本人与家属列表，并在后台刷新过期缓存。
 * @returns 家庭成员数据、首次加载和后台刷新状态
 */
export function useFamilyMembers() {
  const userId = getSession()?.user.id;
  return useQuery<FamilyMember[]>({
    queryKey: buildRequestQueryKey(userId, '/c/v1/family-members'),
    // Hook 已运行在同一 Query Key 内，直接请求以避免嵌套缓存查询。
    queryFn: () => request<FamilyMember[]>('/c/v1/family-members', { method: 'GET', skipCache: true }),
    staleTime: CACHE_STALE_TIME.STABLE,
    enabled: Boolean(userId),
  });
}

import { beforeEach, describe, expect, it, vi } from 'vitest';
import { buildRequestQueryKey, clearRequestCache, invalidateByMutationPath, readWithStaleCache } from './request-cache';
import { queryClient } from './client';

describe('请求缓存规则', () => {
  beforeEach(() => {
    clearRequestCache();
  });

  it('同一账号和路径复用缓存，不同账号严格隔离', async () => {
    const loader = vi.fn().mockResolvedValue([{ patientId: 1 }]);
    const firstKey = buildRequestQueryKey(1, '/c/v1/family-members');
    const anotherAccountKey = buildRequestQueryKey(2, '/c/v1/family-members');
    await expect(readWithStaleCache(firstKey, loader, 60_000)).resolves.toEqual([{ patientId: 1 }]);
    await expect(readWithStaleCache(firstKey, loader, 60_000)).resolves.toEqual([{ patientId: 1 }]);
    expect(loader).toHaveBeenCalledTimes(1);
    expect(queryClient.getQueryData(anotherAccountKey)).toBeUndefined();
  });

  it('过期缓存立即返回旧值，并发起不阻塞的后台刷新', async () => {
    const key = buildRequestQueryKey(1, '/c/v1/prescriptions?patientId=1');
    queryClient.setQueryData(key, [{ id: 1 }], { updatedAt: Date.now() - 60_000 });
    const loader = vi.fn().mockResolvedValue([{ id: 2 }]);
    await expect(readWithStaleCache(key, loader, 30_000)).resolves.toEqual([{ id: 1 }]);
    await vi.waitFor(() => expect(loader).toHaveBeenCalledTimes(1));
    await vi.waitFor(() => expect(queryClient.getQueryData(key)).toEqual([{ id: 2 }]));
  });

  it('后台刷新失败时保留页面已经展示的缓存数据', async () => {
    const key = buildRequestQueryKey(1, '/c/v1/notifications?pageNo=1');
    queryClient.setQueryData(key, [{ id: 1 }], { updatedAt: Date.now() - 60_000 });
    const loader = vi.fn().mockRejectedValue(new Error('网络异常'));
    await expect(readWithStaleCache(key, loader, 30_000)).resolves.toEqual([{ id: 1 }]);
    await vi.waitFor(() => expect(loader).toHaveBeenCalledTimes(1));
    expect(queryClient.getQueryData(key)).toEqual([{ id: 1 }]);
  });

  it('写入挂号相关资源后失效订单、号源与支付缓存', async () => {
    const appointmentsKey = buildRequestQueryKey(1, '/c/v1/appointments?patientId=1');
    const slotsKey = buildRequestQueryKey(1, '/c/v1/doctors/1/slots?hospitalId=1&date=2026-08-05');
    queryClient.setQueryData(appointmentsKey, [{ id: 1 }]);
    queryClient.setQueryData(slotsKey, [{ slotId: 1 }]);
    await invalidateByMutationPath('/c/v1/appointments/1/cancel', 1);
    expect(queryClient.getQueryState(appointmentsKey)?.isInvalidated).toBe(true);
    expect(queryClient.getQueryState(slotsKey)?.isInvalidated).toBe(true);
  });

  it('主动失效的缓存等待最新响应，不能返回旧订单状态', async () => {
    const key = buildRequestQueryKey(1, '/c/v1/drug-orders/1');
    queryClient.setQueryData(key, { id: 1, status: 'PENDING_PAYMENT' });
    await queryClient.invalidateQueries({ queryKey: key });
    const loader = vi.fn().mockResolvedValue({ id: 1, status: 'PAID' });
    await expect(readWithStaleCache(key, loader, 30_000)).resolves.toEqual({ id: 1, status: 'PAID' });
    expect(loader).toHaveBeenCalledTimes(1);
  });

  it('购药支付成功后失效购药订单缓存，物流页读取最新状态', async () => {
    const drugOrderKey = buildRequestQueryKey(1, '/c/v1/drug-orders/1');
    queryClient.setQueryData(drugOrderKey, { id: 1, status: 'PENDING_PAYMENT' });
    await invalidateByMutationPath('/c/v1/payments/99/simulate-pay', 1);
    expect(queryClient.getQueryState(drugOrderKey)?.isInvalidated).toBe(true);
  });
});

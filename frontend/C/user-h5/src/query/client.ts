import { QueryClient } from '@tanstack/react-query';

/** 全局服务端状态缓存客户端，仅保存当前浏览器内存中的数据。 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 页面再次进入时优先显示缓存，后台刷新不抢占当前内容。
      staleTime: 30_000,
      gcTime: 5 * 60_000,
      // 鉴权刷新、幂等重试和业务错误均由统一请求层处理，避免 Query 重复发起同一请求。
      retry: false,
      refetchOnWindowFocus: false,
      refetchOnReconnect: true,
    },
  },
});

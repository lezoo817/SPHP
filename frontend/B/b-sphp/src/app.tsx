// 运行时配置
import { request as umiRequest } from '@umijs/max';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

/**
 * 全局 React Query 客户端。
 * - 默认禁止失败自动重试，避免非幂等操作被重复执行；
 * - 各查询的 staleTime 在 constants/queryKeys.ts 按业务配置。
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: false,
    },
  },
});

/** 请求拦截器接收的请求配置（取用到的字段，其余透传）。 */
interface RequestConfigLike {
  headers?: Record<string, string>;
}

/** 响应拦截器接收的响应体（后端统一 Result<T> 包装）。 */
interface ResponseLike {
  data?: API.Result<unknown>;
}

/** 从 localStorage 读取 token */
function getToken(): string | null {
  return localStorage.getItem('b_access_token');
}

/** 请求配置：注入 Authorization 头 + 处理后端 Result 统一返回格式 */
export const request = {
  requestInterceptors: [
    (url: string, options: RequestConfigLike) => {
      const token = getToken();
      if (token) {
        options.headers = {
          ...options.headers,
          Authorization: `Bearer ${token}`,
        };
      }
      return { url, options };
    },
  ],
  responseInterceptors: [
    (response: ResponseLike) => {
      const { data } = response;
      // 后端统一返回 Result<T>，code !== '00000' 为业务错误
      if (data && data.code !== undefined && data.code !== '00000') {
        const err = new Error(data.message || '系统错误') as Error & {
          code?: string;
        };
        err.code = data.code;
        throw err;
      }
      return response;
    },
  ],
};

/** 全局 Provider：为所有页面提供 React Query 上下文。 */
export function rootContainer(container: ReactNode): ReactNode {
  return (
    <QueryClientProvider client={queryClient}>{container}</QueryClientProvider>
  );
}

/** 全局初始化数据，用于 Layout 用户信息和权限初始化 */
export async function getInitialState(): Promise<{
  currentUser?: API.User;
}> {
  const token = getToken();
  if (!token) return { currentUser: undefined };

  try {
    const res = await umiRequest<{ code: string; data: API.TokenParseVO }>(
      '/api/b/auth/token/parse',
      { timeout: 5000 },
    );
    const data = res?.data;
    if (!data) return { currentUser: undefined };

    return {
      currentUser: {
        id: data.userId,
        name: data.account,
        roles: data.roles ?? [],
        deptId: data.deptId ?? undefined,
        doctorId: data.doctorId ?? undefined,
        hospitalId: data.hospitalId,
      },
    };
  } catch {
    // token 失效，清除本地存储
    localStorage.removeItem('b_access_token');
    return { currentUser: undefined };
  }
}

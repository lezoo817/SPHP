// 运行时配置
import { request as umiRequest } from '@umijs/max';

/** 从 localStorage 读取 token */
function getToken(): string | null {
  return localStorage.getItem('b_access_token');
}

/** 请求配置：注入 Authorization 头 + 处理后端 Result 统一返回格式 */
export const request = {
  requestInterceptors: [
    (url: string, options: any) => {
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
    (response: any) => {
      const { data } = response;
      // 后端统一返回 Result<T>，code !== '00000' 为业务错误
      if (data && data.code !== undefined && data.code !== '00000') {
        const err = new Error(data.message || '系统错误');
        (err as any).code = data.code;
        throw err;
      }
      return response;
    },
  ],
};

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
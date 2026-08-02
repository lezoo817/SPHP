// 运行时配置
import { request } from '@umijs/max';

/** 全局初始化数据，用于 Layout 用户信息和权限初始化 */
export async function getInitialState(): Promise<{
  currentUser?: API.User;
}> {
  try {
    const currentUser = await request<API.User>('/api/b/auth/current-user');
    return { currentUser };
  } catch {
    // 未登录或 token 失效，返回空 initialState
    return { currentUser: undefined };
  }
}

export const layout = () => {
  return {
    logo: 'https://img.alicdn.com/tfs/TB1YHEpwUT1gK0jSZFhXXaAtVXa-28-27.svg',
    title: 'SPHP 医院管理后台',
    menu: {
      locale: false,
    },
  };
};
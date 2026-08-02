import '@umijs/max/typings';

declare namespace API {
  /** 用户信息（从 /api/b/auth/current-user 返回） */
  interface User {
    id?: number;
    username?: string;
    realName?: string;
    avatar?: string;
    roles: string[];
    hospitalId?: number;
    hospitalName?: string;
    deptId?: number;
    deptName?: string;
  }
}
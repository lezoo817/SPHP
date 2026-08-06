/**
 * HTTP 请求层：对 Umi request 做类型化解包封装。
 *
 * 后端统一返回 Result<T>（code/message/data/traceId），非 00000 业务错误
 * 已在 app.ts 响应拦截器统一抛出，此处只需解出 data 字段。
 */
import { request } from '@umijs/max';
import type { RequestOptions } from '@umijs/max';

/** Umi request 的请求配置类型（由 @umijs/max 内置 request 插件提供）。 */
export type { RequestOptions };

/** 从后端 Result<T> 包装中解出业务数据。 */
export function unwrap<T>(res: unknown): T {
  return (res as API.Result<T>).data;
}

/**
 * 发起请求并自动解包业务数据。
 *
 * 适合需要返回体的查询/写操作接口；不关心返回体的写操作直接用 Umi request。
 */
export async function requestData<T>(
  url: string,
  options?: RequestOptions,
): Promise<T> {
  const res = await request(url, options ?? {});
  return unwrap<T>(res);
}

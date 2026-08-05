/**
 * 统一的错误消息提取工具。
 *
 * 后端业务错误由 app.ts 响应拦截器抛出 Error（携带 message/code）；
 * 其它异常可能是字符串 / Error / 任意对象，这里统一提取用户可读的消息。
 */

/** 从任意异常中提取可读消息；无法提取时返回 fallback。 */
export function getErrorMessage(err: unknown, fallback = '操作失败'): string {
  if (err instanceof Error) {
    return err.message || fallback;
  }
  if (typeof err === 'string' && err) {
    return err;
  }
  if (typeof err === 'object' && err !== null && 'message' in err) {
    const msg = (err as { message?: unknown }).message;
    if (typeof msg === 'string' && msg) {
      return msg;
    }
  }
  return fallback;
}

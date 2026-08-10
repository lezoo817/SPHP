/**
 * B 端统一业务返回码常量。
 *
 * 后端所有接口按 {@code Result<T>} 信封返回，code 为业务状态码：
 * '00000' 表示成功，其余为非零业务错误码（错误处理统一在全局拦截器/调用侧按 code 分支）。
 */

/** 统一成功返回码（后端 Result.code == '00000' 视为业务成功）。 */
export const RESULT_CODE_SUCCESS = '00000' as const;

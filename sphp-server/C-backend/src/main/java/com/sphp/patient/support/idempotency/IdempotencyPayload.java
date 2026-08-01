package com.sphp.patient.support.idempotency;

/**
 * 可缓存的 C端接口成功结果。
 *
 * @param message 成功提示
 * @param data 响应业务数据
 * @param <T> 业务数据类型
 */
public record IdempotencyPayload<T>(String message, T data) {
}

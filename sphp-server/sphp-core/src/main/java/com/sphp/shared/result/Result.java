package com.sphp.shared.result;

import com.sphp.shared.filter.TraceIdFilter;
import lombok.Data;
import org.slf4j.MDC;

import java.io.Serializable;

/**
 * 统一 API 返回格式。
 *
 * <p>约定：成功码为字符串 {@code "00000"}，错误码见 B端后端系分 §5.1.2（如 A0301/A0400/B0001）。
 * {@code traceId} 由 {@link TraceIdFilter} 写入 MDC 后自动填充，用于全链路日志追踪。
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> implements Serializable {

    /** 业务返回码：00000 成功，其余为错误码 */
    private String code;
    /** 提示信息 */
    private String message;
    /** 业务数据 */
    private T data;
    /** 全链路追踪号 */
    private String traceId;

    private Result(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
    }

    /** 成功返回（带数据），默认提示「操作成功」 */
    public static <T> Result<T> success(T data) {
        return new Result<>("00000", "操作成功", data);
    }

    /** 成功返回（带数据与自定义提示） */
    public static <T> Result<T> success(String message, T data) {
        return new Result<>("00000", message, data);
    }

    /** 成功返回（无数据） */
    public static <T> Result<T> success() {
        return new Result<>("00000", "操作成功", null);
    }

    /** 失败返回（指定业务码与提示） */
    public static <T> Result<T> error(String code, String message) {
        return new Result<>(code, message, null);
    }

    /** 失败返回（默认系统异常 B0001） */
    public static <T> Result<T> error(String message) {
        return new Result<>("B0001", message, null);
    }
}

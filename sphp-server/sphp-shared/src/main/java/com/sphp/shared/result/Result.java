package com.sphp.shared.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一 API 返回格式。
 *
 * @param <T> 业务数据类型
 */
@Data
public class Result<T> implements Serializable {

    private Integer code;
    private String message;
    private T data;

    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /** 成功返回（带数据） */
    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    /** 成功返回（无数据） */
    public static <T> Result<T> success() {
        return new Result<>(200, "success", null);
    }

    /** 失败返回 */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message, null);
    }

    /** 失败返回（默认 500） */
    public static <T> Result<T> error(String message) {
        return new Result<>(500, message, null);
    }
}

package com.sphp.shared.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * B/C 端共用业务码枚举。
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCodeEnum implements ErrorCode {

    /** 请求执行成功 */
    SUCCESS("00000", "一切正常"),
    /** 账号已存在 */
    ACCOUNT_ALREADY_EXISTS("A0111", "账号已存在"),
    /** 密码校验失败 */
    PASSWORD_VALIDATION_FAILED("A0120", "密码校验失败"),
    /** 账号已停用 */
    ACCOUNT_DISABLED("A0203", "账号已停用"),
    /** 用户登录失败 */
    LOGIN_FAILED("A0210", "用户登录失败"),
    /** 用户输入密码错误次数超限 */
    PASSWORD_RETRY_LIMIT_EXCEEDED("A0211", "用户输入密码错误次数超限"),
    /** 用户登录已过期 */
    LOGIN_EXPIRED("A0230", "用户登录已过期"),
    /** 用户验证码错误 */
    CAPTCHA_ERROR("A0240", "用户验证码错误"),
    /** 用户验证码尝试次数超限 */
    CAPTCHA_RETRY_LIMIT_EXCEEDED("A0241", "用户验证码尝试次数超限"),
    /** 访问未授权 */
    UNAUTHORIZED("A0301", "访问未授权"),
    /** 用户签名异常 */
    USER_SIGNATURE_EXCEPTION("A0341", "用户签名异常"),
    /** 用户请求参数错误 */
    INVALID_PARAMETER("A0400", "用户请求参数错误"),
    /** 无效的用户输入 */
    INVALID_USER_INPUT("A0402", "无效的用户输入"),
    /** 请求参数值超出允许范围 */
    PARAMETER_OUT_OF_RANGE("A0420", "请求参数值超出允许范围"),
    /** 用户输入内容非法 */
    ILLEGAL_INPUT_CONTENT("A0430", "用户输入内容非法"),
    /** 用户支付超时 */
    PAYMENT_TIMEOUT("A0441", "用户支付超时"),
    /** 订单已关闭或状态不可操作 */
    ORDER_CLOSED_OR_STATUS_INVALID("A0443", "订单已关闭或状态不可操作"),
    /** 请求次数超出限制 */
    REQUEST_RATE_LIMITED("A0501", "请求次数超出限制"),
    /** 用户重复请求 */
    DUPLICATE_REQUEST("A0506", "用户重复请求"),
    /** 系统执行出错 */
    SYSTEM_ERROR("B0001", "系统执行出错"),
    /** 系统高并发库存竞争 */
    HIGH_CONCURRENCY_INVENTORY_CONFLICT("B0201", "系统高并发库存竞争"),
    /** 系统业务状态冲突 */
    BUSINESS_STATUS_CONFLICT("B0202", "系统业务状态冲突"),
    /** 系统资源或库存不足 */
    OUT_OF_STOCK("B0300", "系统资源或库存不足"),
    /** 调用第三方服务出错 */
    THIRD_PARTY_SERVICE_ERROR("C0001", "调用第三方服务出错"),
    /** 第三方系统执行超时 */
    THIRD_PARTY_TIMEOUT("C0200", "第三方系统执行超时"),
    /** 通知服务出错 */
    NOTIFICATION_SERVICE_ERROR("C0500", "通知服务出错");

    /** 字符串业务码 */
    private final String code;
    /** 面向调用方的默认中文提示 */
    private final String message;
}

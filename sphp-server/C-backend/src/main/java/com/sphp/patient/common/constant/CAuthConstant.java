package com.sphp.patient.common.constant;

/**
 * C端认证公共常量。
 */
public final class CAuthConstant {

    /** C端访问令牌类型 */
    public static final String ACCESS_TOKEN_TYPE = "C_ACCESS";
    /** Bearer 认证前缀 */
    public static final String BEARER_PREFIX = "Bearer ";
    /** JWT 中账号声明名称 */
    public static final String ACCOUNT_CLAIM = "account";
    /** JWT 中令牌类型声明名称 */
    public static final String TOKEN_TYPE_CLAIM = "tokenType";
    /** JWT 中刷新会话摘要声明名称 */
    public static final String SESSION_HASH_CLAIM = "sessionHash";
    /** 图形验证码 Redis 键前缀 */
    public static final String CAPTCHA_KEY_PREFIX = "cend:captcha:";
    /** 刷新令牌会话 Redis 键前缀 */
    public static final String REFRESH_SESSION_KEY_PREFIX = "cend:refresh:";
    /** 登录失败计数 Redis 键前缀 */
    public static final String LOGIN_FAILURE_KEY_PREFIX = "cend:login:failure:";
    /** 刷新令牌原文前缀 */
    public static final String REFRESH_TOKEN_PREFIX = "rt_";
    /** 图形验证码挑战标识前缀 */
    public static final String CAPTCHA_CHALLENGE_PREFIX = "cap_";

    /**
     * 防止常量类被实例化。
     */
    private CAuthConstant() {
    }
}

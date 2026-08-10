package com.sphp.patient.common.constant;

public class CIdempotencyConstant {

    /** 幂等 Redis 键前缀 */
    public static final String IDEMPOTENCY_KEY_PREFIX = "cend:idempotency:";

    /** 处理中缓存值前缀 */
    public static final String PROCESSING_PREFIX = "PENDING|";
}

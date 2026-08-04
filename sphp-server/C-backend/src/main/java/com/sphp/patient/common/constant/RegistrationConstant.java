package com.sphp.patient.common.constant;

import java.time.ZoneId;

/**
 * C端挂号资源查询公共常量。
 */
public final class RegistrationConstant {

    /** 号源实时余量 Redis 键前缀 */
    public static final String SLOT_REMAIN_KEY_PREFIX = "cend:slot:remain:";

    /** 医生列表默认页码 */
    public static final int DEFAULT_PAGE_NO = 1;

    /** 医生列表默认页大小 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 医生列表最大页大小 */
    public static final int MAX_PAGE_SIZE = 100;

    /** 排班时段组合时间使用的业务时区 */
    public static final ZoneId BUSINESS_ZONE_ID = ZoneId.of("Asia/Shanghai");

    /**
     * 防止常量类被实例化。
     */
    private RegistrationConstant() {
    }
}

package com.sphp.patient.common.constant;

/**
 * C端站内通知业务常量。
 */
public final class NotificationConstant {

    /** 通知列表默认页号 */
    public static final int DEFAULT_PAGE_NO = 1;
    /** 通知列表默认页大小 */
    public static final int DEFAULT_PAGE_SIZE = 20;
    /** 通知列表最大页大小 */
    public static final int MAX_PAGE_SIZE = 100;
    /** 标记通知已读的幂等路径前缀 */
    public static final String READ_PATH_PREFIX = "/c/v1/notifications/";
    /**
     * 防止常量类被实例化。
     */
    private NotificationConstant() {
    }
}

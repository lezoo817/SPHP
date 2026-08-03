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
    /** C端业务 Topic 交换机 */
    public static final String BUSINESS_EXCHANGE = "cend.business.exchange";
    /** C端死信 Topic 交换机 */
    public static final String DLX_EXCHANGE = "cend.dlx.exchange";
    /** 创建站内通知队列 */
    public static final String NOTIFICATION_QUEUE = "cend.notification.queue";
    /** 用药提醒队列 */
    public static final String REMINDER_QUEUE = "cend.reminder.queue";
    /** 随访提醒队列 */
    public static final String FOLLOW_UP_QUEUE = "cend.follow-up.queue";
    /** 消费失败死信队列 */
    public static final String DEAD_LETTER_QUEUE = "cend.dead-letter.queue";
    /** 创建站内通知路由键 */
    public static final String NOTIFICATION_CREATE_ROUTING_KEY = "notification.create";
    /** 用药计划到期提醒路由键 */
    public static final String REMINDER_DUE_ROUTING_KEY = "reminder.due";
    /** 随访计划到期提醒路由键 */
    public static final String FOLLOW_UP_DUE_ROUTING_KEY = "follow-up.due";
    /** 通知消费失败死信路由键 */
    public static final String DEAD_LETTER_ROUTING_KEY = "notification.dead";
    /**
     * 防止常量类被实例化。
     */
    private NotificationConstant() {
    }
}

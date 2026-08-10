package com.sphp.admin.common.constant;

/**
 * B 端在线问诊常量。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
public final class OnlineConsultationConstant {

    /** 在线问诊状态：等待医生回复。 */
    public static final String STATUS_PENDING = "PENDING";

    /** 在线问诊状态：医生正在编辑回复和处方。 */
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    /** 在线问诊状态：医生已完成一次性回复。 */
    public static final String STATUS_COMPLETED = "COMPLETED";

    /** RabbitMQ 业务交换机。 */
    public static final String BUSINESS_EXCHANGE = "cend.business.exchange";

    /** C 端站内通知创建路由键。 */
    public static final String NOTIFICATION_CREATE_ROUTING_KEY = "notification.create";

    /** 医生回复消息发送方类型。 */
    public static final String SENDER_DOCTOR = "DOCTOR";

    /** 医生回复最大字符数。 */
    public static final int MAX_REPLY_LENGTH = 2000;

    private OnlineConsultationConstant() {
    }
}

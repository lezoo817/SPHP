package com.sphp.patient.common.constant;

/**
 * C端问诊与处方查询公共常量。
 */
public final class ConsultationConstant {

    /** 问诊和处方列表默认页码 */
    public static final int DEFAULT_PAGE_NO = 1;

    /** 问诊和处方列表默认页大小 */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** 问诊和处方列表最大页大小 */
    public static final int MAX_PAGE_SIZE = 100;

    /** C端业务消息交换机名称 */
    public static final String BUSINESS_EXCHANGE = "cend.business.exchange";

    /** 问诊消息发送事件路由键 */
    public static final String MESSAGE_SENT_ROUTING_KEY = "consultation.message.sent";


    /**
     * 防止常量类被实例化。
     */
    private ConsultationConstant() {
    }
}

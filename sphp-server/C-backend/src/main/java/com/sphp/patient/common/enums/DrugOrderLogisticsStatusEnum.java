package com.sphp.patient.common.enums;

/**
 * 购药订单物流状态枚举。
 */
public enum DrugOrderLogisticsStatusEnum {

    /** 药房尚未发货 */
    PENDING_SHIPMENT,

    /** 药房已发货 */
    SHIPPED,

    /** 快递运输中 */
    IN_TRANSIT,

    /** 等待患者确认收货 */
    TO_RECEIVE,

    /** 患者已确认收货 */
    RECEIVED
}

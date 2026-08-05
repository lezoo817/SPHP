package com.sphp.patient.order.service;

import com.sphp.patient.order.mq.event.DrugOrderLogisticsAdvanceEvent;

/**
 * C端购药订单模拟物流状态推进服务。
 */
public interface OrderLogisticsService {

    /**
     * 以订单当前物流状态为条件推进一个模拟物流节点。
     *
     * @param event 物流推进事件
     */
    void advanceDrugOrderLogistics(DrugOrderLogisticsAdvanceEvent event);
}

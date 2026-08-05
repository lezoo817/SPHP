package com.sphp.patient.order.service.impl;

import com.sphp.patient.order.mapper.OrderDataMapper;
import com.sphp.patient.order.mq.event.DrugOrderLogisticsAdvanceEvent;
import com.sphp.patient.order.service.OrderLogisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static com.sphp.patient.common.constant.OrderConstant.DRUG_ORDER_IN_TRANSIT_TRACE;
import static com.sphp.patient.common.constant.OrderConstant.DRUG_ORDER_TO_RECEIVE_TRACE;
import static com.sphp.patient.common.enums.DrugOrderLogisticsStatusEnum.IN_TRANSIT;
import static com.sphp.patient.common.enums.DrugOrderLogisticsStatusEnum.PENDING_SHIPMENT;
import static com.sphp.patient.common.enums.DrugOrderLogisticsStatusEnum.TO_RECEIVE;

/**
 * C端购药订单模拟物流状态推进服务实现。
 */
@Service
@RequiredArgsConstructor
public class OrderLogisticsServiceImpl implements OrderLogisticsService {

    /** 购药订单跨表数据访问接口 */
    private final OrderDataMapper orderDataMapper;

    /** Spring 领域事件发布器 */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 在已支付订单上执行一个受限的物流状态推进，并在运输开始后安排下一次推进。
     *
     * @param event 物流推进事件
     * @throws IllegalStateException 物流状态已推进但轨迹写入失败时抛出，以便事务回滚和消息重试
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void advanceDrugOrderLogistics(DrugOrderLogisticsAdvanceEvent event) {
        if (!isSupportedTransition(event)) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        // 条件更新是消息幂等边界，重复或过期消息不会再次推进订单。
        if (orderDataMapper.advanceDrugOrderLogistics(event.drugOrderId(), event.expectedLogisticsStatus().name(),
                event.targetLogisticsStatus().name(), now) != 1) {
            return;
        }
        String node = event.targetLogisticsStatus() == IN_TRANSIT
                ? DRUG_ORDER_IN_TRANSIT_TRACE : DRUG_ORDER_TO_RECEIVE_TRACE;
        // 状态与轨迹必须同一事务提交，避免详情出现无轨迹的物流节点。
        if (orderDataMapper.insertDrugOrderLogisticsTrace(event.drugOrderId(), node, now) != 1) {
            throw new IllegalStateException("购药订单物流轨迹写入失败");
        }
        if (event.targetLogisticsStatus() == IN_TRANSIT) {
            // 事务提交后再投递第二段延迟消息，避免失败事务提前安排待收货状态。
            eventPublisher.publishEvent(DrugOrderLogisticsAdvanceEvent.toReceive(event.drugOrderId()));
        }
    }

    /**
     * 校验消息只能驱动本期允许的两段模拟物流状态机。
     *
     * @param event 物流推进事件
     * @return 状态迁移受支持时返回 true
     */
    private boolean isSupportedTransition(DrugOrderLogisticsAdvanceEvent event) {
        return (event.expectedLogisticsStatus() == PENDING_SHIPMENT && event.targetLogisticsStatus() == IN_TRANSIT)
                || (event.expectedLogisticsStatus() == IN_TRANSIT && event.targetLogisticsStatus() == TO_RECEIVE);
    }
}

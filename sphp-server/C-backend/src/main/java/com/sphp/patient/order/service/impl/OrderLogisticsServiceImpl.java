package com.sphp.patient.order.service.impl;

import com.sphp.patient.order.mapper.OrderDataMapper;
import com.sphp.patient.order.mapper.DrugOrderNotificationTargetRecord;
import com.sphp.patient.order.mq.event.DrugOrderLogisticsAdvanceEvent;
import com.sphp.patient.order.service.OrderLogisticsService;
import com.sphp.patient.notification.mq.producer.NotificationEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static com.sphp.patient.common.constant.OrderConstant.DRUG_ORDER_IN_TRANSIT_TRACE;
import static com.sphp.patient.common.constant.OrderConstant.DRUG_ORDER_TO_RECEIVE_TRACE;
import static com.sphp.patient.common.enums.DrugOrderLogisticsStatusEnum.IN_TRANSIT;
import static com.sphp.patient.common.enums.DrugOrderLogisticsStatusEnum.PENDING_SHIPMENT;
import static com.sphp.patient.common.enums.DrugOrderLogisticsStatusEnum.TO_RECEIVE;
import static com.sphp.patient.common.enums.NotificationTypeEnum.LOGISTICS;

/**
 * C端购药订单模拟物流状态推进服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderLogisticsServiceImpl implements OrderLogisticsService {

    /** 购药订单跨表数据访问接口 */
    private final OrderDataMapper orderDataMapper;

    /** Spring 领域事件发布器 */
    private final ApplicationEventPublisher eventPublisher;

    /** 站内通知事务后事件生产器 */
    private final NotificationEventProducer notificationEventProducer;

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
            // 记录幂等跳过原因，便于区分重复投递、订单已被确认收货或状态已由其他流程推进。
            log.info("购药订单物流状态推进跳过 eventId={}, drugOrderId={}, expectedLogisticsStatus={}, targetLogisticsStatus={}, reason=条件更新未命中",
                    event.eventId(), event.drugOrderId(), event.expectedLogisticsStatus(), event.targetLogisticsStatus());
            return;
        }
        String node = event.targetLogisticsStatus() == IN_TRANSIT
                ? DRUG_ORDER_IN_TRANSIT_TRACE : DRUG_ORDER_TO_RECEIVE_TRACE;
        log.info("购药订单物流状态推进 eventId={}, drugOrderId={}, expectedLogisticsStatus={}, targetLogisticsStatus={}",
                event.eventId(), event.drugOrderId(), event.expectedLogisticsStatus(), event.targetLogisticsStatus());
        // 状态与轨迹必须同一事务提交，避免详情出现无轨迹的物流节点。
        if (orderDataMapper.insertDrugOrderLogisticsTrace(event.drugOrderId(), node, now) != 1) {
            throw new IllegalStateException("购药订单物流轨迹写入失败");
        }
        if (event.targetLogisticsStatus() == IN_TRANSIT) {
            // 事务提交后再投递第二段延迟消息，避免失败事务提前安排待收货状态。
            eventPublisher.publishEvent(DrugOrderLogisticsAdvanceEvent.toReceive(event.drugOrderId()));
            return;
        }
        // 到达待收货状态后才创建提醒，查询目标失败时回滚本次状态推进以避免漏发通知。
        publishToReceiveNotification(event.drugOrderId());
        log.info("购药订单待收货通知创建 drugOrderId={}", event.drugOrderId());
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

    /**
     * 为已送达的购药订单发布待收货站内通知。
     *
     * @param drugOrderId 已完成待收货状态推进的购药订单 ID
     * @throws IllegalStateException 订单通知接收目标缺失时抛出并触发事务回滚
     */
    private void publishToReceiveNotification(Long drugOrderId) {
        DrugOrderNotificationTargetRecord target = orderDataMapper.selectDrugOrderNotificationTarget(drugOrderId);
        if (target == null) {
            throw new IllegalStateException("购药订单通知接收目标不存在");
        }
        // 通知事件在当前事务提交后才进入 RabbitMQ，避免回滚订单产生虚假送达提醒。
        notificationEventProducer.publishNotification("DRUG_ORDER_TO_RECEIVE", target.drugOrderId(),
                target.payerUserId(), target.patientId(), LOGISTICS, "药品已送达", "药品已送达，请及时确认收货。");
        log.info("购药订单待收货通知发布 drugOrderId={}", drugOrderId);
    }
}

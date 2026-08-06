package com.sphp.patient.order.service.impl;

import com.sphp.patient.order.mapper.OrderDataMapper;
import com.sphp.patient.order.mq.event.DrugOrderLogisticsAdvanceEvent;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static com.sphp.patient.common.constant.OrderConstant.DRUG_ORDER_IN_TRANSIT_TRACE;
import static com.sphp.patient.common.constant.OrderConstant.DRUG_ORDER_TO_RECEIVE_TRACE;
import static com.sphp.patient.common.enums.DrugOrderLogisticsStatusEnum.IN_TRANSIT;
import static com.sphp.patient.common.enums.DrugOrderLogisticsStatusEnum.TO_RECEIVE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 购药订单模拟物流状态推进服务测试。
 */
class OrderLogisticsServiceImplTest {

    /**
     * 验证待发货订单会进入运输中、写入轨迹并安排下一段延迟消息。
     */
    @Test
    void advanceToInTransitWritesTraceAndSchedulesNextEvent() {
        OrderDataMapper mapper = mock(OrderDataMapper.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        OrderLogisticsServiceImpl service = new OrderLogisticsServiceImpl(mapper, eventPublisher);
        when(mapper.advanceDrugOrderLogistics(eq(15001L), eq("PENDING_SHIPMENT"), eq("IN_TRANSIT"), any()))
                .thenReturn(1);
        when(mapper.insertDrugOrderLogisticsTrace(eq(15001L), eq(DRUG_ORDER_IN_TRANSIT_TRACE), any()))
                .thenReturn(1);

        service.advanceDrugOrderLogistics(DrugOrderLogisticsAdvanceEvent.toInTransit(15001L));

        verify(mapper).advanceDrugOrderLogistics(eq(15001L), eq("PENDING_SHIPMENT"), eq("IN_TRANSIT"), any());
        verify(mapper).insertDrugOrderLogisticsTrace(eq(15001L), eq(DRUG_ORDER_IN_TRANSIT_TRACE), any());
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.<Object>argThat(event ->
                event instanceof DrugOrderLogisticsAdvanceEvent next
                        && next.expectedLogisticsStatus() == IN_TRANSIT
                        && next.targetLogisticsStatus() == TO_RECEIVE));
    }

    /**
     * 验证运输中订单会进入待收货，且不再安排额外自动流转。
     */
    @Test
    void advanceToReceiveStopsAutomaticFlow() {
        OrderDataMapper mapper = mock(OrderDataMapper.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        OrderLogisticsServiceImpl service = new OrderLogisticsServiceImpl(mapper, eventPublisher);
        when(mapper.advanceDrugOrderLogistics(eq(15001L), eq("IN_TRANSIT"), eq("TO_RECEIVE"), any()))
                .thenReturn(1);
        when(mapper.insertDrugOrderLogisticsTrace(eq(15001L), eq(DRUG_ORDER_TO_RECEIVE_TRACE), any()))
                .thenReturn(1);

        service.advanceDrugOrderLogistics(DrugOrderLogisticsAdvanceEvent.toReceive(15001L));

        verify(mapper).insertDrugOrderLogisticsTrace(eq(15001L), eq(DRUG_ORDER_TO_RECEIVE_TRACE), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    /**
     * 验证重复或过期消息的条件更新未命中时不会重复写入轨迹或投递消息。
     */
    @Test
    void advanceSkipsTraceWhenConditionalUpdateMisses() {
        OrderDataMapper mapper = mock(OrderDataMapper.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        OrderLogisticsServiceImpl service = new OrderLogisticsServiceImpl(mapper, eventPublisher);
        when(mapper.advanceDrugOrderLogistics(eq(15001L), eq("PENDING_SHIPMENT"), eq("IN_TRANSIT"), any()))
                .thenReturn(0);

        service.advanceDrugOrderLogistics(DrugOrderLogisticsAdvanceEvent.toInTransit(15001L));

        verify(mapper, never()).insertDrugOrderLogisticsTrace(any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}

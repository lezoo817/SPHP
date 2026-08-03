package com.sphp.patient.order.service.impl;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.order.entity.DrugOrder;
import com.sphp.patient.order.mapper.DrugOrderItemMapper;
import com.sphp.patient.order.mapper.DrugOrderMapper;
import com.sphp.patient.order.mapper.DrugOrderPaymentMapper;
import com.sphp.patient.order.mapper.OrderDataMapper;
import com.sphp.patient.order.mapper.OrderListRecord;
import com.sphp.patient.order.service.OrderService;
import com.sphp.patient.order.support.OrderStockLockService;
import com.sphp.patient.order.vo.DrugOrderPageVO;
import com.sphp.patient.registration.config.RegistrationProperties;
import com.sphp.patient.notification.mq.producer.NotificationEventProducer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import java.time.OffsetDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/** C端购药订单服务单元测试。 */
class OrderServiceImplTest {
    /** 清理测试线程用户上下文。 */ @AfterEach void clear(){ CUserContext.clear(); }
    /** 验证订单列表使用本人患者和默认分页。 */ @Test void listOrdersUsesDefaultPatientAndPage(){
        OrderDataMapper mapper=mock(OrderDataMapper.class); OrderService service=service(mapper); CUserContext.set(new CUserPrincipal(10001L,"patient", OffsetDateTime.now().plusHours(1),"session"));
        when(mapper.selectOrderSelfPatientId(10001L)).thenReturn(20001L); when(mapper.existsOrderActivePatient(20001L)).thenReturn(true); when(mapper.hasOrderActivePatientRelation(10001L,20001L)).thenReturn(true);
        when(mapper.selectOrderList(20001L,"PENDING_PAYMENT",null,"阿莫西林",20,0)).thenReturn(List.of(new OrderListRecord(15001L,"阿莫西林等 2 种药品","健康药房","PENDING_PAYMENT","PENDING_SHIPMENT",null,7000,OffsetDateTime.now()))); when(mapper.countOrderList(20001L,"PENDING_PAYMENT",null,"阿莫西林")).thenReturn(1L);
        DrugOrderPageVO result=service.listDrugOrders(null,"PENDING_PAYMENT",null," 阿莫西林 ",null,null);
        assertEquals(1L,result.getTotal()); assertEquals(15001L,result.getRecords().getFirst().getId()); assertEquals("阿莫西林等 2 种药品",result.getRecords().getFirst().getOrderName()); assertEquals(20,result.getPageSize());
    }
    /** 创建购药服务测试对象。 */ private OrderService service(OrderDataMapper mapper){ RegistrationProperties properties=new RegistrationProperties(); properties.setPaymentTimeout(900); return new OrderServiceImpl(mapper,mock(DrugOrderMapper.class),mock(DrugOrderItemMapper.class),mock(DrugOrderPaymentMapper.class),mock(OrderStockLockService.class),properties,mock(ApplicationEventPublisher.class),mock(NotificationEventProducer.class)); }
}

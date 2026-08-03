package com.sphp.patient.order.service.impl;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.order.entity.DrugOrder;
import com.sphp.patient.order.dto.DrugOrderCreateRequest;
import com.sphp.patient.order.mapper.DrugOrderItemMapper;
import com.sphp.patient.order.mapper.DrugOrderMapper;
import com.sphp.patient.order.mapper.DrugOrderPaymentMapper;
import com.sphp.patient.order.mapper.OrderDataMapper;
import com.sphp.patient.order.mapper.OrderListRecord;
import com.sphp.patient.order.mapper.OrderPharmacyStockRecord;
import com.sphp.patient.order.mapper.OrderPrescriptionItemRecord;
import com.sphp.patient.order.mapper.OrderPrescriptionRecord;
import com.sphp.patient.order.mapper.OrderStockRecord;
import com.sphp.patient.order.service.DeliveryService;
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
import java.util.function.Supplier;
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
    /** 验证地址簿 ID 下单时使用服务端解析出的不可变地址快照。 */ @Test void createOrderUsesDeliveryAddressSnapshot(){
        OrderDataMapper mapper=mock(OrderDataMapper.class); DrugOrderMapper orderMapper=mock(DrugOrderMapper.class); DrugOrderItemMapper itemMapper=mock(DrugOrderItemMapper.class); DrugOrderPaymentMapper paymentMapper=mock(DrugOrderPaymentMapper.class); OrderStockLockService stockLockService=mock(OrderStockLockService.class); DeliveryService deliveryService=mock(DeliveryService.class);
        RegistrationProperties properties=new RegistrationProperties(); properties.setPaymentTimeout(900); OrderService service=new OrderServiceImpl(mapper,orderMapper,itemMapper,paymentMapper,stockLockService,properties,mock(ApplicationEventPublisher.class),mock(NotificationEventProducer.class),deliveryService);
        CUserContext.set(new CUserPrincipal(10001L,"patient",OffsetDateTime.now().plusHours(1),"session")); DrugOrderCreateRequest request=new DrugOrderCreateRequest(); request.setPrescriptionId(12001L); request.setPharmacyId(14001L); request.setAddressId(30001L);
        when(mapper.selectOrderPrescription(12001L)).thenReturn(new OrderPrescriptionRecord(12001L,20001L,101L,"APPROVED")); when(mapper.selectOrderSelfPatientId(10001L)).thenReturn(20001L); when(mapper.existsOrderActivePatient(20001L)).thenReturn(true); when(mapper.hasOrderActivePatientRelation(10001L,20001L)).thenReturn(true);
        OrderStockRecord stock=new OrderStockRecord(14001L,"院内药房",50001L,"阿莫西林",2,10,1200,null,null,null,null); when(mapper.selectOrderStocks(14001L,12001L)).thenReturn(List.of(stock)); when(mapper.selectOrderPrescriptionItems(12001L)).thenReturn(List.of(new OrderPrescriptionItemRecord(50001L,"阿莫西林",2,null,null,null,null))); when(mapper.selectOrderPharmacyInventory(12001L,101L)).thenReturn(List.of(new OrderPharmacyStockRecord(14001L,"院内药房",101L,true,50001L,10,1200)));
        when(deliveryService.deliveryResolveOrderAddress(30001L,null)).thenReturn("张三 13800138000 河南省郑州市中心路1号"); when(stockLockService.executeWithStockLocks(anyList(),any())).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get()); when(mapper.lockOrderStock(any(),any(),any(),any())).thenReturn(1); when(orderMapper.insert(any(DrugOrder.class))).thenAnswer(invocation -> { invocation.getArgument(0,DrugOrder.class).setId(15001L); return 1; }); when(itemMapper.insert(any(com.sphp.patient.order.entity.DrugOrderItem.class))).thenReturn(1); when(paymentMapper.insert(any(com.sphp.patient.order.entity.DrugOrderPayment.class))).thenAnswer(invocation -> { invocation.getArgument(0,com.sphp.patient.order.entity.DrugOrderPayment.class).setId(80001L); return 1; });
        service.createDrugOrder(request);
        verify(deliveryService).deliveryResolveOrderAddress(30001L,null); verify(orderMapper).insert(org.mockito.ArgumentMatchers.<DrugOrder>argThat(order -> "张三 13800138000 河南省郑州市中心路1号".equals(order.getDeliveryAddress())));
    }
    /** 创建购药服务测试对象。 */ private OrderService service(OrderDataMapper mapper){ RegistrationProperties properties=new RegistrationProperties(); properties.setPaymentTimeout(900); return new OrderServiceImpl(mapper,mock(DrugOrderMapper.class),mock(DrugOrderItemMapper.class),mock(DrugOrderPaymentMapper.class),mock(OrderStockLockService.class),properties,mock(ApplicationEventPublisher.class),mock(NotificationEventProducer.class),mock(DeliveryService.class)); }
}

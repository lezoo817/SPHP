package com.sphp.patient.order.controller;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.order.handler.OrderExceptionHandler;
import com.sphp.patient.order.service.OrderService;
import com.sphp.patient.order.service.PaymentService;
import com.sphp.patient.order.vo.DrugOrderCancelVO;
import com.sphp.patient.order.vo.DrugOrderCreateVO;
import com.sphp.patient.order.vo.DrugOrderDetailVO;
import com.sphp.patient.order.vo.DrugOrderPageVO;
import com.sphp.patient.order.vo.DrugOrderReceiptVO;
import com.sphp.patient.order.vo.PharmacyInventoryVO;
import com.sphp.patient.registration.vo.RegisteringPaymentSuccessVO;
import com.sphp.patient.support.idempotency.CIdempotencyService;
import com.sphp.patient.support.idempotency.IdempotencyPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.OffsetDateTime;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** C端购药订单控制器测试。 */
class OrderControllerTest {
    /** 清理当前测试线程用户上下文。 */ @AfterEach void clearContext(){ CUserContext.clear(); }
    /** 验证药房库存查询路由。 */ @Test void listInventory() throws Exception {
        OrderService service=mock(OrderService.class); CIdempotencyService idem=mock(CIdempotencyService.class);
        when(service.listPharmacyInventory(null,12001L)).thenReturn(List.of(PharmacyInventoryVO.builder().pharmacyId(14001L).name("健康药房").hospitalId(101L).isDefault(true).deliveryMethod("COURIER").items(List.of()).build()));
        mvc(service,mock(PaymentService.class),idem).perform(get("/c/v1/pharmacies/inventory").param("prescriptionId","12001")).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].pharmacyId").value(14001)); }
    /** 验证创建购药订单走幂等返回。 */ @Test void createOrder() throws Exception { OrderService s=mock(OrderService.class); CIdempotencyService i=mock(CIdempotencyService.class); context(); when(i.execute(any(),anyString(),anyString(),any(),any(),any())).thenReturn(new IdempotencyPayload<>("购药订单已创建，请在15分钟内完成支付",DrugOrderCreateVO.builder().drugOrderId(15001L).status("PENDING_PAYMENT").amountCent(7000).paymentId(8002L).items(List.of()).build())); mvc(s,mock(PaymentService.class),i).perform(post("/c/v1/drug-orders").header("X-Idempotency-Key","order-1").contentType(MediaType.APPLICATION_JSON).content("{\"prescriptionId\":12001,\"pharmacyId\":14001,\"deliveryAddress\":\"测试地址\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.drugOrderId").value(15001)); }
    /** 验证购药订单列表路由。 */ @Test void listOrders() throws Exception { OrderService s=mock(OrderService.class); when(s.listDrugOrders(null,null,null,null,null)).thenReturn(DrugOrderPageVO.builder().pageNo(1).pageSize(20).total(0).records(List.of()).build()); mvc(s,mock(PaymentService.class),mock(CIdempotencyService.class)).perform(get("/c/v1/drug-orders")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0)); }
    /** 验证购药订单详情路由。 */ @Test void orderDetail() throws Exception { OrderService s=mock(OrderService.class); when(s.getDrugOrderDetail(15001L)).thenReturn(DrugOrderDetailVO.builder().id(15001L).status("PENDING_PAYMENT").items(List.of()).build()); mvc(s,mock(PaymentService.class),mock(CIdempotencyService.class)).perform(get("/c/v1/drug-orders/15001")).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(15001)); }
    /** 验证取消购药订单路由。 */ @Test void cancelOrder() throws Exception { OrderService s=mock(OrderService.class); CIdempotencyService i=mock(CIdempotencyService.class); context(); when(i.execute(any(),anyString(),anyString(),any(),any(),any())).thenReturn(new IdempotencyPayload<>("购药订单已取消",DrugOrderCancelVO.builder().drugOrderId(15001L).status("CANCELLED").cancelledAt(OffsetDateTime.now()).build())); mvc(s,mock(PaymentService.class),i).perform(post("/c/v1/drug-orders/15001/cancel").header("X-Idempotency-Key","cancel-1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED")); }
    /** 验证确认收货路由。 */ @Test void receipt() throws Exception { OrderService s=mock(OrderService.class); CIdempotencyService i=mock(CIdempotencyService.class); context(); when(i.execute(any(),anyString(),anyString(),any(),any(),any())).thenReturn(new IdempotencyPayload<>("确认收货成功",DrugOrderReceiptVO.builder().drugOrderId(15001L).logisticsStatus("RECEIVED").receivedAt(OffsetDateTime.now()).build())); mvc(s,mock(PaymentService.class),i).perform(post("/c/v1/drug-orders/15001/confirm-receipt").header("X-Idempotency-Key","receipt-1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.logisticsStatus").value("RECEIVED")); }
    /** 验证统一支付路由。 */ @Test void payment() throws Exception { OrderService s=mock(OrderService.class); PaymentService p=mock(PaymentService.class); CIdempotencyService i=mock(CIdempotencyService.class); context(); when(i.execute(any(),anyString(),anyString(),any(),any(),any())).thenReturn(new IdempotencyPayload<>("支付成功",RegisteringPaymentSuccessVO.builder().paymentId(8002L).status("SUCCESS").paidAt(OffsetDateTime.now()).build())); mvc(s,p,i).perform(post("/c/v1/payments/8002/simulate-pay").header("X-Idempotency-Key","pay-1").contentType(MediaType.APPLICATION_JSON).content("{\"loginPassword\":\"P@ssw0rd123\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUCCESS")); }
    /** 创建订单和支付接口测试环境。 */ private MockMvc mvc(OrderService s,PaymentService p,CIdempotencyService i){ return MockMvcBuilders.standaloneSetup(new OrderController(s,i),new PaymentController(p,i)).setControllerAdvice(new OrderExceptionHandler()).build(); }
    /** 建立当前用户上下文。 */ private void context(){ CUserContext.set(new CUserPrincipal(10001L,"patient",OffsetDateTime.now().plusHours(1),"session")); }
}

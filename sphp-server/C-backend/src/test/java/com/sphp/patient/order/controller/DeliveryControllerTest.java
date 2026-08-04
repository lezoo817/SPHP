package com.sphp.patient.order.controller;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.order.handler.DeliveryExceptionHandler;
import com.sphp.patient.order.service.DeliveryService;
import com.sphp.patient.order.vo.DeliveryAddressDeleteVO;
import com.sphp.patient.order.vo.DeliveryAddressVO;
import com.sphp.patient.order.vo.DeliveryPharmacyRecommendationVO;
import com.sphp.patient.support.idempotency.CIdempotencyService;
import com.sphp.patient.support.idempotency.IdempotencyPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C端收货地址控制器测试。
 */
class DeliveryControllerTest {

    /** 清理测试线程用户上下文。 */
    @AfterEach
    void clearContext() {
        CUserContext.clear();
    }

    /** 验证当前账号地址列表路由和默认地址响应。 */
    @Test
    void deliveryListAddressesReturnsCurrentUserAddresses() throws Exception {
        DeliveryService service = mock(DeliveryService.class);
        when(service.deliveryListAddresses()).thenReturn(List.of(address()));

        mvc(service, mock(CIdempotencyService.class)).perform(get("/c/v1/delivery-addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data[0].province").value("HENAN"))
                .andExpect(jsonPath("$.data[0].isDefault").value(true));
    }

    /** 验证药房推荐路由传递地址、处方和排序参数。 */
    @Test
    void deliveryRecommendPharmaciesReturnsSimulationFields() throws Exception {
        DeliveryService service = mock(DeliveryService.class);
        when(service.deliveryRecommendPharmacies(null, 12001L, 30001L, "RECOMMENDED"))
                .thenReturn(List.of(DeliveryPharmacyRecommendationVO.builder().pharmacyId(14001L).name("院内药房")
                        .distanceMeters(18_000L).estimatedDeliveryMinutes(930).totalAmountCent(7000).items(List.of()).build()));

        mvc(service, mock(CIdempotencyService.class)).perform(get("/c/v1/pharmacies/recommendations")
                        .param("prescriptionId", "12001").param("addressId", "30001").param("sort", "RECOMMENDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].distanceMeters").value(18_000))
                .andExpect(jsonPath("$.data[0].estimatedDeliveryMinutes").value(930));
    }

    /** 验证推荐接口缺少必填处方参数时返回统一参数错误。 */
    @Test
    void deliveryRecommendPharmaciesRejectsMissingPrescriptionId() throws Exception {
        mvc(mock(DeliveryService.class), mock(CIdempotencyService.class)).perform(get("/c/v1/pharmacies/recommendations")
                        .param("addressId", "30001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0400"));
    }

    /** 验证新增地址经幂等服务返回新增结果。 */
    @Test
    void deliveryCreateAddressReturnsCreatedAddress() throws Exception {
        CIdempotencyService idempotencyService = mock(CIdempotencyService.class);
        context();
        when(idempotencyService.execute(any(), any(), any(), any(), any(), any()))
                .thenReturn(new IdempotencyPayload<>("收货地址已新增", address()));

        mvc(mock(DeliveryService.class), idempotencyService).perform(post("/c/v1/delivery-addresses")
                        .header("X-Idempotency-Key", "delivery-create-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverName\":\"张三\",\"receiverPhone\":\"13800138000\",\"province\":\"HENAN\",\"city\":\"郑州市\",\"detailAddress\":\"中心路1号\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(30001L));
    }

    /** 验证更新、删除与默认地址路由均要求幂等键并返回统一结果。 */
    @Test
    void deliveryMutationsReturnExpectedEnvelope() throws Exception {
        CIdempotencyService idempotencyService = mock(CIdempotencyService.class);
        context();
        when(idempotencyService.execute(any(), any(), any(), any(), any(), any()))
                .thenReturn(new IdempotencyPayload<>("收货地址已更新", address()))
                .thenReturn(new IdempotencyPayload<>("默认收货地址已设置", address()))
                .thenReturn(new IdempotencyPayload<>("收货地址已删除", DeliveryAddressDeleteVO.builder().id(30001L).deletedAt(OffsetDateTime.now()).build()));
        MockMvc mockMvc = mvc(mock(DeliveryService.class), idempotencyService);

        mockMvc.perform(put("/c/v1/delivery-addresses/30001").header("X-Idempotency-Key", "delivery-update-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverName\":\"张三\",\"receiverPhone\":\"13800138000\",\"province\":\"HENAN\",\"city\":\"郑州市\",\"detailAddress\":\"中心路2号\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(30001L));
        mockMvc.perform(post("/c/v1/delivery-addresses/30001/default").header("X-Idempotency-Key", "delivery-default-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.isDefault").value(true));
        mockMvc.perform(delete("/c/v1/delivery-addresses/30001").header("X-Idempotency-Key", "delivery-delete-1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(30001L));
    }

    /** 验证写地址请求缺少幂等键时返回参数错误。 */
    @Test
    void deliveryCreateAddressRejectsMissingIdempotencyKey() throws Exception {
        mvc(mock(DeliveryService.class), mock(CIdempotencyService.class)).perform(post("/c/v1/delivery-addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverName\":\"张三\",\"receiverPhone\":\"13800138000\",\"province\":\"HENAN\",\"city\":\"郑州市\",\"detailAddress\":\"中心路1号\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("A0400"));
    }

    /** 创建地址返回对象。 */
    private DeliveryAddressVO address() {
        return DeliveryAddressVO.builder().id(30001L).receiverName("张三").receiverPhone("13800138000")
                .province("HENAN").provinceName("河南省").city("郑州市").detailAddress("中心路1号").isDefault(true).build();
    }

    /** 创建地址控制器测试环境。 */
    private MockMvc mvc(DeliveryService service, CIdempotencyService idempotencyService) {
        return MockMvcBuilders.standaloneSetup(new DeliveryController(service, idempotencyService))
                .setControllerAdvice(new DeliveryExceptionHandler()).build();
    }

    /** 设置当前登录用户上下文。 */
    private void context() {
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
    }
}

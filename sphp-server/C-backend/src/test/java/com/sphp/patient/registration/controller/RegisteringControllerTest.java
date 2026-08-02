package com.sphp.patient.registration.controller;

import com.sphp.patient.registration.dto.RegisteringAppointmentCreateRequest;
import com.sphp.patient.registration.handler.RegisteringExceptionHandler;
import com.sphp.patient.registration.service.RegisteringService;
import com.sphp.patient.registration.vo.RegisteringAppointmentCreateVO;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.support.idempotency.CIdempotencyService;
import com.sphp.patient.support.idempotency.IdempotencyPayload;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C端挂号与支付控制器接口测试。
 */
class RegisteringControllerTest {

    /**
     * 验证创建挂号锁定订单的路由、幂等请求头和统一成功响应。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void registeringCreateAppointmentReturnsLockResult() throws Exception {
        RegisteringService registeringService = mock(RegisteringService.class);
        CIdempotencyService idempotencyService = mock(CIdempotencyService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RegisteringController(registeringService, idempotencyService))
                .setControllerAdvice(new RegisteringExceptionHandler())
                .build();
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
        when(registeringService.registeringCreateAppointment(any(RegisteringAppointmentCreateRequest.class)))
                .thenReturn(RegisteringAppointmentCreateVO.builder()
                        .appointmentId(7001L)
                        .status("UNPAID")
                        .amountCent(5000)
                        .expireAt(OffsetDateTime.parse("2026-08-02T10:15:00+08:00"))
                        .paymentId(8001L)
                        .build());

        when(idempotencyService.execute(any(), anyString(), anyString(), any(), any(), any()))
                .thenAnswer(invocation -> new IdempotencyPayload<>("号源锁定成功，请在15分钟内完成支付",
                        registeringService.registeringCreateAppointment(invocation.getArgument(3))));

        try {
            mockMvc.perform(post("/c/v1/appointments")
                        .header("X-Idempotency-Key", "appointment-create-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientId\":20001,\"hospitalId\":101,\"slotId\":501}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.appointmentId").value(7001))
                .andExpect(jsonPath("$.data.status").value("UNPAID"))
                .andExpect(jsonPath("$.data.paymentId").value(8001));
        } finally {
            CUserContext.clear();
        }
    }
}

package com.sphp.patient.registration.controller;

import com.sphp.patient.registration.dto.RegisteringAppointmentCreateRequest;
import com.sphp.patient.registration.handler.RegisteringExceptionHandler;
import com.sphp.patient.registration.service.RegisteringService;
import com.sphp.patient.registration.vo.RegisteringAppointmentCreateVO;
import com.sphp.patient.registration.vo.RegisteringAppointmentDetailVO;
import com.sphp.patient.registration.vo.RegisteringAppointmentListVO;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.support.idempotency.CIdempotencyService;
import com.sphp.patient.support.idempotency.IdempotencyPayload;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C端挂号与支付控制器接口测试。
 */
class RegisteringControllerTest {

    /**
     * 验证挂号订单列表返回当前科室位置。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void registeringListAppointmentsReturnsDepartmentLocation() throws Exception {
        RegisteringService registeringService = mock(RegisteringService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RegisteringController(registeringService, mock(CIdempotencyService.class)))
                .setControllerAdvice(new RegisteringExceptionHandler())
                .build();
        // 控制器将未传入的分页参数原样交给服务层，默认值由服务层统一处理。
        when(registeringService.registeringListAppointments(20001L, null, null, null))
                .thenReturn(RegisteringAppointmentListVO.builder()
                        .pageNo(1).pageSize(20).total(1)
                        .records(List.of(RegisteringAppointmentListVO.Item.builder()
                                .id(7001L).doctorName("张医生").departmentName("呼吸内科")
                                .departmentLocation("门诊楼3层A区").startTime(OffsetDateTime.parse("2026-08-03T08:00:00+08:00"))
                                .status("PAID").amountCent(5000).build()))
                        .build());

        mockMvc.perform(get("/c/v1/appointments").param("patientId", "20001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].departmentLocation").value("门诊楼3层A区"));
    }

    /**
     * 验证挂号订单详情在医生资料中返回当前科室位置。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void registeringGetAppointmentReturnsDepartmentLocation() throws Exception {
        RegisteringService registeringService = mock(RegisteringService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RegisteringController(registeringService, mock(CIdempotencyService.class)))
                .setControllerAdvice(new RegisteringExceptionHandler())
                .build();
        when(registeringService.registeringGetAppointment(7001L)).thenReturn(RegisteringAppointmentDetailVO.builder()
                .id(7001L).status("PAID")
                .doctor(RegisteringAppointmentDetailVO.Doctor.builder().id(401L).name("张医生")
                        .departmentName("呼吸内科").departmentLocation("门诊楼3层A区").build())
                .amountCent(5000).build());

        mockMvc.perform(get("/c/v1/appointments/7001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.doctor.departmentLocation").value("门诊楼3层A区"));
    }

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

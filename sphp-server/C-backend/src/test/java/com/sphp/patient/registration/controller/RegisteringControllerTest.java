package com.sphp.patient.registration.controller;

import com.sphp.patient.registration.dto.RegisteringAppointmentCreateRequest;
import com.sphp.patient.registration.dto.RegisteringAppointmentCancelRequest;
import com.sphp.patient.registration.handler.RegisteringExceptionHandler;
import com.sphp.patient.registration.service.RegisteringService;
import com.sphp.patient.registration.vo.RegisteringAppointmentCreateVO;
import com.sphp.patient.registration.vo.RegisteringAppointmentDetailVO;
import com.sphp.patient.registration.vo.RegisteringAppointmentListVO;
import com.sphp.patient.registration.vo.RegisteringAppointmentCancelVO;
import com.sphp.patient.registration.vo.RegisteringDoctorBookingStatusVO;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
     * 验证未支付挂号取消继续兼容无请求体调用。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void registeringCancelUnpaidAppointmentAllowsEmptyBody() throws Exception {
        RegisteringService service = mock(RegisteringService.class);
        CIdempotencyService idempotencyService = mock(CIdempotencyService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RegisteringController(service, idempotencyService))
                .setControllerAdvice(new RegisteringExceptionHandler()).build();
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
        when(service.registeringCancelAppointment(7001L, null)).thenReturn(RegisteringAppointmentCancelVO.builder()
                .appointmentId(7001L).status("CANCELLED").build());
        when(idempotencyService.execute(any(), anyString(), anyString(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.<java.util.function.Supplier<IdempotencyPayload<RegisteringAppointmentCancelVO>>>
                        getArgument(5).get());

        try {
            mockMvc.perform(post("/c/v1/appointments/7001/cancel").header("X-Idempotency-Key", "cancel-unpaid-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"));
            verify(service).registeringCancelAppointment(7001L, null);
        } finally {
            CUserContext.clear();
        }
    }

    /**
     * 验证已支付挂号取消会透传登录密码给服务层。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void registeringCancelPaidAppointmentPassesLoginPassword() throws Exception {
        RegisteringService service = mock(RegisteringService.class);
        CIdempotencyService idempotencyService = mock(CIdempotencyService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RegisteringController(service, idempotencyService))
                .setControllerAdvice(new RegisteringExceptionHandler()).build();
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
        when(service.registeringCancelAppointment(eq(7001L), any(RegisteringAppointmentCancelRequest.class)))
                .thenReturn(RegisteringAppointmentCancelVO.builder().appointmentId(7001L).status("CANCELLED").build());
        when(idempotencyService.execute(any(), anyString(), anyString(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.<java.util.function.Supplier<IdempotencyPayload<RegisteringAppointmentCancelVO>>>
                        getArgument(5).get());

        try {
            mockMvc.perform(post("/c/v1/appointments/7001/cancel")
                            .header("X-Idempotency-Key", "cancel-paid-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"loginPassword\":\"P@ssw0rd123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"));
            verify(service).registeringCancelAppointment(eq(7001L),
                    argThat(request -> "P@ssw0rd123".equals(request.getLoginPassword())));
        } finally {
            CUserContext.clear();
        }
    }

    /**
     * 验证医生主页可按指定就诊人查询重复预约状态。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void registeringGetDoctorBookingStatusReturnsPatientLevelBookedFlag() throws Exception {
        RegisteringService registeringService = mock(RegisteringService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RegisteringController(registeringService, mock(CIdempotencyService.class)))
                .setControllerAdvice(new RegisteringExceptionHandler())
                .build();
        when(registeringService.registeringGetDoctorBookingStatus(401L, 20001L))
                .thenReturn(RegisteringDoctorBookingStatusVO.builder().doctorId(401L).booked(true).build());

        mockMvc.perform(get("/c/v1/appointments/doctor-booking-status")
                        .param("doctorId", "401")
                        .param("patientId", "20001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.doctorId").value(401))
                .andExpect(jsonPath("$.data.booked").value(true));
    }

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
                                .endTime(OffsetDateTime.parse("2026-08-03T08:30:00+08:00"))
                                .status("PAID").amountCent(5000).build()))
                        .build());

        mockMvc.perform(get("/c/v1/appointments").param("patientId", "20001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].departmentLocation").value("门诊楼3层A区"))
                .andExpect(jsonPath("$.data.records[0].endTime").isNumber());
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

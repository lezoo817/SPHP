package com.sphp.patient.consultation.controller;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.consultation.dto.PreConsultationSaveRequest;
import com.sphp.patient.consultation.handler.ConsultationExceptionHandler;
import com.sphp.patient.consultation.service.ConsultationService;
import com.sphp.patient.consultation.vo.PreConsultationSaveVO;
import com.sphp.patient.consultation.vo.ConsultationPageVO;
import com.sphp.patient.support.idempotency.CIdempotencyService;
import com.sphp.patient.support.idempotency.IdempotencyPayload;
import org.junit.jupiter.api.AfterEach;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C端问诊控制器接口测试。
 */
class ConsultationControllerTest {

    /**
     * 每个测试结束后清理线程用户上下文。
     */
    @AfterEach
    void clearContext() {
        CUserContext.clear();
    }

    /**
     * 验证提交预问诊走幂等处理并返回保存与提交时间。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void savePreConsultationReturnsIdempotentSubmitResult() throws Exception {
        ConsultationService consultationService = mock(ConsultationService.class);
        CIdempotencyService idempotencyService = mock(CIdempotencyService.class);
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
        PreConsultationSaveVO result = PreConsultationSaveVO.builder()
                .consultationId(11001L)
                .status("PENDING")
                .savedAt(OffsetDateTime.parse("2026-08-02T10:00:00+08:00"))
                .submittedAt(OffsetDateTime.parse("2026-08-02T10:00:00+08:00"))
                .build();
        when(idempotencyService.execute(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(new IdempotencyPayload<>("预问诊已提交", result));

        newMockMvc(consultationService, idempotencyService)
                .perform(post("/c/v1/consultations/pre-consultations")
                        .header("X-Idempotency-Key", "pre-consultation-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appointmentId\":7001,\"chiefComplaint\":\"咳嗽发热三天\",\"submit\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.message").value("预问诊已提交"))
                .andExpect(jsonPath("$.data.consultationId").value(11001))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.submittedAt").exists());
    }

    /**
     * 验证状态变更接口缺少幂等键时返回统一参数错误。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void savePreConsultationRejectsMissingIdempotencyKey() throws Exception {
        ConsultationService consultationService = mock(ConsultationService.class);
        CIdempotencyService idempotencyService = mock(CIdempotencyService.class);

        newMockMvc(consultationService, idempotencyService)
                .perform(post("/c/v1/consultations/pre-consultations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appointmentId\":7001,\"chiefComplaint\":\"咳嗽\",\"submit\":false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0400"));
    }

    /**
     * 验证问诊列表路由返回分页响应。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void listConsultationsReturnsPageResult() throws Exception {
        ConsultationService consultationService = mock(ConsultationService.class);
        CIdempotencyService idempotencyService = mock(CIdempotencyService.class);
        when(consultationService.listConsultations(20001L, "PENDING", 1, 20))
                .thenReturn(ConsultationPageVO.builder().pageNo(1).pageSize(20).total(1)
                        .records(java.util.List.of(ConsultationPageVO.Item.builder().id(11001L)
                                .appointmentId(7001L).doctorName("王医生").status("PENDING")
                                .updatedAt(OffsetDateTime.parse("2026-08-02T10:00:00+08:00")).build()))
                        .build());

        newMockMvc(consultationService, idempotencyService)
                .perform(get("/c/v1/consultations").param("patientId", "20001")
                        .param("status", "PENDING").param("pageNo", "1").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(11001))
                .andExpect(jsonPath("$.data.records[0].status").value("PENDING"));
    }

    /**
     * 创建问诊控制器测试环境。
     *
     * @param consultationService 问诊服务模拟对象
     * @param idempotencyService 幂等服务模拟对象
     * @return MockMvc 测试对象
     */
    private MockMvc newMockMvc(ConsultationService consultationService, CIdempotencyService idempotencyService) {
        return MockMvcBuilders.standaloneSetup(new ConsultationController(consultationService, idempotencyService))
                .setControllerAdvice(new ConsultationExceptionHandler())
                .build();
    }
}

package com.sphp.patient.triage.controller;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.support.idempotency.CIdempotencyService;
import com.sphp.patient.support.idempotency.IdempotencyPayload;
import com.sphp.patient.triage.handler.TriageExceptionHandler;
import com.sphp.patient.triage.service.TriageService;
import com.sphp.patient.triage.vo.TriageAssessmentVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 导诊控制器接口测试。
 */
class TriageControllerTest {

    /**
     * 每个测试结束后清理当前 C端用户上下文。
     */
    @AfterEach
    void clearContext() {
        CUserContext.clear();
    }

    /**
     * 验证导诊请求通过幂等服务返回推荐结果。
     *
     * @throws Exception MockMvc 执行失败时抛出
     */
    @Test
    void triageCreateAssessmentReturnsIdempotentResult() throws Exception {
        TriageService service = mock(TriageService.class);
        CIdempotencyService idempotencyService = mock(CIdempotencyService.class);
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
        TriageAssessmentVO result = TriageAssessmentVO.builder()
                .assessmentId(601L)
                .urgency("MEDIUM")
                .recommendedDepartments(List.of(TriageAssessmentVO.RecommendedDepartment.builder()
                        .id(301L).name("呼吸内科").reason("症状与呼吸系统相关").build()))
                .disclaimer("本结果仅供健康咨询参考，不替代医生诊断。")
                .build();
        when(idempotencyService.execute(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(new IdempotencyPayload<>("导诊评估完成", result));

        newMockMvc(service, idempotencyService).perform(post("/c/v1/triage/assessments")
                        .header("X-Idempotency-Key", "triage-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hospitalId\":101,\"symptom\":\"咳嗽发热三天\",\"temperature\":38.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.message").value("导诊评估完成"))
                .andExpect(jsonPath("$.data.assessmentId").value(601L))
                .andExpect(jsonPath("$.data.recommendedDepartments[0].id").value(301L));
    }

    /**
     * 验证导诊写接口缺少幂等键时返回参数错误。
     *
     * @throws Exception MockMvc 执行失败时抛出
     */
    @Test
    void triageCreateAssessmentRejectsMissingIdempotencyKey() throws Exception {
        newMockMvc(mock(TriageService.class), mock(CIdempotencyService.class))
                .perform(post("/c/v1/triage/assessments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hospitalId\":101,\"symptom\":\"咳嗽\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0400"));
    }

    /**
     * 验证空症状按非法内容返回 A0430。
     *
     * @throws Exception MockMvc 执行失败时抛出
     */
    @Test
    void triageCreateAssessmentRejectsEmptySymptom() throws Exception {
        newMockMvc(mock(TriageService.class), mock(CIdempotencyService.class))
                .perform(post("/c/v1/triage/assessments")
                        .header("X-Idempotency-Key", "triage-invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hospitalId\":101,\"symptom\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0430"));
    }

    /**
     * 创建导诊控制器独立测试环境。
     *
     * @param service 导诊服务模拟对象
     * @param idempotencyService 幂等服务模拟对象
     * @return MockMvc 测试对象
     */
    private MockMvc newMockMvc(TriageService service, CIdempotencyService idempotencyService) {
        return MockMvcBuilders.standaloneSetup(new TriageController(service, idempotencyService))
                .setControllerAdvice(new TriageExceptionHandler())
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(
                        new ObjectMapper()))
                .build();
    }
}

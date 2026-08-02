package com.sphp.patient.health.controller;

import com.sphp.patient.health.handler.HealthExceptionHandler;
import com.sphp.patient.health.service.HealthService;
import com.sphp.patient.health.dto.AllergyCreateRequest;
import com.sphp.patient.health.dto.AllergyUpdateRequest;
import com.sphp.patient.health.vo.AllergyCreateVO;
import com.sphp.patient.health.vo.AllergyUpdateVO;
import com.sphp.patient.health.vo.HealthProfileVO;
import com.sphp.patient.health.vo.HealthRecordVO;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.support.idempotency.CIdempotencyService;
import com.sphp.patient.support.idempotency.IdempotencyPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.time.OffsetDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * 健康档案控制器接口测试。
 */
class HealthControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 每个测试结束后清理当前 C端用户上下文。
     */
    @AfterEach
    void clearContext() {
        CUserContext.clear();
    }

    /**
     * 验证健康档案查询接口返回统一响应和脱敏患者资料。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void getHealthRecordReturnsExpectedEnvelope() throws Exception {
        HealthService healthService = mock(HealthService.class);
        CIdempotencyService idempotencyService = mock(CIdempotencyService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HealthController(healthService, idempotencyService))
                .setControllerAdvice(new HealthExceptionHandler())
                .build();
        when(healthService.getHealthRecord(20001L)).thenReturn(HealthRecordVO.builder()
                .profile(HealthProfileVO.builder().id(20001L).name("张三").gender("MALE").build())
                .allergies(List.of())
                .medicalHistories(List.of())
                .summary("已记录0项过敏史和0项既往史")
                .build());

        mockMvc.perform(get("/c/v1/health-record").param("patientId", "20001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.message").value("查询成功"))
                .andExpect(jsonPath("$.data.profile.id").value(20001L))
                .andExpect(jsonPath("$.data.profile.name").value("张三"))
                .andExpect(jsonPath("$.data.profile.phone").doesNotExist());
    }

    /**
     * 验证新增过敏史接口通过幂等服务返回首次成功结果。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void createAllergyReturnsIdempotentSuccessResult() throws Exception {
        HealthService healthService = mock(HealthService.class);
        CIdempotencyService idempotencyService = mock(CIdempotencyService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HealthController(healthService, idempotencyService))
                .setControllerAdvice(new HealthExceptionHandler())
                .build();
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        AllergyCreateRequest request = new AllergyCreateRequest();
        request.setAllergen("青霉素");
        request.setReaction("皮疹");
        when(idempotencyService.execute(any(), any(), any(), any(), any(), any()))
                .thenReturn(new IdempotencyPayload<>("过敏史已保存", AllergyCreateVO.builder()
                        .id(16001L).allergen("青霉素").reaction("皮疹").build()));

        mockMvc.perform(post("/c/v1/health-record/allergies")
                        .header("X-Idempotency-Key", "allergy-key-001")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.message").value("过敏史已保存"))
                .andExpect(jsonPath("$.data.id").value(16001L));
    }

    /**
     * 验证更新过敏史接口通过幂等服务返回更新结果。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void updateAllergyReturnsIdempotentSuccessResult() throws Exception {
        HealthService healthService = mock(HealthService.class);
        CIdempotencyService idempotencyService = mock(CIdempotencyService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HealthController(healthService, idempotencyService))
                .setControllerAdvice(new HealthExceptionHandler())
                .build();
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        AllergyUpdateRequest request = new AllergyUpdateRequest();
        request.setAllergen("阿莫西林");
        when(idempotencyService.execute(any(), any(), any(), any(), any(), any()))
                .thenReturn(new IdempotencyPayload<>("过敏史已更新", AllergyUpdateVO.builder()
                        .id(16001L).allergen("阿莫西林").reaction("皮疹")
                        .updatedAt(OffsetDateTime.now()).build()));

        mockMvc.perform(put("/c/v1/health-record/allergies/16001")
                        .header("X-Idempotency-Key", "allergy-key-002")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("过敏史已更新"))
                .andExpect(jsonPath("$.data.allergen").value("阿莫西林"));
    }
}

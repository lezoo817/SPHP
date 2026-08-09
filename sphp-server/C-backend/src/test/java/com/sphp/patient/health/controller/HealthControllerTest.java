package com.sphp.patient.health.controller;

import com.sphp.patient.health.handler.HealthExceptionHandler;
import com.sphp.patient.health.service.HealthService;
import com.sphp.patient.health.dto.AllergyCreateRequest;
import com.sphp.patient.health.dto.AllergyUpdateRequest;
import com.sphp.patient.health.dto.MedicalHistoryCreateRequest;
import com.sphp.patient.health.dto.MedicalHistoryUpdateRequest;
import com.sphp.patient.health.vo.AllergyCreateVO;
import com.sphp.patient.health.vo.AllergyUpdateVO;
import com.sphp.patient.health.vo.MedicalHistoryCreateVO;
import com.sphp.patient.health.vo.MedicalHistoryUpdateVO;
import com.sphp.patient.health.vo.HealthProfileVO;
import com.sphp.patient.health.vo.HealthRecordVO;
import com.sphp.patient.health.vo.HealthRecordDeleteVO;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.support.idempotency.CIdempotencyService;
import com.sphp.patient.support.idempotency.IdempotencyPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.HttpStatus;
import com.sphp.shared.common.enums.ErrorCodeEnum;

import java.util.List;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * 健康档案控制器接口测试。
 */
class HealthControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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
        MockMvc mockMvc = newMockMvc(healthService, idempotencyService);
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
        MockMvc mockMvc = newMockMvc(healthService, idempotencyService);
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
        MockMvc mockMvc = newMockMvc(healthService, idempotencyService);
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

    /**
     * 验证新增既往史接口通过幂等服务返回首次成功结果。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void createMedicalHistoryReturnsIdempotentSuccessResult() throws Exception {
        HealthService healthService = mock(HealthService.class);
        CIdempotencyService idempotencyService = mock(CIdempotencyService.class);
        MockMvc mockMvc = newMockMvc(healthService, idempotencyService);
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        MedicalHistoryCreateRequest request = new MedicalHistoryCreateRequest();
        request.setContent("高血压病史5年");
        request.setOccurredAt(LocalDate.of(2021, 1, 1));
        when(idempotencyService.execute(any(), any(), any(), any(), any(), any()))
                .thenReturn(new IdempotencyPayload<>("既往史已保存", MedicalHistoryCreateVO.builder()
                        .id(17001L).content("高血压病史5年")
                        .occurredAt(LocalDate.of(2021, 1, 1)).build()));

        mockMvc.perform(post("/c/v1/health-record/histories")
                        .header("X-Idempotency-Key", "history-key-001")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("既往史已保存"))
                .andExpect(jsonPath("$.data.id").value(17001L))
                .andExpect(jsonPath("$.data.occurredAt").value("2021-01-01"));
    }

    /**
     * 验证更新既往史接口通过幂等服务返回更新结果。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void updateMedicalHistoryReturnsIdempotentSuccessResult() throws Exception {
        HealthService healthService = mock(HealthService.class);
        CIdempotencyService idempotencyService = mock(CIdempotencyService.class);
        MockMvc mockMvc = newMockMvc(healthService, idempotencyService);
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        MedicalHistoryUpdateRequest request = new MedicalHistoryUpdateRequest();
        request.setContent("高血压病史6年");
        when(idempotencyService.execute(any(), any(), any(), any(), any(), any()))
                .thenReturn(new IdempotencyPayload<>("既往史已更新", MedicalHistoryUpdateVO.builder()
                        .id(17001L).content("高血压病史6年")
                        .occurredAt(LocalDate.of(2021, 1, 1)).updatedAt(OffsetDateTime.now()).build()));

        mockMvc.perform(put("/c/v1/health-record/histories/17001")
                        .header("X-Idempotency-Key", "history-key-002")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("既往史已更新"))
                .andExpect(jsonPath("$.data.content").value("高血压病史6年"));
    }

    /**
     * 验证删除过敏史接口通过幂等服务返回软删除结果。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void deleteAllergyReturnsDeletedResult() throws Exception {
        HealthService healthService = mock(HealthService.class);
        CIdempotencyService idempotencyService = mock(CIdempotencyService.class);
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        when(idempotencyService.execute(any(), any(), any(), any(), any(), any()))
                .thenReturn(new IdempotencyPayload<>("过敏史已删除", HealthRecordDeleteVO.builder()
                        .id(16001L).deletedAt(OffsetDateTime.parse("2026-08-09T12:00:00+08:00")).build()));

        newMockMvc(healthService, idempotencyService).perform(delete("/c/v1/health-record/allergies/16001")
                        .header("X-Idempotency-Key", "allergy-delete-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("过敏史已删除"))
                .andExpect(jsonPath("$.data.id").value(16001L))
                .andExpect(jsonPath("$.data.deletedAt").value("2026-08-09T12:00:00+08:00"));
    }

    /**
     * 验证删除既往史缺少幂等键时返回参数错误。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void deleteMedicalHistoryRejectsMissingIdempotencyKey() throws Exception {
        newMockMvc(mock(HealthService.class), mock(CIdempotencyService.class))
                .perform(delete("/c/v1/health-record/histories/17001"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0400"));
    }

    /**
     * 验证缺少幂等键时返回统一参数错误响应。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void createAllergyRejectsMissingIdempotencyKey() throws Exception {
        HealthService healthService = mock(HealthService.class);
        CIdempotencyService idempotencyService = mock(CIdempotencyService.class);
        AllergyCreateRequest request = new AllergyCreateRequest();
        request.setAllergen("青霉素");

        newMockMvc(healthService, idempotencyService).perform(post("/c/v1/health-record/allergies")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0400"));
    }

    /**
     * 验证无权访问患者健康档案时返回 HTTP 403。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void getHealthRecordReturnsForbiddenForUnauthorizedPatient() throws Exception {
        HealthService healthService = mock(HealthService.class);
        CIdempotencyService idempotencyService = mock(CIdempotencyService.class);
        when(healthService.getHealthRecord(20002L)).thenThrow(
                new CAuthException(ErrorCodeEnum.UNAUTHORIZED, HttpStatus.FORBIDDEN, "无权访问该就诊人健康档案"));

        newMockMvc(healthService, idempotencyService).perform(get("/c/v1/health-record").param("patientId", "20002"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A0301"));
    }

    /**
     * 验证健康档案系统异常返回 HTTP 500。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void getHealthRecordReturnsInternalServerErrorForSystemFailure() throws Exception {
        HealthService healthService = mock(HealthService.class);
        CIdempotencyService idempotencyService = mock(CIdempotencyService.class);
        when(healthService.getHealthRecord(20001L)).thenThrow(
                new CAuthException(ErrorCodeEnum.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "健康档案查询失败"));

        newMockMvc(healthService, idempotencyService).perform(get("/c/v1/health-record").param("patientId", "20001"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("B0001"));
    }

    /**
     * 创建使用生产日期序列化规则的健康档案控制器测试环境。
     *
     * @param healthService 健康档案服务模拟对象
     * @param idempotencyService 幂等服务模拟对象
     * @return MockMvc 测试对象
     */
    private MockMvc newMockMvc(HealthService healthService, CIdempotencyService idempotencyService) {
        return MockMvcBuilders.standaloneSetup(new HealthController(healthService, idempotencyService))
                .setControllerAdvice(new HealthExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }
}

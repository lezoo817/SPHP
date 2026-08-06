package com.sphp.patient.family.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.family.dto.ProfileUpdateRequest;
import com.sphp.patient.family.handler.ProfileExceptionHandler;
import com.sphp.patient.family.service.ProfileService;
import com.sphp.patient.family.vo.ProfileUpdateVO;
import com.sphp.patient.family.vo.ProfileVO;
import com.sphp.patient.support.idempotency.CIdempotencyService;
import com.sphp.patient.support.idempotency.IdempotencyPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * 个人资料控制器查询接口测试。
 */
class ProfileControllerTest {

    private ProfileService profileService;
    private CIdempotencyService idempotencyService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 初始化独立个人资料控制器测试环境。
     */
    @BeforeEach
    void setUp() {
        profileService = mock(ProfileService.class);
        idempotencyService = mock(CIdempotencyService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProfileController(profileService, idempotencyService))
                .setControllerAdvice(new ProfileExceptionHandler())
                .build();
    }

    /**
     * 每个测试结束后清理当前用户上下文。
     */
    @AfterEach
    void clearContext() {
        CUserContext.clear();
    }

    /**
     * 验证查询个人资料返回统一响应与脱敏资料。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void getProfileReturnsExpectedEnvelope() throws Exception {
        when(profileService.getProfile()).thenReturn(ProfileVO.builder()
                .id(20001L)
                .name("张三")
                .gender("MALE")
                .birthday(LocalDate.of(1990, 5, 20))
                .phone("138****8000")
                .idCardNo("110***********1234")
                .emergencyContact("李四 139****9000")
                .build());

        mockMvc.perform(get("/c/v1/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.message").value("查询成功"))
                .andExpect(jsonPath("$.data.id").value(20001L))
                .andExpect(jsonPath("$.data.phone").value("138****8000"))
                .andExpect(jsonPath("$.data.idCardNo").value("110***********1234"))
                .andExpect(jsonPath("$.data.emergencyContact").value("李四 139****9000"));
    }

    /**
     * 验证更新个人资料经幂等服务包装后返回更新摘要。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void updateProfileReturnsUpdatedProfile() throws Exception {
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setName("张三");
        request.setPhone("13800138000");
        request.setIdCardNo("110105194912311234");
        when(idempotencyService.execute(any(), any(), any(), any(), any(), any()))
                .thenReturn(new IdempotencyPayload<>("个人资料已更新", ProfileUpdateVO.builder()
                        .id(20001L).name("张三").phone("138****8000")
                        .idCardNo("110***********1234").updatedAt(OffsetDateTime.now()).build()));

        mockMvc.perform(put("/c/v1/profile")
                        .header("X-Idempotency-Key", "profile-key-001")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.message").value("个人资料已更新"))
                .andExpect(jsonPath("$.data.id").value(20001L))
                .andExpect(jsonPath("$.data.phone").value("138****8000"))
                .andExpect(jsonPath("$.data.idCardNo").value("110***********1234"));
    }

    /**
     * 验证更新资料缺少幂等键时返回参数错误。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void updateProfileRejectsMissingIdempotencyKey() throws Exception {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setName("张三");

        mockMvc.perform(put("/c/v1/profile")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0400"));
    }

    /**
     * 验证更新资料的手机号格式由 DTO 校验拒绝。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void updateProfileRejectsInvalidPhone() throws Exception {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setName("张三");
        request.setPhone("123");

        mockMvc.perform(put("/c/v1/profile")
                        .header("X-Idempotency-Key", "profile-key-002")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0400"));
    }

    /**
     * 验证更新资料的身份证号格式由 DTO 校验拒绝。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void updateProfileRejectsInvalidIdCardNo() throws Exception {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setName("张三");
        request.setIdCardNo("11010519491231");

        mockMvc.perform(put("/c/v1/profile")
                        .header("X-Idempotency-Key", "profile-key-003")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0400"));
    }
}

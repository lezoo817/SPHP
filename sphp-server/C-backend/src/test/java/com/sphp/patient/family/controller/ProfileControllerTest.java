package com.sphp.patient.family.controller;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.family.handler.ProfileExceptionHandler;
import com.sphp.patient.family.service.ProfileService;
import com.sphp.patient.family.vo.ProfileVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 个人资料控制器查询接口测试。
 */
class ProfileControllerTest {

    private ProfileService profileService;
    private MockMvc mockMvc;

    /**
     * 初始化独立个人资料控制器测试环境。
     */
    @BeforeEach
    void setUp() {
        profileService = mock(ProfileService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProfileController(profileService))
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
                .emergencyContact("李四 139****9000")
                .build());

        mockMvc.perform(get("/c/v1/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.message").value("查询成功"))
                .andExpect(jsonPath("$.data.id").value(20001L))
                .andExpect(jsonPath("$.data.phone").value("138****8000"))
                .andExpect(jsonPath("$.data.emergencyContact").value("李四 139****9000"));
    }
}

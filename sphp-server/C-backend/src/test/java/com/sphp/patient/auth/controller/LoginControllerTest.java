package com.sphp.patient.auth.controller;

import com.sphp.patient.auth.handler.LoginExceptionHandler;
import com.sphp.patient.auth.service.LoginService;
import com.sphp.patient.auth.vo.CaptchaVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * C端登录控制器接口测试。
 */
class LoginControllerTest {

    private LoginService loginService;
    private MockMvc mockMvc;

    /**
     * 初始化独立控制器测试环境。
     */
    @BeforeEach
    void setUp() {
        loginService = mock(LoginService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new LoginController(loginService))
                .setControllerAdvice(new LoginExceptionHandler())
                .build();
    }

    /**
     * 验证图形验证码接口返回统一响应信封。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void getCaptchaReturnsExpectedEnvelope() throws Exception {
        when(loginService.createCaptcha()).thenReturn(CaptchaVO.builder()
                .challengeId("cap_test")
                .imageBase64("data:image/png;base64,test")
                .expireSeconds(120)
                .build());

        mockMvc.perform(get("/c/v1/auth/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.message").value("获取验证码成功"))
                .andExpect(jsonPath("$.data.challengeId").value("cap_test"))
                .andExpect(jsonPath("$.data.expireSeconds").value(120));
    }
}

package com.sphp.patient.auth.controller;

import com.sphp.patient.auth.handler.LoginExceptionHandler;
import com.sphp.patient.auth.service.LoginService;
import com.sphp.patient.auth.vo.CaptchaVO;
import com.sphp.patient.auth.dto.RegisterRequest;
import com.sphp.patient.auth.dto.LoginRequest;
import com.sphp.patient.auth.dto.RefreshTokenRequest;
import com.sphp.patient.auth.vo.LoginVO;
import com.sphp.patient.auth.vo.LoginUserVO;
import com.sphp.patient.auth.vo.RegisterVO;
import com.sphp.patient.auth.vo.TokenParseVO;
import com.sphp.patient.auth.vo.RefreshTokenVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.mockito.ArgumentMatchers.any;

/**
 * C端登录控制器接口测试。
 */
class LoginControllerTest {

    private LoginService loginService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

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

    /**
     * 验证注册接口返回新建 C端用户信息。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void registerReturnsCreatedUser() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setAccount("patient_zhangsan");
        request.setPassword("P@ssw0rd123");
        request.setChallengeId("cap_test");
        request.setCaptchaCode("A7K9");
        when(loginService.register(any(RegisterRequest.class))).thenReturn(RegisterVO.builder()
                .userId(10001L).account("patient_zhangsan").build());

        mockMvc.perform(post("/c/v1/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.message").value("注册成功"))
                .andExpect(jsonPath("$.data.userId").value(10001L));
    }

    /**
     * 验证注册账号长度不合法时返回 HTTP 400。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void registerRejectsInvalidAccount() throws Exception {
        mockMvc.perform(post("/c/v1/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("{\"account\":\"a\",\"password\":\"12345678\","
                                + "\"challengeId\":\"cap_test\",\"captchaCode\":\"A7K9\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0400"));
    }

    /**
     * 验证账号密码登录接口返回 Token 对和用户摘要。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void loginReturnsTokenPair() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setAccount("patient_zhangsan");
        request.setPassword("P@ssw0rd123");
        when(loginService.login(any(LoginRequest.class))).thenReturn(LoginVO.builder()
                .accessToken("access-token")
                .refreshToken("rt_token")
                .expiresIn(7200)
                .user(LoginUserVO.builder().id(10001L).account("patient_zhangsan").build())
                .build());

        mockMvc.perform(post("/c/v1/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("登录成功"))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("rt_token"))
                .andExpect(jsonPath("$.data.user.id").value(10001L));
    }

    /**
     * 验证 C端 JWT 解析接口返回最小身份信息。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void parseTokenReturnsMinimalIdentity() throws Exception {
        when(loginService.parseToken()).thenReturn(TokenParseVO.builder()
                .userId(10001L)
                .account("patient_zhangsan")
                .tokenExpiresAt(OffsetDateTime.parse("2026-08-03T10:00:00+08:00"))
                .build());

        mockMvc.perform(get("/c/v1/auth/token/parse"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("令牌解析成功"))
                .andExpect(jsonPath("$.data.userId").value(10001L))
                .andExpect(jsonPath("$.data.account").value("patient_zhangsan"));
    }

    /**
     * 验证刷新令牌接口返回轮换后的 Token 对。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void refreshReturnsRotatedTokenPair() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("rt_old_token");
        when(loginService.refresh(any(RefreshTokenRequest.class))).thenReturn(RefreshTokenVO.builder()
                .accessToken("new-access-token")
                .refreshToken("rt_new_token")
                .expiresIn(7200)
                .build());

        mockMvc.perform(post("/c/v1/auth/token/refresh")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("令牌刷新成功"))
                .andExpect(jsonPath("$.data.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("rt_new_token"));
    }
}

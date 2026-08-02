package com.sphp.patient.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.jwt.CJwtClaims;
import com.sphp.patient.auth.support.jwt.CJwtService;
import com.sphp.shared.common.constant.HeaderConstant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * C端 JWT 拦截器单元测试。
 */
class CJwtInterceptorTest {

    /**
     * 每个测试结束后清理用户上下文。
     */
    @AfterEach
    void clearContext() {
        CUserContext.clear();
    }

    /**
     * 验证有效 C端 Token 和 Redis 会话能够建立并清理用户上下文。
     *
     * @throws Exception 拦截器处理失败时抛出
     */
    @Test
    void validTokenCreatesAndClearsUserContext() throws Exception {
        CJwtService jwtService = mock(CJwtService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtService.parseAccessToken("access-token")).thenReturn(new CJwtClaims(
                10001L, "patient_zhangsan", OffsetDateTime.now().plusHours(1), "session-hash"));
        when(valueOperations.get("cend:refresh:session-hash")).thenReturn("10001:30001");
        CJwtInterceptor interceptor = new CJwtInterceptor(jwtService, redisTemplate, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderConstant.AUTHORIZATION, "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertEquals(10001L, CUserContext.getRequired().userId());
        interceptor.afterCompletion(request, response, new Object(), null);
        assertNull(CUserContext.get());
    }

    /**
     * 验证外部传入 X-User-Id 时拒绝建立身份上下文。
     *
     * @throws Exception 拦截器处理失败时抛出
     */
    @Test
    void externalUserHeaderIsRejected() throws Exception {
        CJwtInterceptor interceptor = new CJwtInterceptor(
                mock(CJwtService.class), mock(StringRedisTemplate.class), new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderConstant.USER_ID, "10001");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
        assertNull(CUserContext.get());
    }

    /**
     * 验证浏览器跨域预检请求不需要 C端 Token。
     *
     * @throws Exception 拦截器处理失败时抛出
     */
    @Test
    void optionsPreflightRequestBypassesTokenValidation() throws Exception {
        CJwtInterceptor interceptor = new CJwtInterceptor(
                mock(CJwtService.class), mock(StringRedisTemplate.class), new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/c/v1/family-members");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertNull(CUserContext.get());
    }
}

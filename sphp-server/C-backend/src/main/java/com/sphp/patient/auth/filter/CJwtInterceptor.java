package com.sphp.patient.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.auth.support.jwt.CJwtClaims;
import com.sphp.patient.auth.support.jwt.CJwtService;
import com.sphp.patient.common.constant.CAuthConstant;
import com.sphp.shared.common.constant.HeaderConstant;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import com.sphp.shared.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.sphp.patient.common.constant.CAuthConstant.BEARER_PREFIX;
import static com.sphp.patient.common.constant.CAuthConstant.REFRESH_SESSION_KEY_PREFIX;
import static com.sphp.shared.common.enums.ErrorCodeEnum.UNAUTHORIZED;

/**
 * C端 JWT 请求拦截器。
 */
@Component
@RequiredArgsConstructor
public class CJwtInterceptor implements HandlerInterceptor {

    private final CJwtService jwtService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 校验 C端 Bearer Token 和对应刷新会话。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param handler 目标处理器
     * @return 校验通过时返回 true
     * @throws IOException 写入未授权响应失败时抛出
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        // 请求入口先清理可能残留的线程身份，确保失败路径也不会复用旧上下文
        CUserContext.clear();
        // 浏览器跨域预检不携带 Token，交由全局 CORS 配置返回允许的请求头和方法
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        // 外部身份头不能替代 JWT 建立 C端用户上下文
        if (StringUtils.hasText(request.getHeader(HeaderConstant.USER_ID))) {
            writeUnauthorized(response, "不允许使用外部用户身份头");
            return false;
        }
        String authorization = request.getHeader(HeaderConstant.AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, "缺少有效的访问令牌");
            return false;
        }
        try {
            CJwtClaims claims = jwtService.parseAccessToken(
                    authorization.substring(BEARER_PREFIX.length()).trim());
            // 会话摘要必须仍存在于 Redis，退出或改密撤销后 Access Token 立即失效
            String sessionValue = redisTemplate.opsForValue().get(
                    REFRESH_SESSION_KEY_PREFIX + claims.sessionHash());
            if (!StringUtils.hasText(sessionValue) || !sessionValue.startsWith(claims.userId() + ":")) {
                writeUnauthorized(response, "当前登录会话已失效");
                return false;
            }
            CUserContext.set(new CUserPrincipal(
                    claims.userId(), claims.account(), claims.expiresAt(), claims.sessionHash()));
            return true;
        } catch (RuntimeException e) {
            writeUnauthorized(response, "Token无效或已过期");
            return false;
        }
    }

    /**
     * 请求结束后清理当前线程身份。
     *
     * @param request HTTP 请求
     * @param response HTTP 响应
     * @param handler 目标处理器
     * @param ex 请求处理异常
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        CUserContext.clear();
    }

    /**
     * 写入统一未授权响应。
     *
     * @param response HTTP 响应
     * @param message 用户可读提示
     * @throws IOException 响应序列化失败时抛出
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), Result.error(UNAUTHORIZED, message));
    }
}

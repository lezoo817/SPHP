package com.sphp.shared.filter;

import com.sphp.shared.common.constant.HeaderConstant;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 全链路追踪号过滤器。
 *
 * <p>从请求头 {@code X-Trace-Id} 透传（便于调用方串联日志），否则自动生成；
 * 写入 MDC（供日志与 {@code Result.traceId} 使用）及响应头 {@code X-Trace-Id}。
 * 请求结束清理 MDC，防止线程池复用导致追踪号串扰。
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    /** MDC key，与 {@code com.sphp.shared.result.Result#traceId} 对应 */
    public static final String TRACE_ID_KEY = "traceId";
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 优先透传调用方传入的 traceId，否则生成一个
        String traceId = request.getHeader(HeaderConstant.TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(HeaderConstant.TRACE_ID, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 请求结束清理，防止线程复用串扰
            MDC.remove(TRACE_ID_KEY);
        }
    }
}

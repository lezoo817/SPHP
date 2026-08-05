package com.sphp.admin.common;

import com.sphp.shared.common.constant.HeaderConstant;
import com.sphp.shared.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Agent 调用鉴权拦截器（系分 §8.2）。
 *
 * <p>解析 X-User-Id 请求头并加载用户上下文，缓存至 {@link UserContextHolder}（ThreadLocal）：
 * <ul>
 *     <li>头存在：校验用户有效后写入上下文；用户不存在/已停用抛 A0301</li>
 *     <li>头缺失：跳过（B 端 Web 请求走 Sa-Token Bearer 鉴权）</li>
 * </ul>
 * 请求结束后在 {@link #afterCompletion} 清理 ThreadLocal，防止内存泄漏。
 */
@RequiredArgsConstructor
public class UserContextInterceptor implements HandlerInterceptor {

    private final UserContextService userContextService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userIdStr = request.getHeader(HeaderConstant.USER_ID);
        if (StringUtils.hasText(userIdStr)) {
            Long userId;
            try {
                userId = Long.valueOf(userIdStr.trim());
            } catch (NumberFormatException e) {
                throw new BusinessException("A0301", "用户身份无效");
            }
            UserContext context = userContextService.loadUserContext(userId);
            UserContextHolder.setContext(context);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContextHolder.clear();
    }
}

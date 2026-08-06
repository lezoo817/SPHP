package com.sphp.admin.common;

import com.sphp.shared.common.constant.HeaderConstant;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import com.sphp.shared.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Agent 调用鉴权拦截器。
 *
 * <p>解析 X-User-Id 请求头并加载用户上下文，缓存至 {@link UserContextHolder}（ThreadLocal）：
 * <ul>
 *     <li>头存在：校验用户有效后写入上下文；用户不存在/已停用/格式非法抛 UNAUTHORIZED</li>
 *     <li>头缺失：跳过（B 端 Web 请求走 Sa-Token Bearer 鉴权）</li>
 * </ul>
 * 请求结束后在 {@link #afterCompletion} 清理 ThreadLocal，防止内存泄漏。
 */
@Component
@RequiredArgsConstructor
public class UserContextInterceptor implements HandlerInterceptor {

    private final UserContextService userContextService;

    /**
     * 解析 X-User-Id 请求头并建立用户上下文（Agent 通道）。
     *
     * @param request  当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler  即将执行的处理器
     * @return 恒为 true（无头时仅跳过上下文建立，鉴权由 Sa-Token 接管）
     * @throws BusinessException UNAUTHORIZED(A0301)：X-User-Id 头格式非法（非 Long）
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userIdStr = request.getHeader(HeaderConstant.USER_ID);
        if (StringUtils.hasText(userIdStr)) {
            Long userId;
            try {
                userId = Long.valueOf(userIdStr.trim());
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED, "用户身份无效");
            }
            UserContext context = userContextService.loadUserContext(userId);
            UserContextHolder.setContext(context);
        }
        return true;
    }

    /**
     * 请求结束后清理线程上下文。
     *
     * @param request  当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler  已执行的处理器
     * @param ex       处理过程中抛出的异常（可为 null）
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContextHolder.clear();
    }
}

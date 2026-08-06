package com.sphp.admin.common.handler;

import cn.dev33.satoken.exception.NotLoginException;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import com.sphp.shared.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * B端鉴权异常处理：Sa-Token 未登录 / Token 无效统一映射为 UNAUTHORIZED(A0301)。
 *
 * <p>与共享层 {@code GlobalExceptionHandler} 并存，各自处理自己的异常类型。
 */
@Slf4j
@RestControllerAdvice
public class NotLoginExceptionHandler {

    /**
     * 处理 Sa-Token 未登录异常。
     *
     * @param e Sa-Token 抛出的 NotLoginException（含未登录 / Token 过期 / Token 非法等场景）
     * @return 统一失败响应（A0301 + 登录失效提示）
     */
    @ExceptionHandler(NotLoginException.class)
    public Result<Void> handleNotLogin(NotLoginException e) {
        log.warn("未登录或Token无效: {}", e.getMessage());
        return Result.error(ErrorCodeEnum.UNAUTHORIZED, "登录状态已失效，请重新登录");
    }
}

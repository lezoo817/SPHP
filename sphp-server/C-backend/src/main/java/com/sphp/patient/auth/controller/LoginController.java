package com.sphp.patient.auth.controller;

import com.sphp.patient.auth.service.LoginService;
import com.sphp.patient.auth.vo.CaptchaVO;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C端用户登录注册接口。
 */
@RestController
@RequestMapping("/c/v1/auth")
@Tag(name = "C端认证", description = "图形验证码、注册、登录和令牌管理")
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;

    /**
     * 获取一次性图形验证码。
     *
     * @return 验证码挑战标识、图片和有效期
     */
    @GetMapping("/captcha")
    @Operation(summary = "获取图形验证码")
    public Result<CaptchaVO> captcha() {
        return Result.success("获取验证码成功", loginService.createCaptcha());
    }
}

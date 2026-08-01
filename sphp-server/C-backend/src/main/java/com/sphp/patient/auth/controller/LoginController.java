package com.sphp.patient.auth.controller;

import com.sphp.patient.auth.service.LoginService;
import com.sphp.patient.auth.dto.RegisterRequest;
import com.sphp.patient.auth.dto.LoginRequest;
import com.sphp.patient.auth.dto.RefreshTokenRequest;
import com.sphp.patient.auth.vo.CaptchaVO;
import com.sphp.patient.auth.vo.RegisterVO;
import com.sphp.patient.auth.vo.LoginVO;
import com.sphp.patient.auth.vo.TokenParseVO;
import com.sphp.patient.auth.vo.RefreshTokenVO;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

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

    /**
     * 注册 C端账号并创建本人默认就诊人。
     *
     * @param request 注册请求
     * @return 新建账号信息
     */
    @PostMapping("/register")
    @Operation(summary = "注册C端账号")
    public Result<RegisterVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success("注册成功", loginService.register(request));
    }

    /**
     * 使用账号密码登录 C端系统。
     *
     * @param request 登录请求
     * @return Token 对和用户摘要
     */
    @PostMapping("/login")
    @Operation(summary = "C端账号密码登录")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success("登录成功", loginService.login(request));
    }

    /**
     * 解析当前已校验的 C端 Access Token。
     *
     * @return 当前用户最小身份上下文
     */
    @GetMapping("/token/parse")
    @Operation(summary = "解析当前C端Token")
    public Result<TokenParseVO> parseToken() {
        return Result.success("令牌解析成功", loginService.parseToken());
    }

    /**
     * 使用 Refresh Token 轮换新的 Token 对。
     *
     * @param request 刷新令牌请求
     * @return 新 Token 对
     */
    @PostMapping("/token/refresh")
    @Operation(summary = "刷新C端访问令牌")
    public Result<RefreshTokenVO> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success("令牌刷新成功", loginService.refresh(request));
    }
}

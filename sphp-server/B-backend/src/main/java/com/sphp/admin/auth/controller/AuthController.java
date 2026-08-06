package com.sphp.admin.auth.controller;

import com.sphp.admin.auth.dto.LoginRequest;
import com.sphp.admin.auth.dto.RefreshTokenRequest;
import com.sphp.admin.auth.service.AuthService;
import com.sphp.admin.auth.vo.LoginVO;
import com.sphp.admin.auth.vo.LogoutVO;
import com.sphp.admin.auth.vo.RefreshTokenVO;
import com.sphp.admin.auth.vo.TokenParseVO;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * B 端认证接口：登录、刷新令牌、Token 解析（供 Agent）、退出登录。
 *
 * <p>请求路径：{@code /b/auth/**}（外部 URL 由 {@code server.servlet.context-path=/api} 前缀补全）。
 */
@RestController
@RequestMapping("/b/auth")
@Tag(name = "认证模块", description = "登录 / 刷新令牌 / Token 解析 / 退出登录")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "登录", description = "账号密码登录，返回 accessToken + refreshToken + 用户信息")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success("登录成功", authService.login(request));
    }

    @PostMapping("/token/refresh")
    @Operation(summary = "刷新令牌", description = "用 refreshToken 换新 token 对（旧刷新令牌自动吊销）")
    public Result<RefreshTokenVO> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refresh(request));
    }

    @GetMapping("/token/parse")
    @Operation(summary = "解析当前 Token", description = "供 Agent 调用：Bearer 头携带 accessToken，返回用户上下文")
    public Result<TokenParseVO> parseToken() {
        return Result.success("令牌解析成功", authService.parseToken());
    }

    @PostMapping("/logout")
    @Operation(summary = "退出登录", description = "吊销当前用户全部刷新令牌并注销会话")
    public Result<LogoutVO> logout() {
        return Result.success("退出成功", authService.logout());
    }
}

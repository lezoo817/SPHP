package com.sphp.admin.auth.service;

import com.sphp.admin.auth.dto.LoginRequest;
import com.sphp.admin.auth.dto.RefreshTokenRequest;
import com.sphp.admin.auth.vo.LoginVO;
import com.sphp.admin.auth.vo.LogoutVO;
import com.sphp.admin.auth.vo.RefreshTokenVO;
import com.sphp.admin.auth.vo.TokenParseVO;

/**
 * B 端认证服务。
 *
 * <p>负责账号密码登录、refreshToken 轮换、Agent 通道的 Token 解析，以及 logout 时的
 * refreshToken 全量吊销。accessToken 由 Sa-Token 签发，详见
 * {@link com.sphp.admin.auth.service.impl.AuthServiceImpl} 类注释。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
public interface AuthService {

    /**
     * 账号密码登录，签发 accessToken + refreshToken 并返回用户信息。
     *
     * @param request 登录请求（账号 / 密码）
     * @return 登录响应
     */
    LoginVO login(LoginRequest request);

    /**
     * 校验旧 refreshToken 后轮换签发新 token 对（旧 refreshToken 自动吊销）。
     *
     * @param request 刷新请求（旧 refreshToken）
     * @return 新 token 对
     */
    RefreshTokenVO refresh(RefreshTokenRequest request);

    /**
     * 解析当前请求 Token，返回用户上下文（供 Agent 通道调用）。
     *
     * @return Token 解析响应
     */
    TokenParseVO parseToken();

    /**
     * 退出登录：吊销当前用户全部有效 refreshToken，阻断后续续期。
     *
     * @return 退出结果
     */
    LogoutVO logout();
}

package com.sphp.admin.auth.service;

import com.sphp.admin.auth.dto.LoginRequest;
import com.sphp.admin.auth.dto.RefreshTokenRequest;
import com.sphp.admin.auth.vo.LoginVO;
import com.sphp.admin.auth.vo.LogoutVO;
import com.sphp.admin.auth.vo.RefreshTokenVO;
import com.sphp.admin.auth.vo.TokenParseVO;

/**
 * B端认证服务。
 */
public interface AuthService {

    /**
     * 登录：校验账号密码，签发 accessToken + refreshToken（入库），返回用户信息。
     */
    LoginVO login(LoginRequest request);

    /**
     * 刷新令牌：校验旧 refreshToken，轮换签发新 token 对。
     */
    RefreshTokenVO refresh(RefreshTokenRequest request);

    /**
     * 解析当前请求 Token（供 Agent 调用），返回用户上下文。
     */
    TokenParseVO parseToken();

    /**
     * 退出登录：注销当前会话并吊销该用户全部有效刷新令牌。
     */
    LogoutVO logout();
}

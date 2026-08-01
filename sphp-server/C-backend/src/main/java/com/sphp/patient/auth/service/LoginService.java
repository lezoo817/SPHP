package com.sphp.patient.auth.service;

import com.sphp.patient.auth.vo.CaptchaVO;
import com.sphp.patient.auth.dto.RegisterRequest;
import com.sphp.patient.auth.dto.LoginRequest;
import com.sphp.patient.auth.dto.RefreshTokenRequest;
import com.sphp.patient.auth.dto.LogoutRequest;
import com.sphp.patient.auth.vo.LoginVO;
import com.sphp.patient.auth.vo.TokenParseVO;
import com.sphp.patient.auth.vo.RefreshTokenVO;
import com.sphp.patient.auth.vo.LogoutVO;
import com.sphp.patient.auth.vo.RegisterVO;

/**
 * C端登录注册服务。
 */
public interface LoginService {

    /**
     * 生成一次性图形验证码并保存摘要。
     *
     * @return 图形验证码信息
     */
    CaptchaVO createCaptcha();

    /**
     * 注册 C端账号并创建本人默认就诊人。
     *
     * @param request 注册请求
     * @return 新建账号信息
     */
    RegisterVO register(RegisterRequest request);

    /**
     * 使用 C端账号密码登录并签发 Token 对。
     *
     * @param request 登录请求
     * @return Token 对和用户摘要
     */
    LoginVO login(LoginRequest request);

    /**
     * 读取当前已验证 C端用户的最小令牌上下文。
     *
     * @return Token 最小身份信息
     */
    TokenParseVO parseToken();

    /**
     * 校验并轮换 C端刷新令牌。
     *
     * @param request 刷新令牌请求
     * @return 新 Token 对
     */
    RefreshTokenVO refresh(RefreshTokenRequest request);

    /**
     * 撤销当前 Access Token 绑定的刷新会话。
     *
     * @param request 退出登录请求
     * @return 退出结果
     */
    LogoutVO logout(LogoutRequest request);
}

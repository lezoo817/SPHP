package com.sphp.patient.auth.service;

import com.sphp.patient.auth.vo.CaptchaVO;
import com.sphp.patient.auth.dto.RegisterRequest;
import com.sphp.patient.auth.dto.LoginRequest;
import com.sphp.patient.auth.vo.LoginVO;
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
}

package com.sphp.patient.auth.service;

import com.sphp.patient.auth.vo.CaptchaVO;

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
}

package com.sphp.patient.auth.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * 图形验证码响应对象。
 */
@Getter
@Builder
public class CaptchaVO {

    /** 验证码挑战标识 */
    private final String challengeId;
    /** PNG Base64 图片数据 */
    private final String imageBase64;
    /** 有效期秒数 */
    private final long expireSeconds;
}

package com.sphp.patient.family.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * C端当前账号本人资料更新响应对象。
 */
@Getter
@Builder
public class ProfileUpdateVO {

    /** 本人就诊人 ID */
    private final Long id;

    /** 更新后的本人姓名 */
    private final String name;

    /** 脱敏手机号 */
    private final String phone;

    /** 资料更新时间 */
    private final OffsetDateTime updatedAt;
}

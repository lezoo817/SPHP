package com.sphp.patient.health.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 更新过敏史响应对象。
 */
@Getter
@Builder
public class AllergyUpdateVO {

    /** 过敏史 ID */
    private final Long id;
    /** 过敏原名称 */
    private final String allergen;
    /** 过敏反应描述 */
    private final String reaction;
    /** 更新时间 */
    private final OffsetDateTime updatedAt;
}

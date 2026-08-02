package com.sphp.patient.health.vo;

import lombok.Builder;
import lombok.Getter;

/**
 * 健康档案中的过敏史列表项。
 */
@Getter
@Builder
public class AllergyItemVO {

    /** 过敏史 ID */
    private final Long id;
    /** 过敏原名称 */
    private final String allergen;
    /** 过敏反应描述 */
    private final String reaction;
}

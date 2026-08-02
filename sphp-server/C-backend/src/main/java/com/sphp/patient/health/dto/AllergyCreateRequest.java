package com.sphp.patient.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 新增过敏史请求。
 */
@Getter
@Setter
@NoArgsConstructor
public class AllergyCreateRequest {

    /** 可选目标就诊人 ID，未传时使用本人 */
    @Positive(message = "就诊人ID必须为正整数")
    private Long patientId;
    /** 过敏原名称 */
    @NotBlank(message = "过敏原不能为空")
    @Size(max = 128, message = "过敏原长度不能超过128位")
    private String allergen;
    /** 过敏反应描述 */
    @Size(max = 512, message = "过敏反应长度不能超过512位")
    private String reaction;
}

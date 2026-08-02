package com.sphp.patient.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 更新过敏史请求。
 */
@Getter
@Setter
@NoArgsConstructor
public class AllergyUpdateRequest {

    /** 过敏原名称 */
    @NotBlank(message = "过敏原不能为空")
    @Size(max = 128, message = "过敏原长度不能超过128位")
    private String allergen;
    /** 过敏反应描述，未传时保留原值 */
    @Size(max = 512, message = "过敏反应长度不能超过512位")
    private String reaction;
}

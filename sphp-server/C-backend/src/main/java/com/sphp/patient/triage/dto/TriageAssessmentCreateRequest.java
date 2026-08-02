package com.sphp.patient.triage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 提交症状获取导诊建议请求。
 */
@Getter
@Setter
public class TriageAssessmentCreateRequest {

    /** 可选就诊人 ID，未传时使用当前账号本人。 */
    @Positive(message = "patientId 必须为正数")
    private Long patientId;

    /** 用于限定推荐科室范围的医院 ID。 */
    @NotNull(message = "hospitalId 不能为空")
    @Positive(message = "hospitalId 必须为正数")
    private Long hospitalId;

    /** 当前症状描述，按系分限制为 1 至 1000 字。 */
    @NotBlank(message = "症状不能为空")
    @Size(max = 1000, message = "症状不能超过1000字")
    private String symptom;

    /** 可选症状持续时间说明。 */
    @Size(max = 128, message = "症状持续时间不能超过128字")
    private String duration;

    /** 可选最近一次体温，单位为摄氏度。 */
    private BigDecimal temperature;

    /** 可选的本次症状相关既往史。 */
    @Size(max = 2000, message = "既往史不能超过2000字")
    private String medicalHistory;
}

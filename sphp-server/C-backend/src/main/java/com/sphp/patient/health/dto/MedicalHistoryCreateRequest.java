package com.sphp.patient.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 新增既往史请求。
 */
@Getter
@Setter
@NoArgsConstructor
public class MedicalHistoryCreateRequest {

    /** 可选目标就诊人 ID，未传时使用本人 */
    @Positive(message = "就诊人ID必须为正整数")
    private Long patientId;
    /** 既往史内容 */
    @NotBlank(message = "既往史内容不能为空")
    @Size(max = 2000, message = "既往史内容长度不能超过2000位")
    private String content;
    /** 病史发生或记录日期 */
    private LocalDate occurredAt;
}

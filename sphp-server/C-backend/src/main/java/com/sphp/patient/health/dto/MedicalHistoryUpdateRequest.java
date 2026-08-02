package com.sphp.patient.health.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 更新既往史请求。
 */
@Getter
@Setter
@NoArgsConstructor
public class MedicalHistoryUpdateRequest {

    /** 既往史内容 */
    @NotBlank(message = "既往史内容不能为空")
    @Size(max = 2000, message = "既往史内容长度不能超过2000位")
    private String content;
    /** 病史发生或记录日期，未传时保留原值 */
    private LocalDate occurredAt;
}

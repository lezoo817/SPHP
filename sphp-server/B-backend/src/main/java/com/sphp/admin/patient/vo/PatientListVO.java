package com.sphp.admin.patient.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 患者列表项 VO。
 *
 * @author lezoo17
 * @since 2026-08-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "患者列表项")
public class PatientListVO {

    @Schema(description = "患者ID")
    private Long id;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "性别：MALE / FEMALE / UNKNOWN")
    private String gender;

    @Schema(description = "年龄")
    private Integer age;

    @Schema(description = "最近就诊日期")
    private LocalDate lastVisitDate;

    /** 内部字段，用于计算年龄后清空，不对外暴露 */
    @JsonIgnore
    @Schema(hidden = true)
    private LocalDate dateOfBirth;
}
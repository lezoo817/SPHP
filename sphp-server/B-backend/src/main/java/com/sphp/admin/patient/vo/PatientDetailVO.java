package com.sphp.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 患者详情 VO。
 *
 * @author lezoo17
 * @since 2026-08-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "患者详情")
public class PatientDetailVO {

    @Schema(description = "患者ID")
    private Long id;

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "性别：MALE / FEMALE / UNKNOWN")
    private String gender;

    @Schema(description = "出生日期")
    private LocalDate dateOfBirth;

    @Schema(description = "手机号（脱敏）")
    private String phone;

    @Schema(description = "紧急联系人")
    private String emergencyContact;

    @Schema(description = "过敏史列表")
    private List<AllergyVO> allergies;

    @Schema(description = "既往史列表")
    private List<MedicalHistoryVO> medicalHistories;
}
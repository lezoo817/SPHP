package com.sphp.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 患者当前用药与随访 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "患者当前用药与随访")
public class PatientMedicationVO {

    @Schema(description = "用药计划列表")
    private List<MedicationPlanVO> medicationPlans;

    @Schema(description = "随访计划列表")
    private List<FollowUpPlanVO> followUpPlans;
}
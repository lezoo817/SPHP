package com.sphp.admin.statistics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 科室统计 VO。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "科室统计项")
public class DepartmentStatVO {

    @Schema(description = "科室 ID")
    private Long deptId;

    @Schema(description = "科室名称")
    private String deptName;

    @Schema(description = "挂号量")
    private long appointmentCount;

    @Schema(description = "接诊量")
    private long consultCount;

    @Schema(description = "处方量")
    private long prescriptionCount;

    @Schema(description = "号源利用率（0~1 小数）")
    private double slotUsageRate;
}

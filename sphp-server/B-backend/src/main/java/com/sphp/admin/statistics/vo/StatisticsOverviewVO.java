package com.sphp.admin.statistics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 运营总览 VO。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "运营总览")
public class StatisticsOverviewVO {

    @Schema(description = "总挂号量")
    private long totalAppointments;

    @Schema(description = "完成率（0~1 小数）")
    private double completedRate;

    @Schema(description = "总收入（单位：分）")
    private long totalRevenueCent;

    @Schema(description = "处方量")
    private long totalPrescriptions;

    @Schema(description = "平均等待时间（分钟）")
    private long avgWaitTime;
}
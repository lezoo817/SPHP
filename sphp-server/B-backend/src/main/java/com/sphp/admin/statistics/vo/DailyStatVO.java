package com.sphp.admin.statistics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 日统计 VO。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "日统计项")
public class DailyStatVO {

    @Schema(description = "日期（yyyy-MM-dd）")
    private LocalDate date;

    @Schema(description = "挂号量")
    private long appointmentCount;

    @Schema(description = "接诊量")
    private long consultCount;

    @Schema(description = "处方量")
    private long prescriptionCount;

    @Schema(description = "收入（单位：分）")
    private long revenueCent;
}
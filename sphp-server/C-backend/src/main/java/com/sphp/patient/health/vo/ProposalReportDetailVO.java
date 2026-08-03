package com.sphp.patient.health.vo;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

/** 报告详情响应。 */
@Getter
@Builder
public class ProposalReportDetailVO {
    // 报告 ID
    private final Long id;
    // 报告名称
    private final String reportName;
    // 报告时间
    private final LocalDate reportDate;
    // 指标列表
    private final List<Indicator> indicators;

    // 指标
    @Getter
    @Builder
    public static class Indicator {
        // 指标名称
        private final String name;
        // 指标值
        private final String value;
        // 指标单位
        private final String unit;
        // 参考范围
        private final String referenceRange;
    }
}

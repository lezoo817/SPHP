package com.sphp.patient.health.vo;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

/** 报告分页响应。 */
@Getter
@Builder
public class ProposalReportPageVO {
    // 页码
    private final int pageNo;
    // 大小
    private final int pageSize;
    // 总记录数
    private final long total;
    // 记录列表
    private final List<Item> records;

    // 记录项
    @Getter
    @Builder
    public static class Item {
        // 报告 ID
        private final Long id;
        // 报告名称
        private final String reportName;
        // 报告日期
        private final LocalDate reportDate;
        // 指标数量
        private final long indicatorCount;
    }
}

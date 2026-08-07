package com.sphp.admin.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 批量排班提交结果 VO。
 *
 * <p>按 action 分类汇总：实际新建的、复用 CANCELLED 重置为 DRAFT 的、被跳过的。
 *
 * @author lezoo17
 * @date 2026-08-07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "批量排班提交结果")
public class BatchCreateReportVO {

    @Schema(description = "新建的排班数")
    private int createdCount;

    @Schema(description = "复用 CANCELLED 重置为 DRAFT 的排班数")
    private int reusedCount;

    @Schema(description = "跳过的候选数")
    private int skippedCount;

    @Schema(description = "新建明细")
    private List<BatchItem> createdItems;

    @Schema(description = "复用明细")
    private List<BatchItem> reusedItems;

    @Schema(description = "跳过明细（含原因）")
    private List<BatchSkipItem> skippedItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "新建/复用明细")
    public static class BatchItem {
        @Schema(description = "排班 ID")
        private Long scheduleId;
        @Schema(description = "排班日期")
        private LocalDate scheduleDate;
        @Schema(description = "班次")
        private String shift;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "跳过明细")
    public static class BatchSkipItem {
        @Schema(description = "排班日期")
        private LocalDate scheduleDate;
        @Schema(description = "班次")
        private String shift;
        @Schema(description = "跳过原因")
        private String reason;
    }
}

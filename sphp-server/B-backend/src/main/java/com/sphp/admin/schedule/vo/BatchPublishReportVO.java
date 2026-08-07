package com.sphp.admin.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量发布排班结果 VO。
 *
 * <p>逐条调现有 {@code publish()}；非 DRAFT / 越权 / 未配置时段等失败原因以明细形式返回，不抛错中断整批。
 *
 * @author lezoo17
 * @date 2026-08-07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "批量发布排班结果")
public class BatchPublishReportVO {

    @Schema(description = "成功发布数")
    private int publishedCount;

    @Schema(description = "失败数")
    private int failedCount;

    @Schema(description = "失败明细（含原因）")
    private List<FailedItem> failedItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "失败明细")
    public static class FailedItem {
        @Schema(description = "排班 ID")
        private Long scheduleId;
        @Schema(description = "失败原因")
        private String reason;
    }
}

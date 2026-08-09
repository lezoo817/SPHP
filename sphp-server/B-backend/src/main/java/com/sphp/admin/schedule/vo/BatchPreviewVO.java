package com.sphp.admin.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 批量排班预览 VO。
 *
 * <p>提交前对 (date, shift) 候选集进行只读预演，给出每个候选的最终去向与默认时段拆分预览。
 *
 * @author lezoo17
 * @since 2026-08-07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "批量排班预览结果")
public class BatchPreviewVO {

    @Schema(description = "医生ID")
    private Long doctorId;

    @Schema(description = "医生姓名")
    private String doctorName;

    @Schema(description = "开始日期 yyyy-MM-dd")
    private String startDate;

    @Schema(description = "结束日期 yyyy-MM-dd")
    private String endDate;

    @Schema(description = "星期模式（1=周一, 7=周日）")
    private List<Integer> weekdays;

    @Schema(description = "班次列表")
    private List<String> shifts;

    @Schema(description = "号源总数")
    private Integer totalSlots;

    @Schema(description = "时段拆分方式")
    private String slotSplitMode;

    @Schema(description = "默认时段拆分预览（每班次一套时段配置，所有候选共用）")
    private List<SlotSplitItem> slotSplitPreview;

    @Schema(description = "候选明细：每个 (date, shift) 的去向")
    private List<BatchPreviewItem> items;

    @Schema(description = "可创建候选数（含新建 + 复用 CANCELLED）")
    private int toCreateCount;

    @Schema(description = "将跳过的候选数")
    private int toSkipCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "单班次的默认时段拆分预览")
    public static class SlotSplitItem {
        @Schema(description = "开始时间 HH:mm")
        private String startTime;
        @Schema(description = "结束时间 HH:mm")
        private String endTime;
        @Schema(description = "该时段号源数")
        private Integer count;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "单个候选 (date, shift) 预览")
    public static class BatchPreviewItem {
        @Schema(description = "排班日期")
        private LocalDate scheduleDate;
        @Schema(description = "班次")
        private String shift;
        @Schema(description = "去向：CREATE=新建, REUSE=复用已作废, SKIP=跳过")
        private String action;
        @Schema(description = "跳过原因（action=SKIP 时非空）")
        private String skipReason;
    }
}

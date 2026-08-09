package com.sphp.admin.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量创建排班请求。
 *
 * <p>按"1 个医生 × 日期范围 × 星期模式 × 班次"笛卡尔积展开为若干个排班草稿，
 * 对每个候选 (date, shift) 单独处理冲突：跳过 DRAFT/PUBLISHED 冲突，复用 CANCELLED，新建其他。
 *
 * @author lezoo17
 * @since 2026-08-07
 */
@Data
@Schema(description = "批量创建排班请求")
public class BatchScheduleRequest {

    /** 日期范围上限（天）：超过该范围时拒绝，防误操作 */
    public static final int MAX_DATE_RANGE_DAYS = 90;

    @NotNull(message = "医生ID不能为空")
    @Schema(description = "医生ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long doctorId;

    @NotNull(message = "排班开始日期不能为空")
    @Schema(description = "开始日期 yyyy-MM-dd", example = "2026-09-01",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String startDate;

    @NotNull(message = "排班结束日期不能为空")
    @Schema(description = "结束日期 yyyy-MM-dd（含）", example = "2026-09-30",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String endDate;

    /**
     * 星期模式：1=周一, 2=周二, ..., 7=周日（与 {@link java.time.DayOfWeek#getValue()} 对齐）。
     * 仅范围内匹配这些星期的日期会进入候选集。
     */
    @NotEmpty(message = "星期模式不能为空")
    @Size(min = 1, max = 7, message = "星期模式长度需在 1~7 之间")
    @Schema(description = "星期模式（1=周一, 7=周日），仅范围内命中这些星期的日期参与批量",
            example = "[1,3,5]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Integer> weekdays;

    @NotEmpty(message = "班次不能为空")
    @Schema(description = "班次列表：MORNING / AFTERNOON，可多选",
            example = "[\"MORNING\", \"AFTERNOON\"]",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<@Pattern(regexp = "MORNING|AFTERNOON", message = "班次仅支持 MORNING / AFTERNOON") String> shifts;

    @NotNull(message = "号源总数不能为空")
    @Min(value = 1, message = "号源总数需在 1~99 之间")
    @Max(value = 99, message = "号源总数需在 1~99 之间")
    @Schema(description = "号源总数（1~99，每个候选排班使用同一数值）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer totalSlots;

    @NotNull(message = "时段拆分方式不能为空")
    @Pattern(regexp = "HOURLY|HALF_HOUR|FULL", message = "时段拆分仅支持 HOURLY / HALF_HOUR / FULL")
    @Schema(description = "默认时段拆分方式：HOURLY=1小时/段, HALF_HOUR=30分钟/段, FULL=整段",
            example = "HOURLY", requiredMode = Schema.RequiredMode.REQUIRED)
    private String slotSplitMode;
}

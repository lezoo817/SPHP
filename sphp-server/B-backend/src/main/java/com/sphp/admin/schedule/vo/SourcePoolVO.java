package com.sphp.admin.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 号源池行（按已发布排班明细，每行 = 医生某天某班次）。
 *
 * <p>数据来源：schedule / slot / slot_snapshot 三表实时聚合，只读，不落新表。
 * 科室（诊室）取 {@code schedule.dept_id → department.name}。
 * 剩余号源口径与排班列表一致：已发布时段取 AVAILABLE + RELEASED 快照数（C 端可预约）。
 *
 * @author lezoo17
 * @since 2026-08-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "号源池行")
public class SourcePoolVO {

    @Schema(description = "排班ID")
    private Long scheduleId;

    @Schema(description = "排班日期")
    private LocalDate scheduleDate;

    @Schema(description = "班次：MORNING / AFTERNOON")
    private String shift;

    @Schema(description = "科室（诊室）名称")
    private String deptName;

    @Schema(description = "医生姓名")
    private String doctorName;

    @Schema(description = "总号源数（排班 total_slots）")
    private Long totalSlots;

    @Schema(description = "剩余可约号源数（AVAILABLE + RELEASED 快照数，与 C 端可约口径一致）")
    private Long remainSlots;

    @Schema(description = "已约号源数（SOLD 快照数）")
    private Long soldSlots;

    @Schema(description = "锁定中号源数（LOCKED 快照数）")
    private Long lockedSlots;

    @Schema(description = "是否已过期（PUBLISHED 且 schedule_date < today）")
    private Boolean isExpired;
}

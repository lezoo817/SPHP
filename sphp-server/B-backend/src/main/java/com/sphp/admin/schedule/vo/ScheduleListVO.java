package com.sphp.admin.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 排班列表项响应（系分 §5.4.1）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "排班列表项")
public class ScheduleListVO {

    @Schema(description = "排班ID")
    private Long id;

    @Schema(description = "医生ID")
    private Long doctorId;

    @Schema(description = "医生姓名")
    private String doctorName;

    @Schema(description = "科室ID")
    private Long deptId;

    @Schema(description = "科室名称")
    private String deptName;

    @Schema(description = "排班日期")
    private LocalDate scheduleDate;

    @Schema(description = "班次：MORNING / AFTERNOON")
    private String shift;

    @Schema(description = "号源总数")
    private Integer totalSlots;

    @Schema(description = "已预约号源数（SOLD + LOCKED）")
    private long bookedCount;

    @Schema(description = "剩余可约号源数")
    private long remainCount;

    @Schema(description = "锁定中号源数")
    private long lockedCount;

    @Schema(description = "状态：DRAFT / PUBLISHED / CANCELLED")
    private String status;

    @Schema(description = "发布时间")
    private OffsetDateTime publishedAt;
}

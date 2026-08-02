package com.sphp.admin.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 创建排班响应（系分 §5.4.2）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建排班响应")
public class ScheduleCreateVO {

    @Schema(description = "排班ID")
    private Long id;

    @Schema(description = "状态：DRAFT")
    private String status;

    @Schema(description = "创建时间")
    private OffsetDateTime createdAt;
}

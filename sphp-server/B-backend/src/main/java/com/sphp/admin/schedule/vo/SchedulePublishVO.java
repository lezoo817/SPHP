package com.sphp.admin.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 发布/取消发布排班响应。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "发布/取消发布排班响应")
public class SchedulePublishVO {

    @Schema(description = "排班ID")
    private Long id;

    @Schema(description = "状态：PUBLISHED / CANCELLED")
    private String status;

    @Schema(description = "发布时间（取消发布时为 null）")
    private OffsetDateTime publishedAt;
}

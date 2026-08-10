package com.sphp.admin.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 开始接诊响应 VO。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "开始接诊响应")
public class ConsultStartVO {

    @Schema(description = "问诊记录 ID")
    private Long consultId;

    @Schema(description = "问诊状态：IN_PROGRESS")
    private String status;

    @Schema(description = "开始接诊时间")
    private OffsetDateTime startedAt;
}
package com.sphp.admin.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 保存病历响应 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "保存病历响应")
public class NoteSaveVO {

    @Schema(description = "问诊记录 ID")
    private Long consultId;

    @Schema(description = "更新时间")
    private OffsetDateTime updatedAt;
}
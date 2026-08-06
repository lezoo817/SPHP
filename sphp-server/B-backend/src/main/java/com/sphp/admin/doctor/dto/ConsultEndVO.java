package com.sphp.admin.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 结束问诊响应 VO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "结束问诊响应")
public class ConsultEndVO {

    @Schema(description = "问诊记录 ID")
    private Long consultId;

    @Schema(description = "问诊状态：COMPLETED")
    private String status;

    @Schema(description = "结束问诊时间")
    private OffsetDateTime endedAt;
}
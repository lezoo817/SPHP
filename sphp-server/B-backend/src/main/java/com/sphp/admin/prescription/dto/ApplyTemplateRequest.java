package com.sphp.admin.prescription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 应用处方模板请求体。
 *
 * @author lezoo17
 * @since 2026-08-09
 */
@Data
@Schema(description = "应用处方模板请求")
public class ApplyTemplateRequest {

    @NotNull(message = "问诊记录 ID 不能为空")
    @Schema(description = "问诊记录 ID")
    private Long consultId;
}

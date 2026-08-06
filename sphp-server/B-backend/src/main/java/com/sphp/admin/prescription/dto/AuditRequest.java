package com.sphp.admin.prescription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 审核处方请求。
 */
@Data
@Schema(description = "审核处方请求")
public class AuditRequest {

    @NotBlank(message = "审核动作不能为空")
    @Pattern(regexp = "APPROVED|REJECTED", message = "审核动作仅支持 APPROVED / REJECTED")
    @Schema(description = "审核动作：APPROVED 通过 / REJECTED 驳回", example = "APPROVED")
    private String action;

    @Schema(description = "驳回原因（驳回时必填）", example = "药品剂量超出安全范围")
    private String rejectReason;
}
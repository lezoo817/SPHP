package com.sphp.admin.hospital.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 编辑科室请求。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@Schema(description = "编辑科室请求")
public class DepartmentUpdateRequest {

    @NotBlank(message = "科室名称不能为空")
    @Schema(description = "科室名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "科室负责人医生ID")
    private Long headDoctorId;

    @Schema(description = "科室位置（如：1号楼2层201室）")
    private String location;
}

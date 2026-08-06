package com.sphp.admin.hospital.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 编辑医生请求。
 *
 * <p>字段均可空，仅更新传入的非空值；不修改所属科室（{@code deptId}）。
 */
@Data
@Schema(description = "编辑医生请求")
public class DoctorUpdateRequest {

    @Schema(description = "医生姓名")
    private String name;

    @Schema(description = "职称")
    private String title;

    @Schema(description = "擅长领域")
    private String specialty;

    @Schema(description = "简介")
    private String introduction;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "挂号费（分）")
    private Integer registrationFeeCent;
}

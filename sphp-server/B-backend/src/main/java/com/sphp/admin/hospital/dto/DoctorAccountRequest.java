package com.sphp.admin.hospital.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 修改医生登录账号请求（系分 §5.3.12）。
 */
@Data
@Schema(description = "修改医生登录账号请求")
public class DoctorAccountRequest {

    @NotBlank(message = "登录账号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{4,32}$", message = "登录账号需为4-32位字母、数字或下划线")
    @Schema(description = "新登录账号，4-32位，全院唯一", requiredMode = Schema.RequiredMode.REQUIRED)
    private String account;
}

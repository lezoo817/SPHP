package com.sphp.admin.hospital.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增医生请求。
 *
 * <p>同步开通 {@code DOCTOR} 角色的登录账号，回填 {@code doctor.b_user_id}。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@Schema(description = "新增医生请求")
public class DoctorCreateRequest {

    @NotBlank(message = "医生姓名不能为空")
    @Schema(description = "医生姓名", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull(message = "所属科室不能为空")
    @Schema(description = "所属科室ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long deptId;

    @NotBlank(message = "职称不能为空")
    @Schema(description = "职称：主任医师/副主任医师/主治医师/住院医师",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Schema(description = "擅长领域")
    private String specialty;

    @NotBlank(message = "执业证号不能为空")
    @Schema(description = "执业证号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String licenseNo;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "挂号费（分），默认0")
    private Integer registrationFeeCent;

    @NotBlank(message = "登录账号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{4,32}$", message = "登录账号需为4-32位字母、数字或下划线")
    @Schema(description = "登录账号，4-32位，全院唯一", requiredMode = Schema.RequiredMode.REQUIRED)
    private String account;

    @NotBlank(message = "初始密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需为6-64位")
    @Schema(description = "初始密码，6-64位，jBCrypt哈希入库", requiredMode = Schema.RequiredMode.REQUIRED)
    private String password;

    @Pattern(regexp = "ENABLED|DISABLED|SUSPENDED", message = "状态仅支持 ENABLED / DISABLED / SUSPENDED")
    @Schema(description = "状态，默认ENABLED")
    private String status;
}

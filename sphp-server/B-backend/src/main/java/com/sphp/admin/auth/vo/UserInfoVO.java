package com.sphp.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 登录用户信息。
 *
 * <p>b_user 表无姓名 / 科室列，姓名与科室需经 doctor_id 联查 doctor 表补全；
 * 管理员（{@code doctorId == null}）姓名取登录账号。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录用户信息")
public class UserInfoVO {

    @Schema(description = "用户 ID（b_user.id）")
    private Long id;

    @Schema(description = "用户姓名（关联医生姓名，ADMIN 取账号）")
    private String name;

    @Schema(description = "角色列表（取自 BRoleEnum）", example = "[\"DOCTOR\"]")
    private List<String> roles;

    @Schema(description = "科室 ID（DOCTOR/DEPT_HEAD 有值，ADMIN 为 null）")
    private Long deptId;

    @Schema(description = "医生 ID（DOCTOR/DEPT_HEAD 有值，ADMIN 为 null）")
    private Long doctorId;

    @Schema(description = "医院 ID")
    private Long hospitalId;
}

package com.sphp.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 登录返回的用户信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录用户信息")
public class UserInfoVO {

    @Schema(description = "b_user.id")
    private Long id;

    @Schema(description = "用户姓名（关联医生姓名，ADMIN 取账号）")
    private String name;

    @Schema(description = "角色列表", example = "[\"DOCTOR\"]")
    private List<String> roles;

    @Schema(description = "科室ID（DOCTOR/DEPT_HEAD 有值，ADMIN 为 null）")
    private Long deptId;

    @Schema(description = "医生ID（DOCTOR/DEPT_HEAD 有值，ADMIN 为 null）")
    private Long doctorId;

    @Schema(description = "医院ID")
    private Long hospitalId;
}

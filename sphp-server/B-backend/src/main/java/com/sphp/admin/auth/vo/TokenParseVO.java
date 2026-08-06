package com.sphp.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Token 解析响应（供 Agent 通道调用）。
 *
 * <p>基于 Sa-Token 解析当前请求 accessToken，组装出 Agent 所需的最小用户身份上下文；
 * 医生 / 科室维度由 {@code doctor_id} 联查 doctor 表补全。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Token 解析响应")
public class TokenParseVO {

    @Schema(description = "用户 ID（b_user.id）")
    private Long userId;

    @Schema(description = "登录账号")
    private String account;

    @Schema(description = "角色列表（取自 BRoleEnum）", example = "[\"DOCTOR\"]")
    private List<String> roles;

    @Schema(description = "科室 ID（DOCTOR/DEPT_HEAD 有值，ADMIN 为 null）")
    private Long deptId;

    @Schema(description = "医生 ID（DOCTOR/DEPT_HEAD 有值，ADMIN 为 null）")
    private Long doctorId;

    @Schema(description = "医院 ID")
    private Long hospitalId;

    @Schema(description = "令牌过期时间（ISO-8601；负超时时为 null）")
    private OffsetDateTime tokenExpiresAt;
}

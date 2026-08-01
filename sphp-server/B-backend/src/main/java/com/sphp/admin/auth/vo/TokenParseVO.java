package com.sphp.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Token 解析响应（供 Agent 调用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Token 解析响应")
public class TokenParseVO {

    @Schema(description = "用户ID（b_user.id）")
    private Long userId;

    @Schema(description = "登录账号")
    private String account;

    @Schema(description = "角色列表", example = "[\"DOCTOR\"]")
    private List<String> roles;

    @Schema(description = "科室ID（DOCTOR/DEPT_HEAD 有值，ADMIN 为 null）")
    private Long deptId;

    @Schema(description = "医生ID（DOCTOR/DEPT_HEAD 有值，ADMIN 为 null）")
    private Long doctorId;

    @Schema(description = "医院ID")
    private Long hospitalId;

    @Schema(description = "令牌过期时间（ISO-8601）")
    private OffsetDateTime tokenExpiresAt;
}

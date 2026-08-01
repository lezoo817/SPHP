package com.sphp.admin.auth.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 退出登录响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "退出登录响应")
public class LogoutVO {

    @Schema(description = "是否已退出", example = "true")
    private Boolean loggedOut;
}

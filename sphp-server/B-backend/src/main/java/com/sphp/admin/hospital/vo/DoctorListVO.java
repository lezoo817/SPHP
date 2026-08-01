package com.sphp.admin.hospital.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 医生列表项响应（系分 §5.3.8）。
 *
 * <p>字段说明：{@code hasAccount} 是否存在登录账号；{@code account} 登录账号，
 * 仅当 {@code hasAccount=true} 时有效，否则为 {@code null}。前端展示账号时应先判读 {@code hasAccount}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "医生列表项")
public class DoctorListVO {

    @Schema(description = "医生ID")
    private Long id;

    @Schema(description = "医生姓名")
    private String name;

    @Schema(description = "所属科室ID")
    private Long deptId;

    @Schema(description = "所属科室名称")
    private String deptName;

    @Schema(description = "职称")
    private String title;

    @Schema(description = "擅长领域")
    private String specialty;

    @Schema(description = "执业证号")
    private String licenseNo;

    @Schema(description = "联系电话")
    private String phone;

    @Schema(description = "挂号费（分）")
    private Integer registrationFeeCent;

    @Schema(description = "状态：ENABLED / DISABLED / SUSPENDED")
    private String status;

    @Schema(description = "是否存在登录账号")
    private boolean hasAccount;

    @Schema(description = "登录账号，仅当 hasAccount=true 时有效，否则为 null")
    private String account;
}

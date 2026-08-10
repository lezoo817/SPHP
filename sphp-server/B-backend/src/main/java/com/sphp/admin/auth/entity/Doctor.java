package com.sphp.admin.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 医生表实体（对应表 {@code doctor}）。
 *
 * <p>认证场景仅需 {@code name} / {@code deptId}（用于登录与 token/parse 补全用户上下文）。
 * 状态字段必须取自业务枚举：
 * <ul>
 *   <li>status — {@link com.sphp.admin.common.enums.BUserStatusEnum}（与 b_user 状态机一致）</li>
 * </ul>
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@TableName("doctor")
public class Doctor {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属医院。 */
    private Long hospitalId;

    /** 所属科室。 */
    private Long deptId;

    /** 关联 {@code b_user.id}（冗余字段，加速联查）。 */
    private Long bUserId;

    /** 姓名。 */
    private String name;

    /** 职称：主任医师 / 副主任医师 / 主治医师 / 住院医师。 */
    private String title;

    /** 擅长领域。 */
    private String specialty;

    /** 简介。 */
    private String introduction;

    /** 执业证号。 */
    private String licenseNo;

    /** 联系电话。 */
    private String phone;

    /** 挂号费（分），默认 0。 */
    private Integer registrationFeeCent;

    /**
     * 状态。
     *
     * @see com.sphp.admin.common.enums.BUserStatusEnum
     */
    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;
}

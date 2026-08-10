package com.sphp.admin.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * B 端用户表实体（对应表 {@code b_user}）。
 *
 * <p>注意：b_user 无 {@code name} / {@code dept_id} 列，姓名与科室需经 {@code doctor_id}
 * 联查 doctor 表补全；角色与状态字段必须取自业务枚举：
 * <ul>
 *   <li>role — {@link com.sphp.admin.common.enums.BRoleEnum}</li>
 *   <li>status — {@link com.sphp.admin.common.enums.BUserStatusEnum}</li>
 * </ul>
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@TableName("b_user")
public class BUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号（全院唯一）。 */
    private String account;

    /** 密码哈希（jBCrypt）。 */
    private String passwordHash;

    /**
     * 角色。
     *
     * @see com.sphp.admin.common.enums.BRoleEnum
     */
    private String role;

    /** 所属医院。 */
    private Long hospitalId;

    /**
     * 关联医生 ID。
     *
     * <p>ADMIN 角色为 null；DEPT_HEAD / DOCTOR 角色关联具体医生记录。
     */
    private Long doctorId;

    /**
     * 账号状态。
     *
     * @see com.sphp.admin.common.enums.BUserStatusEnum
     */
    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    /** 软删除时间（{@code null} 表示有效）。 */
    private OffsetDateTime deletedAt;
}

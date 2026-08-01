package com.sphp.admin.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * B端用户表实体（对应表 b_user）。
 *
 * <p>注意：b_user 无 name/dept_id 列，姓名与科室需经 doctor_id 联查 doctor 表。
 */
@Data
@TableName("b_user")
public class BUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录账号（全院唯一） */
    private String account;

    /** 密码哈希（jBCrypt） */
    private String passwordHash;

    /** 角色：ADMIN / DEPT_HEAD / DOCTOR */
    private String role;

    /** 所属医院 */
    private Long hospitalId;

    /** 关联医生（ADMIN 为 null） */
    private Long doctorId;

    /** 账号状态：ENABLED / DISABLED */
    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    /** 软删除时间（null 表示有效） */
    private OffsetDateTime deletedAt;
}

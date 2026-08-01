package com.sphp.admin.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 医生表实体（对应表 doctor）。
 *
 * <p>认证场景仅需 name/dept_id（用于登录与 token/parse 补全用户上下文）。
 */
@Data
@TableName("doctor")
public class Doctor {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属医院 */
    private Long hospitalId;

    /** 所属科室 */
    private Long deptId;

    /** 关联 b_user.id（冗余，加速联查） */
    private Long bUserId;

    /** 姓名 */
    private String name;

    /** 状态：ENABLED / DISABLED / SUSPENDED */
    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;
}

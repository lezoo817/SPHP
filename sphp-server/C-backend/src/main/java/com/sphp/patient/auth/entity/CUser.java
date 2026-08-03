package com.sphp.patient.auth.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sphp.shared.entity.BaseDeleteDO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * C端用户账号实体。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("c_user")
public class CUser extends BaseDeleteDO {

    /** 唯一登录账号 */
    @TableField("account")
    private String account;

    /** BCrypt 密码摘要 */
    @TableField("password_hash")
    private String passwordHash;

    /** 账号状态 */
    @TableField("status")
    private String status;
}

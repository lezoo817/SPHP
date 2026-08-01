package com.sphp.patient.auth.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * C端刷新令牌持久化实体，仅保存令牌摘要。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("c_refresh_token")
public class CRefreshToken {

    /** 数据库自增主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 所属 C端用户 ID */
    @TableField("user_id")
    private Long userId;
    /** Refresh Token SHA-256 摘要 */
    @TableField("token_hash")
    private String tokenHash;
    /** 令牌过期时间 */
    @TableField("expired_at")
    private OffsetDateTime expiredAt;
    /** 令牌撤销时间 */
    @TableField("revoked_at")
    private OffsetDateTime revokedAt;
    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}

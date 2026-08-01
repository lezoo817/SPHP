package com.sphp.admin.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * B端刷新令牌表实体（对应表 b_refresh_token）。
 *
 * <p>只存 token 的 SHA-256 哈希，原文仅返回客户端；revoked_at 非空表示已吊销。
 */
@Data
@TableName("b_refresh_token")
public class BRefreshToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 b_user.id */
    private Long userId;

    /** 刷新令牌 SHA-256 哈希 */
    private String tokenHash;

    /** 过期时间 */
    private OffsetDateTime expiredAt;

    /** 吊销时间（null 表示有效） */
    private OffsetDateTime revokedAt;

    private OffsetDateTime createdAt;
}

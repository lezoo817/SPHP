package com.sphp.admin.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * B 端刷新令牌表实体（对应表 {@code b_refresh_token}）。
 *
 * <p>仅存储 token 的 SHA-256 哈希，原文仅在签发瞬间返回给客户端一次；
 * {@code revokedAt} 非空表示已吊销（含主动退出 / 轮换 / 管理员吊销）。
 */
@Data
@TableName("b_refresh_token")
public class BRefreshToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 {@code b_user.id}。 */
    private Long userId;

    /** 刷新令牌 SHA-256 哈希（16 进制小写）。 */
    private String tokenHash;

    /** 过期时间（UTC）。 */
    private OffsetDateTime expiredAt;

    /** 吊销时间（{@code null} 表示有效）。 */
    private OffsetDateTime revokedAt;

    private OffsetDateTime createdAt;
}

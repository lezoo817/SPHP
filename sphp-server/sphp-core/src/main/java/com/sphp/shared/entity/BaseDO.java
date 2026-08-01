package com.sphp.shared.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 持久化实体公共审计字段基类。
 *
 * <p>字段类型与 PostgreSQL 的 {@code bigint}、{@code timestamptz} 保持一致。
 */
@Getter
@Setter
public class BaseDO implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 数据库自增主键 */
    @TableId(value = "id", type = IdType.AUTO)
    protected Long id;

    /** 创建时间，由插入自动填充 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    protected OffsetDateTime createdAt;

    /** 更新时间，由插入和更新自动填充 */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    protected OffsetDateTime updatedAt;
}

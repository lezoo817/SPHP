package com.sphp.admin.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;

/** 药房表实体（对应表 pharmacy）。 */
@Data
@TableName("pharmacy")
public class Pharmacy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long hospitalId;
    private String name;
    private String address;
    private String phone;

    /**
     * 是否默认药房。
     *
     * <p>字段名避开 {@code is} 前缀（CLAUDE.md 4.3 POJO 布尔命名红线），DB 列仍为
     * {@code is_default}；JSON 属性经 {@code @JsonProperty} 保持 {@code isDefault} 以维持 API 契约。
     */
    @TableField("is_default")
    @JsonProperty("isDefault")
    private Boolean defaultFlag;

    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
}

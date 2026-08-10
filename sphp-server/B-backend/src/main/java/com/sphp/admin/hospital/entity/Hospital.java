package com.sphp.admin.hospital.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 医院表实体（对应表 hospital）。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@TableName("hospital")
public class Hospital {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 医院名称 */
    private String name;

    /** 医院等级（如 三甲） */
    private String level;

    /** 医院简介 */
    private String description;

    /** 地址 */
    private String address;

    /** 联系方式 */
    private String contact;

    /** 状态：ENABLED / DISABLED */
    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    /** 软删除时间（null 表示有效） */
    private OffsetDateTime deletedAt;
}

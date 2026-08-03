package com.sphp.admin.prescription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 药品目录表实体（对应表 drug）。
 */
@Data
@TableName("drug")
public class Drug {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属医院 ID */
    private Long hospitalId;

    /** 药品名称 */
    private String name;

    /** 规格 */
    private String specification;

    /** 生产厂家 */
    private String manufacturer;

    /** 批准文号 */
    private String approvalNumber;

    /** 单位（默认盒） */
    private String unit;

    /** 适应症 */
    private String indication;

    /** 禁忌症 */
    private String contraindication;

    /** 副作用 */
    private String sideEffect;

    /** 状态：ENABLED / DISABLED */
    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;
}
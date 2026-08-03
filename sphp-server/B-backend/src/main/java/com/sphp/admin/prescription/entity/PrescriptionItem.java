package com.sphp.admin.prescription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 处方明细表实体（对应表 prescription_item）。
 */
@Data
@TableName("prescription_item")
public class PrescriptionItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 处方 ID */
    private Long prescriptionId;

    /** 药品 ID */
    private Long drugId;

    /** 用量（如 QD / BID / TID） */
    private String dosage;

    /** 频次描述 */
    private String frequency;

    /** 用法（如口服 / 外用 / 注射） */
    private String usageMethod;

    /** 用药天数 */
    private Integer days;

    /** 数量（盒/瓶） */
    private Integer quantity;

    private OffsetDateTime createdAt;
}
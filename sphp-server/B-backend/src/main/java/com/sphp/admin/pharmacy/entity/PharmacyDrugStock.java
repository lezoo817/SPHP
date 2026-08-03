package com.sphp.admin.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** 药房药品库存表实体（对应表 pharmacy_drug_stock）。 */
@Data
@TableName("pharmacy_drug_stock")
public class PharmacyDrugStock {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pharmacyId;
    private Long drugId;
    private Integer availableCount;
    private Integer lockedCount;
    private Integer safetyStock;
    private Integer unitPriceCent;
    private OffsetDateTime updatedAt;
}

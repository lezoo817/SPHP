package com.sphp.admin.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** 购药订单表实体（对应表 drug_order，仅 B 端预校验只读使用）。 */
@Data
@TableName("drug_order")
public class DrugOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long patientId;
    private Long prescriptionId;
    private Long pharmacyId;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
}

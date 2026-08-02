package com.sphp.patient.consultation.entity;

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
 * 处方药品明细实体。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("prescription_item")
public class ConsultationPrescriptionItem {

    /** 主键 ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    /** 关联处方 ID */
    @TableField("prescription_id")
    private Long prescriptionId;
    /** 药品 ID */
    @TableField("drug_id")
    private Long drugId;
    /** 单次用量 */
    @TableField("dosage")
    private String dosage;
    /** 用药频次 */
    @TableField("frequency")
    private String frequency;
    /** 用药方式 */
    @TableField("usage_method")
    private String usageMethod;
    /** 用药天数 */
    @TableField("days")
    private Short days;
    /** 药品数量 */
    @TableField("quantity")
    private Short quantity;
    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}

package com.sphp.patient.order.entity;

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
 * 购药订单药品明细实体。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("drug_order_item")
public class DrugOrderItem {

    /** 主键 ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 购药订单 ID */
    @TableField("drug_order_id")
    private Long drugOrderId;

    /** 药品 ID */
    @TableField("drug_id")
    private Long drugId;

    /** 下单时药品名称快照 */
    @TableField("drug_name_snapshot")
    private String drugNameSnapshot;

    /** 购买数量 */
    @TableField("quantity")
    private Integer quantity;

    /** 下单时单价，单位分 */
    @TableField("unit_price_cent")
    private Integer unitPriceCent;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}

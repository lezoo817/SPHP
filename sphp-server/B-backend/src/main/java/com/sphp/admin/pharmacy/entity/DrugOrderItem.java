package com.sphp.admin.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** 购药订单明细表实体（对应表 drug_order_item，仅 B 端预校验只读使用）。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@TableName("drug_order_item")
public class DrugOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long drugOrderId;
    private Long drugId;
    private String drugNameSnapshot;
    private Integer quantity;
    private Integer unitPriceCent;
    private OffsetDateTime createdAt;
}

package com.sphp.patient.order.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sphp.shared.entity.BaseDeleteDO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * C端购药订单实体。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("drug_order")
public class DrugOrder extends BaseDeleteDO {

    /** 就诊人 ID */
    @TableField("patient_id")
    private Long patientId;
    /** 已批准处方 ID */
    @TableField("prescription_id")
    private Long prescriptionId;
    /** 选定药房 ID */
    @TableField("pharmacy_id")
    private Long pharmacyId;
    /** 药房名称快照 */
    @TableField("pharmacy_name_snapshot")
    private String pharmacyNameSnapshot;
    /** 配送方式，固定 COURIER */
    @TableField("delivery_method")
    private String deliveryMethod;
    /** 收货地址 */
    @TableField("delivery_address")
    private String deliveryAddress;
    /** 订单状态 */
    @TableField("status")
    private String status;
    /** 物流状态 */
    @TableField("logistics_status")
    private String logisticsStatus;
    /** 订单金额，单位分 */
    @TableField("amount_cent")
    private Integer amountCent;
    /** 支付到期时间 */
    @TableField("expire_at")
    private OffsetDateTime expireAt;
    /** 确认收货时间 */
    @TableField("received_at")
    private OffsetDateTime receivedAt;
}

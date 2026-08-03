package com.sphp.patient.registration.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sphp.shared.entity.BaseDO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * C端挂号支付单实体。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("payment_order")
public class RegisteringPaymentOrder extends BaseDO {

    /** 关联挂号订单 ID */
    @TableField("appointment_id")
    private Long appointmentId;

    /** 付款 C端用户 ID */
    @TableField("payer_user_id")
    private Long payerUserId;

    /** 支付金额，单位分 */
    @TableField("amount_cent")
    private Integer amountCent;

    /** 支付单状态 */
    @TableField("status")
    private String status;

    /** 支付到期时间 */
    @TableField("expire_at")
    private OffsetDateTime expireAt;

    /** 支付完成时间 */
    @TableField("paid_at")
    private OffsetDateTime paidAt;
}

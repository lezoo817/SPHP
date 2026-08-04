package com.sphp.patient.registration.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sphp.shared.entity.BaseDeleteDO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * C端挂号订单实体。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("appointment")
public class RegisteringAppointment extends BaseDeleteDO {

    /** 关联号源快照 ID */
    @TableField("slot_snapshot_id")
    private Long slotSnapshotId;
    /** 就诊人 ID */
    @TableField("patient_id")
    private Long patientId;

    /** 医生 ID */
    @TableField("doctor_id")
    private Long doctorId;

    /** 订单状态 */
    @TableField("status")
    private String status;

    /** 挂号金额，单位分 */
    @TableField("amount_cent")
    private Integer amountCent;

    /** 支付到期时间 */
    @TableField("expire_at")
    private OffsetDateTime expireAt;

    /** 支付完成时间 */
    @TableField("paid_at")
    private OffsetDateTime paidAt;

    /** 取消时间 */
    @TableField("cancelled_at")
    private OffsetDateTime cancelledAt;
}

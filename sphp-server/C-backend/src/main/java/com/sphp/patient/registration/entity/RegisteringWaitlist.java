package com.sphp.patient.registration.entity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sphp.shared.entity.BaseDeleteDO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/** C端挂号候补登记实体。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("appointment_waitlist")
public class RegisteringWaitlist extends BaseDeleteDO {

    /** 登记候补的 C 端用户 ID */
    @TableField("user_id")
    private Long userId;

    /** 就诊人 ID */
    @TableField("patient_id")
    private Long patientId;

    /** 时段 ID */
    @TableField("slot_id")
    private Long slotId;

    /** 排队号 */
    @TableField("queue_no")
    private Integer queueNo;

    /** 候补状态 */
    @TableField("status")
    private String status;

    /** 首次通知可预约的时间 */
    @TableField("notified_at")
    private OffsetDateTime notifiedAt;

    /** 候补人成功锁定挂号订单的时间 */
    @TableField("fulfilled_at")
    private OffsetDateTime fulfilledAt;
}

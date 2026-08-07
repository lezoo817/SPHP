package com.sphp.patient.health.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.sphp.shared.entity.BaseDeleteDO;
import lombok.*;
import java.time.OffsetDateTime;

/** 用药计划实体。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("medication_plan")
public class ProposalMedicationPlan extends BaseDeleteDO {

    // 创建该计划的购药订单 ID；历史计划可为空
    @TableField("drug_order_id")
    private Long drugOrderId;

    // 目标就诊人 ID
    @TableField("patient_id")
    private Long patientId;

    // 药品名称快照
    @TableField("drug_name_snapshot")
    private String drugNameSnapshot;

    // 用药剂量快照
    private String dosage;

    // 用药频率快照
    private String frequency;

    // 下次提醒时间
    @TableField("next_remind_at")
    private OffsetDateTime nextRemindAt;

    // 是否由用户手动开启用药提醒
    @TableField("reminder_enabled")
    private Boolean reminderEnabled;

    // 根据处方频次生成的每日提醒时刻 JSON 数组
    @TableField("reminder_times")
    private String reminderTimesJson;

    // 状态
    private String status;

    // 结束时间
    @TableField("end_at")
    private OffsetDateTime endAt;

}

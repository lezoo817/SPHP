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

    // 状态
    private String status;

    // 结束时间
    @TableField("end_at")
    private OffsetDateTime endAt;

}

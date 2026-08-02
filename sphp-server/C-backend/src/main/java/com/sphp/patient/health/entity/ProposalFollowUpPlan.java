package com.sphp.patient.health.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.sphp.shared.entity.BaseDeleteDO;
import lombok.*;
import java.time.OffsetDateTime;

/** 随访计划实体。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("follow_up_plan")
public class ProposalFollowUpPlan extends BaseDeleteDO {

    // 就诊人 ID
    @TableField("patient_id")
    private Long patientId;

    // 随访计划类型
    @TableField("follow_up_type")
    private String followUpType;

    // 随访计划内容
    private String content;

    // 随访计划截止日期
    @TableField("due_at")
    private OffsetDateTime dueAt;

    // 随访计划提醒时间
    @TableField("remind_at")
    private OffsetDateTime remindAt;

    // 随访计划状态
    private String status;
}

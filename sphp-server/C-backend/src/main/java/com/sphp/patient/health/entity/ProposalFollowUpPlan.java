package com.sphp.patient.health.entity;
import com.baomidou.mybatisplus.annotation.*; import com.sphp.shared.entity.BaseDeleteDO; import lombok.*; import java.time.OffsetDateTime;
/** 随访计划实体。 */ @Getter @Setter @NoArgsConstructor @TableName("follow_up_plan") public class ProposalFollowUpPlan extends BaseDeleteDO { @TableField("patient_id") private Long patientId; @TableField("follow_up_type") private String followUpType; private String content; @TableField("due_at") private OffsetDateTime dueAt; @TableField("remind_at") private OffsetDateTime remindAt; private String status; }

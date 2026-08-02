package com.sphp.patient.health.entity;
import com.baomidou.mybatisplus.annotation.*; import lombok.*; import java.time.OffsetDateTime;
/** 检查报告指标实体。 */ @Getter @Setter @NoArgsConstructor @TableName("patient_report_indicator") public class ProposalReportIndicator { @TableId(value="id",type=IdType.AUTO) private Long id; @TableField("report_id") private Long reportId; private String name; private String value; private String unit; @TableField("reference_range") private String referenceRange; @TableField(value="created_at",fill=FieldFill.INSERT) private OffsetDateTime createdAt; }

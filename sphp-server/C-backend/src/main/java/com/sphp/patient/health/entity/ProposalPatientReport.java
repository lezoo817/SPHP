package com.sphp.patient.health.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.sphp.shared.entity.BaseDeleteDO;
import lombok.*; import java.time.LocalDate;

/** 患者检查报告实体。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("patient_report")
public class ProposalPatientReport extends BaseDeleteDO {
    /** 检查报告 ID */
    @TableField("patient_id")
    private Long patientId;

    /** 检查报告名称 */
    @TableField("report_name")
    private String reportName;

    /** 检查报告日期 */
    @TableField("report_date")
    private LocalDate reportDate;

    /** 检查报告解读状态 */
    @TableField("interpretation_status")
    private String interpretationStatus;

    /** 检查报告解读内容 */
    @TableField("interpretation")
    private String interpretation;
}

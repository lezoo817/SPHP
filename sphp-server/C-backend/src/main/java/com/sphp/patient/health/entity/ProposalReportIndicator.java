package com.sphp.patient.health.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.*;
import java.time.OffsetDateTime;


/** 检查报告指标实体。 */
@Getter
@Setter
@NoArgsConstructor
@TableName("patient_report_indicator")
public class ProposalReportIndicator {
    /** 主键 ID */
    @TableId(value="id",type=IdType.AUTO)
    private Long id;

    /** 检查报告 ID */
    @TableField("report_id")
    private Long reportId;

    /** 指标名称 */
    private String name;

    /** 值 */
    private String value;

    /** 单位 */
    private String unit;

    /** 参考范围 */
    @TableField("reference_range")
    private String referenceRange;

    /** 创建时间 */
    @TableField(value="created_at",fill=FieldFill.INSERT)
    private OffsetDateTime createdAt;

}

package com.sphp.patient.consultation.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sphp.shared.entity.BaseDeleteDO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 处方解读结果实体。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("prescription_interpretation")
public class PrescriptionInterpretation extends BaseDeleteDO {

    /** 关联的处方 ID。 */
    @TableField("prescription_id")
    private Long prescriptionId;

    /** 面向患者展示的处方解读内容。 */
    private String content;

    /** 医疗免责声明。 */
    private String disclaimer;

    /** 解读生成状态。 */
    private String status;

    /** 解读生成完成时间。 */
    @TableField("generated_at")
    private OffsetDateTime generatedAt;
}

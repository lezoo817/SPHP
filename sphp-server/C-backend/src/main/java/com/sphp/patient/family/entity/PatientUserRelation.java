package com.sphp.patient.family.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sphp.shared.entity.BaseDeleteDO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * C端用户与就诊人关系实体。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("patient_user_relation")
public class PatientUserRelation extends BaseDeleteDO {

    /** C端用户 ID */
    @TableField("user_id")
    private Long userId;
    /** 就诊人 ID */
    @TableField("patient_id")
    private Long patientId;
    /** 与账号拥有者的关系 */
    @TableField("relationship")
    private String relationship;
    /** 是否为默认就诊人 */
    @TableField("is_default")
    private Boolean isDefault;
}

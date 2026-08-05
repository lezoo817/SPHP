package com.sphp.patient.consultation.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sphp.shared.entity.BaseDeleteDO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * C端问诊记录实体。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("consult_record")
public class ConsultationRecord extends BaseDeleteDO {

    /** 关联的挂号订单 ID */
    @TableField("appointment_id")
    private Long appointmentId;

    /** 接诊医生 ID */
    @TableField("doctor_id")
    private Long doctorId;

    /** 就诊人 ID */
    @TableField("patient_id")
    private Long patientId;

    /** 问诊状态 */
    @TableField("status")
    private String status;

    /** Agent 汇总的患者主诉 */
    @TableField("chief_complaint")
    private String chiefComplaint;

    /** 现病史补充 */
    @TableField("history_of_present_illness")
    private String historyOfPresentIllness;

    /** 附件 JSON 数组 */
    @TableField("attachments")
    private String attachmentsJson;

    /** AI 总结及本人健康档案快照 JSON */
    @TableField("ai_summary")
    private String aiSummaryJson;

    /** 预问诊提交为待接诊的时间 */
    @TableField("pre_consultation_submitted_at")
    private OffsetDateTime preConsultationSubmittedAt;
}

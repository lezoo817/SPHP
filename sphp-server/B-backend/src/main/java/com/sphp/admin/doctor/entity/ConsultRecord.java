package com.sphp.admin.doctor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 问诊记录表实体（对应表 consult_record）。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@TableName("consult_record")
public class ConsultRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 挂号订单 ID */
    private Long appointmentId;

    /** 医生 ID */
    private Long doctorId;

    /** 患者 ID */
    private Long patientId;

    /** 状态：PENDING / IN_PROGRESS / COMPLETED / NO_SHOW */
    private String status;

    /** AI 预问诊摘要（jsonb） */
    private String aiSummary;

    /** 医生病历文本 */
    private String doctorNote;

    /** 患者主诉 */
    private String chiefComplaint;

    /** 现病史补充 */
    private String historyOfPresentIllness;

    /** 预问诊提交时间 */
    private OffsetDateTime preConsultationSubmittedAt;

    /** 开始接诊时间 */
    private OffsetDateTime startedAt;

    /** 结束问诊时间 */
    private OffsetDateTime endedAt;

    /** 在线问诊医生最终回复时间 */
    private OffsetDateTime doctorRepliedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    /** 软删除时间（null 表示有效） */
    private OffsetDateTime deletedAt;
}

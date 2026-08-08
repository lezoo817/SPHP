package com.sphp.patient.consultation.mapper;

import java.time.OffsetDateTime;

/**
 * 问诊详情投影。
 *
 * @param id 问诊记录 ID
 * @param appointmentId 关联挂号订单 ID，在线问诊为空
 * @param patientId 就诊人 ID
 * @param status 问诊状态
 * @param doctorId 医生 ID
 * @param doctorName 医生姓名
 * @param doctorTitle 医生职称
 * @param chiefComplaint 患者主诉
 * @param historyOfPresentIllness 现病史补充
 * @param attachmentsJson 附件 JSON 数组
 * @param savedAt 最近保存时间
 * @param submittedAt 预问诊提交时间
 */
public record ConsultationDetailRecord(Long id, Long appointmentId, Long patientId, String status,
                                       Long doctorId, String doctorName,
                                       String doctorTitle, String chiefComplaint, String historyOfPresentIllness,
                                       String attachmentsJson, OffsetDateTime savedAt, OffsetDateTime submittedAt) {
}

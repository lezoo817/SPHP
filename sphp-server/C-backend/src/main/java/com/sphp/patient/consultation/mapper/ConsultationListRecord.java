package com.sphp.patient.consultation.mapper;

import java.time.OffsetDateTime;

/**
 * 问诊记录列表投影。
 *
 * @param id 问诊记录 ID
 * @param appointmentId 关联挂号订单 ID
 * @param doctorName 医生姓名
 * @param status 问诊状态
 * @param updatedAt 最近更新时间
 */
public record ConsultationListRecord(Long id, Long appointmentId, String doctorName, String status,
                                     OffsetDateTime updatedAt) {
}

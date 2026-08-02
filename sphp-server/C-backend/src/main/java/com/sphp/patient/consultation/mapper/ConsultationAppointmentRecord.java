package com.sphp.patient.consultation.mapper;

/**
 * 创建预问诊时锁定的挂号订单投影。
 *
 * @param id 挂号订单 ID
 * @param patientId 就诊人 ID
 * @param doctorId 接诊医生 ID
 * @param status 挂号订单状态
 */
public record ConsultationAppointmentRecord(Long id, Long patientId, Long doctorId, String status) {
}

package com.sphp.admin.doctor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.admin.doctor.entity.PatientMedicalHistory;

/**
 * B端患者既往史表 Mapper。
 *
 * <p>注意：C端存在同名 {@code patientMedicalHistoryMapper} bean，此处显式命名避免冲突。
 */
public interface BPatientMedicalHistoryMapper extends BaseMapper<PatientMedicalHistory> {
}
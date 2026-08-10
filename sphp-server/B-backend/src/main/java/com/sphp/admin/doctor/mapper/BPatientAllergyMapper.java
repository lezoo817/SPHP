package com.sphp.admin.doctor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.admin.doctor.entity.PatientAllergy;

/**
 * B端患者过敏史表 Mapper。
 *
 * <p>注意：C端存在同名 {@code patientAllergyMapper} bean，此处显式命名避免冲突。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
public interface BPatientAllergyMapper extends BaseMapper<PatientAllergy> {
}
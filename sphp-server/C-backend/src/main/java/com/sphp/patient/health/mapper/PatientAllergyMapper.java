package com.sphp.patient.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.patient.health.entity.PatientAllergy;
import org.apache.ibatis.annotations.Mapper;

/**
 * 患者过敏史数据访问接口。
 */
@Mapper
public interface PatientAllergyMapper extends BaseMapper<PatientAllergy> {
}

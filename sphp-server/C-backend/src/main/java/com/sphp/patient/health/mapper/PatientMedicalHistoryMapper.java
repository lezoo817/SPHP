package com.sphp.patient.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.patient.health.entity.PatientMedicalHistory;
import org.apache.ibatis.annotations.Mapper;

/**
 * 患者既往史数据访问接口。
 */
@Mapper
public interface PatientMedicalHistoryMapper extends BaseMapper<PatientMedicalHistory> {
}

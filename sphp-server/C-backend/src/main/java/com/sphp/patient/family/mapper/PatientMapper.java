package com.sphp.patient.family.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.patient.family.entity.Patient;
import org.apache.ibatis.annotations.Mapper;

/**
 * 就诊人数据访问接口。
 */
@Mapper
public interface PatientMapper extends BaseMapper<Patient> {
}

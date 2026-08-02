package com.sphp.patient.consultation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.patient.consultation.entity.ConsultationPrescription;
import org.apache.ibatis.annotations.Mapper;

/**
 * 处方基础数据访问接口。
 */
@Mapper
public interface ConsultationPrescriptionMapper extends BaseMapper<ConsultationPrescription> {
}

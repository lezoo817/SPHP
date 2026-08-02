package com.sphp.patient.consultation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.patient.consultation.entity.ConsultationRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 问诊记录基础数据访问接口。
 */
@Mapper
public interface ConsultationRecordMapper extends BaseMapper<ConsultationRecord> {
}

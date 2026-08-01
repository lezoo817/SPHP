package com.sphp.patient.family.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.patient.family.entity.PatientUserRelation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户与就诊人关系数据访问接口。
 */
@Mapper
public interface PatientUserRelationMapper extends BaseMapper<PatientUserRelation> {
}

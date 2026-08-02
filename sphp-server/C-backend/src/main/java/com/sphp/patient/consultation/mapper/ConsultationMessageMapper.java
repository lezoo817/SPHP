package com.sphp.patient.consultation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.patient.consultation.entity.ConsultationMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 问诊文字消息基础数据访问接口。
 */
@Mapper
public interface ConsultationMessageMapper extends BaseMapper<ConsultationMessage> {
}

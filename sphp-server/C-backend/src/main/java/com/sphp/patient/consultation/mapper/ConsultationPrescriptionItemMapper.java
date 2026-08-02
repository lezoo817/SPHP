package com.sphp.patient.consultation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.patient.consultation.entity.ConsultationPrescriptionItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 处方药品明细基础数据访问接口。
 */
@Mapper
public interface ConsultationPrescriptionItemMapper extends BaseMapper<ConsultationPrescriptionItem> {
}

package com.sphp.patient.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.patient.order.entity.DrugOrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 购药订单明细基础数据访问接口。
 */
@Mapper
public interface DrugOrderItemMapper extends BaseMapper<DrugOrderItem> {
}

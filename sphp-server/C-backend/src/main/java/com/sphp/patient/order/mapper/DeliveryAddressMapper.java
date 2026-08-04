package com.sphp.patient.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.patient.order.entity.DeliveryAddress;
import org.apache.ibatis.annotations.Mapper;

/**
 * C端收货地址基础数据访问接口。
 */
@Mapper
public interface DeliveryAddressMapper extends BaseMapper<DeliveryAddress> {
}

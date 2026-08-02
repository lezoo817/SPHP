package com.sphp.patient.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.patient.order.entity.DrugOrderPayment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 购药支付单基础数据访问接口。
 */
@Mapper
public interface DrugOrderPaymentMapper extends BaseMapper<DrugOrderPayment> {
}

package com.sphp.patient.registration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.patient.registration.entity.RegisteringPaymentOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * C端挂号支付单数据访问接口。
 */
@Mapper
public interface RegisteringPaymentOrderMapper extends BaseMapper<RegisteringPaymentOrder> {
}

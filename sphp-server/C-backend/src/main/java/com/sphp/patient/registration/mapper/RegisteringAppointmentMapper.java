package com.sphp.patient.registration.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.patient.registration.entity.RegisteringAppointment;
import org.apache.ibatis.annotations.Mapper;

/**
 * C端挂号订单数据访问接口。
 */
@Mapper
public interface RegisteringAppointmentMapper extends BaseMapper<RegisteringAppointment> {
}

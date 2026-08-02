package com.sphp.patient.registration.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.patient.registration.entity.RegisteringWaitlist;
import org.apache.ibatis.annotations.Mapper;
/** C端挂号候补登记数据访问接口。 */
@Mapper public interface RegisteringWaitlistMapper extends BaseMapper<RegisteringWaitlist> { }

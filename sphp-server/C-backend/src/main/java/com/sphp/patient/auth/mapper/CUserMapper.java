package com.sphp.patient.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.patient.auth.entity.CUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * C端用户账号数据访问接口。
 */
@Mapper
public interface CUserMapper extends BaseMapper<CUser> {
}

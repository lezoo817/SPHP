package com.sphp.patient.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.patient.auth.entity.CRefreshToken;
import org.apache.ibatis.annotations.Mapper;

/**
 * C端刷新令牌数据访问接口。
 */
@Mapper
public interface CRefreshTokenMapper extends BaseMapper<CRefreshToken> {
}

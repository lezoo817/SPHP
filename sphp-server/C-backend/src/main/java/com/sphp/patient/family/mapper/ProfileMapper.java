package com.sphp.patient.family.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;


/**
 * C端本人资料跨表数据访问接口。
 */
@Mapper
public interface ProfileMapper {

    /**
     * 查询当前账号有效 SELF 关系关联的本人资料。
     *
     * @param userId 当前 C端用户 ID
     * @return 本人资料，不存在或已软删除时返回 null
     */
    ProfileRecord selectSelfProfile(@Param("userId") Long userId);

}

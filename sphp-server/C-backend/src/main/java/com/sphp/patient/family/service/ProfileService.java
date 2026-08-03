package com.sphp.patient.family.service;

import com.sphp.patient.family.vo.ProfileVO;

/**
 * C端当前账号本人资料服务。
 */
public interface ProfileService {

    /**
     * 查询当前登录账号本人资料。
     *
     * @return 脱敏后的本人资料
     */
    ProfileVO getProfile();
}

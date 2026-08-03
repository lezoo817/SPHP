package com.sphp.patient.family.service;

import com.sphp.patient.family.dto.ProfileUpdateRequest;
import com.sphp.patient.family.vo.ProfileUpdateVO;
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

    /**
     * 更新当前登录账号本人资料。
     *
     * @param request 本人资料更新请求
     * @return 更新后的最小资料摘要
     */
    ProfileUpdateVO updateProfile(ProfileUpdateRequest request);
}

package com.sphp.patient.family.service.impl;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.family.mapper.ProfileMapper;
import com.sphp.patient.family.mapper.ProfileRecord;
import com.sphp.patient.family.service.ProfileService;
import com.sphp.patient.family.vo.ProfileVO;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * C端当前账号本人资料服务实现。
 */
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private final ProfileMapper profileMapper;

    /**
     * 查询当前登录账号本人资料。
     *
     * @return 脱敏后的本人资料
     * @throws CAuthException 本人资料不存在或已软删除时抛出
     */
    @Override
    public ProfileVO getProfile() {
        Long userId = CUserContext.getRequired().userId();
        ProfileRecord profile = requireSelfProfile(userId);
        return ProfileVO.builder()
                .id(profile.getPatientId())
                .name(profile.getName())
                .gender(profile.getGender())
                .birthday(profile.getBirthday())
                .phone(maskPhone(profile.getPhone()))
                .emergencyContact(maskEmergencyContact(profile.getEmergencyContact()))
                .build();
    }

    /**
     * 读取当前用户有效 SELF 关系的本人资料。
     *
     * @param userId 当前 C端用户 ID
     * @return 本人资料记录
     * @throws CAuthException 本人关系或患者记录不存在时抛出
     */
    private ProfileRecord requireSelfProfile(Long userId) {
        ProfileRecord profile = profileMapper.selectSelfProfile(userId);
        if (profile == null) {
            throw new CAuthException(ErrorCodeEnum.INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "本人资料不存在");
        }
        return profile;
    }

    /**
     * 按手机号脱敏规则隐藏中间四位。
     *
     * @param phone 手机号原始值
     * @return 脱敏手机号或 null
     */
    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        if (phone.length() != 11) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /**
     * 隐藏紧急联系人文本中出现的大陆手机号。
     *
     * @param emergencyContact 紧急联系人原始文本
     * @return 脱敏后的紧急联系人文本或 null
     */
    private String maskEmergencyContact(String emergencyContact) {
        if (!StringUtils.hasText(emergencyContact)) {
            return null;
        }
        return emergencyContact.replaceAll("(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)", "$1****$2");
    }
}

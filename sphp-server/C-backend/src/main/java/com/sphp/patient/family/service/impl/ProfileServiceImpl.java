package com.sphp.patient.family.service.impl;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.common.enums.GenderEnum;
import com.sphp.patient.family.dto.ProfileUpdateRequest;
import com.sphp.patient.family.mapper.ProfileMapper;
import com.sphp.patient.family.mapper.ProfileRecord;
import com.sphp.patient.family.service.ProfileService;
import com.sphp.patient.family.vo.ProfileUpdateVO;
import com.sphp.patient.family.vo.ProfileVO;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;

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
     * 更新当前登录账号本人资料。
     *
     * @param request 本人资料更新请求
     * @return 更新后的最小资料摘要
     * @throws CAuthException 参数非法、本人资料不存在或并发更新冲突时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProfileUpdateVO updateProfile(ProfileUpdateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        validateUpdateRequest(request);
        ProfileRecord existing = requireSelfProfile(userId);
        OffsetDateTime updatedAt = OffsetDateTime.now();

        // 可选字段为 null 时保留已存资料，避免客户端局部更新覆盖既有信息
        String gender = request.getGender() == null ? existing.getGender() : request.getGender();
        LocalDate birthday = request.getBirthday() == null ? existing.getBirthday() : request.getBirthday();
        String phone = request.getPhone() == null ? existing.getPhone() : request.getPhone();
        String emergencyContact = request.getEmergencyContact() == null
                ? existing.getEmergencyContact() : request.getEmergencyContact();
        // SQL 同时限定用户、SELF 关系与软删除状态，阻止跨账号或解绑后的资料更新
        if (profileMapper.updateSelfProfile(userId, existing.getPatientId(), request.getName().trim(), gender, birthday,
                phone, emergencyContact, updatedAt) != 1) {
            throw new CAuthException(ErrorCodeEnum.BUSINESS_STATUS_CONFLICT, HttpStatus.CONFLICT,
                    "个人资料已发生变化，请刷新后重试");
        }
        return ProfileUpdateVO.builder()
                .id(existing.getPatientId())
                .name(request.getName().trim())
                .phone(maskPhone(phone))
                .updatedAt(updatedAt)
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
     * 校验本人资料更新的业务约束。
     *
     * @param request 本人资料更新请求
     * @throws CAuthException 性别或出生日期不合法时抛出
     */
    private void validateUpdateRequest(ProfileUpdateRequest request) {
        if (!isGender(request.getGender())) {
            throw new CAuthException(ErrorCodeEnum.INVALID_PARAMETER, HttpStatus.BAD_REQUEST, "性别编码不合法");
        }
        if (request.getBirthday() != null && request.getBirthday().isAfter(LocalDate.now())) {
            throw new CAuthException(ErrorCodeEnum.INVALID_PARAMETER, HttpStatus.BAD_REQUEST, "出生日期不能晚于当天");
        }
    }

    /**
     * 判断可选性别编码是否属于系统支持范围。
     *
     * @param gender 性别编码，可为 null
     * @return 未传或编码合法时返回 true
     */
    private boolean isGender(String gender) {
        return gender == null || Arrays.stream(GenderEnum.values())
                .anyMatch(item -> item.getValue().equals(gender));
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

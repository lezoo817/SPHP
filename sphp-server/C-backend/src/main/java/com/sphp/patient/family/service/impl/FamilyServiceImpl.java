package com.sphp.patient.family.service.impl;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.common.enums.PatientRelationshipEnum;
import com.sphp.patient.family.mapper.FamilyMemberMapper;
import com.sphp.patient.family.mapper.FamilyMemberRecord;
import com.sphp.patient.family.service.FamilyService;
import com.sphp.patient.family.vo.FamilyMemberListVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * C端家庭成员管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class FamilyServiceImpl implements FamilyService {

    private final FamilyMemberMapper familyMemberMapper;

    /**
     * 查询当前 C端账号的全部有效就诊人。
     *
     * @return 本人及家庭成员列表
     */
    @Override
    public List<FamilyMemberListVO> listFamilyMembers() {
        Long userId = CUserContext.getRequired().userId();
        return familyMemberMapper.selectActiveMembers(userId).stream()
                .map(this::toListVO)
                .toList();
    }

    /**
     * 将家庭成员查询记录转换为面向 H5 的脱敏列表项。
     *
     * @param record 家庭成员联表查询记录
     * @return 脱敏列表项
     */
    private FamilyMemberListVO toListVO(FamilyMemberRecord record) {
        return FamilyMemberListVO.builder()
                .patientId(record.getPatientId())
                .name(record.getName())
                .relation(record.getRelationship())
                .relationName(relationName(record.getRelationship()))
                .gender(record.getGender())
                .birthday(record.getBirthday())
                .phone(maskPhone(record.getPhone()))
                .isDefault(record.getIsDefault())
                .build();
    }

    /**
     * 解析家庭关系的中文展示名称。
     *
     * @param relationship 关系编码
     * @return 中文展示名称
     */
    private String relationName(String relationship) {
        return Arrays.stream(PatientRelationshipEnum.values())
                .filter(item -> item.getValue().equals(relationship))
                .findFirst()
                .map(PatientRelationshipEnum::getDisplayName)
                .orElse(PatientRelationshipEnum.OTHER.getDisplayName());
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
}

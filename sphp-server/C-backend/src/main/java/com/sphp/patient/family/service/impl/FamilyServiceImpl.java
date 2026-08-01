package com.sphp.patient.family.service.impl;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.common.enums.PatientRelationshipEnum;
import com.sphp.patient.common.enums.GenderEnum;
import com.sphp.patient.family.dto.FamilyMemberCreateRequest;
import com.sphp.patient.family.entity.Patient;
import com.sphp.patient.family.entity.PatientUserRelation;
import com.sphp.patient.family.mapper.FamilyMemberMapper;
import com.sphp.patient.family.mapper.FamilyMemberRecord;
import com.sphp.patient.family.mapper.PatientMapper;
import com.sphp.patient.family.mapper.PatientUserRelationMapper;
import com.sphp.patient.family.service.FamilyService;
import com.sphp.patient.family.vo.FamilyMemberCreateVO;
import com.sphp.patient.family.vo.FamilyMemberListVO;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * C端家庭成员管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class FamilyServiceImpl implements FamilyService {

    private final FamilyMemberMapper familyMemberMapper;
    private final PatientMapper patientMapper;
    private final PatientUserRelationMapper relationMapper;

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
     * 新增当前账号下的非本人家庭成员。
     *
     * @param request 新增家庭成员请求
     * @return 新建家庭成员信息
     * @throws CAuthException 参数不合法、账号失效、重复绑定或成员上限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FamilyMemberCreateVO createFamilyMember(FamilyMemberCreateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        validateCreateRequest(request);
        // 锁定 C端用户行，串行化同一账号的数量检查、身份证校验与创建操作
        if (familyMemberMapper.lockUserForFamilyMutation(userId) == null) {
            throw new CAuthException(ErrorCodeEnum.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "当前登录状态无效");
        }
        if (familyMemberMapper.countActiveNonSelfMembers(userId) >= 5) {
            throw duplicateMember("家庭成员数量已达上限");
        }

        String idCardNo = normalizeIdCardNo(request.getIdCardNo());
        if (StringUtils.hasText(idCardNo)
                && familyMemberMapper.existsActiveIdCard(userId, idCardNo, null)) {
            throw duplicateMember("身份证号已绑定有效家庭成员");
        }

        OffsetDateTime now = OffsetDateTime.now();
        Patient patient = new Patient();
        patient.setName(request.getName().trim());
        patient.setGender(request.getGender());
        patient.setDateOfBirth(request.getBirthday());
        patient.setPhoneCiphertext(request.getPhone());
        patient.setIdCardCiphertext(idCardNo);
        patient.setEmergencyContact(request.getEmergencyContact());
        patient.setCreatedAt(now);
        patient.setUpdatedAt(now);
        patientMapper.insert(patient);

        PatientUserRelation relation = new PatientUserRelation();
        relation.setUserId(userId);
        relation.setPatientId(patient.getId());
        relation.setRelationship(request.getRelation());
        relation.setIsDefault(false);
        relation.setCreatedAt(now);
        relation.setUpdatedAt(now);
        relationMapper.insert(relation);

        return FamilyMemberCreateVO.builder()
                .patientId(patient.getId())
                .name(patient.getName())
                .relation(relation.getRelationship())
                .isDefault(false)
                .createdAt(now)
                .build();
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

    /**
     * 校验新增家庭成员的业务约束。
     *
     * @param request 新增家庭成员请求
     * @throws CAuthException 关系、性别或出生日期不合法时抛出
     */
    private void validateCreateRequest(FamilyMemberCreateRequest request) {
        if (!isNonSelfRelation(request.getRelation())) {
            throw new CAuthException(ErrorCodeEnum.INVALID_PARAMETER,
                    HttpStatus.BAD_REQUEST, "家庭关系不合法或不能为本人");
        }
        if (StringUtils.hasText(request.getGender()) && !isGender(request.getGender())) {
            throw new CAuthException(ErrorCodeEnum.INVALID_PARAMETER,
                    HttpStatus.BAD_REQUEST, "性别编码不合法");
        }
        if (request.getBirthday() != null && request.getBirthday().isAfter(LocalDate.now())) {
            throw new CAuthException(ErrorCodeEnum.INVALID_PARAMETER,
                    HttpStatus.BAD_REQUEST, "出生日期不能晚于当天");
        }
    }

    /**
     * 判断关系编码是否为允许创建的非本人关系。
     *
     * @param relation 关系编码
     * @return 是非本人关系时返回 true
     */
    private boolean isNonSelfRelation(String relation) {
        return Arrays.stream(PatientRelationshipEnum.values())
                .anyMatch(item -> item.getValue().equals(relation)
                        && item != PatientRelationshipEnum.SELF);
    }

    /**
     * 判断性别编码是否有效。
     *
     * @param gender 性别编码
     * @return 有效时返回 true
     */
    private boolean isGender(String gender) {
        return Arrays.stream(GenderEnum.values())
                .anyMatch(item -> item.getValue().equals(gender));
    }

    /**
     * 规范化身份证号，统一使用大写校验位进行存储和去重。
     *
     * @param idCardNo 原始身份证号
     * @return 规范化身份证号，可为空
     */
    private String normalizeIdCardNo(String idCardNo) {
        return StringUtils.hasText(idCardNo) ? idCardNo.toUpperCase(Locale.ROOT) : null;
    }

    /**
     * 创建家庭成员重复或上限异常。
     *
     * @param message 用户可读提示
     * @return HTTP 409 业务异常
     */
    private CAuthException duplicateMember(String message) {
        return new CAuthException(ErrorCodeEnum.DUPLICATE_REQUEST, HttpStatus.CONFLICT, message);
    }
}

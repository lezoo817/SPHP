package com.sphp.patient.family.service.impl;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.common.enums.PatientRelationshipEnum;
import com.sphp.patient.common.enums.GenderEnum;
import com.sphp.patient.family.dto.FamilyMemberCreateRequest;
import com.sphp.patient.family.dto.FamilyMemberUpdateRequest;
import com.sphp.patient.family.entity.Patient;
import com.sphp.patient.family.entity.PatientUserRelation;
import com.sphp.patient.family.mapper.FamilyMemberMapper;
import com.sphp.patient.family.mapper.FamilyMemberRecord;
import com.sphp.patient.family.mapper.PatientMapper;
import com.sphp.patient.family.mapper.PatientUserRelationMapper;
import com.sphp.patient.family.service.FamilyService;
import com.sphp.patient.family.vo.FamilyMemberCreateVO;
import com.sphp.patient.family.vo.FamilyMemberListVO;
import com.sphp.patient.family.vo.FamilyMemberUpdateVO;
import com.sphp.patient.family.vo.FamilyMemberUnbindVO;
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

import static com.sphp.patient.common.enums.PatientRelationshipEnum.OTHER;
import static com.sphp.patient.common.enums.PatientRelationshipEnum.SELF;
import static com.sphp.shared.common.enums.ErrorCodeEnum.*;

/**
 * C端家庭成员管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class FamilyServiceImpl implements FamilyService {

    private final FamilyMemberMapper familyMemberMapper;
    private final PatientMapper patientMapper;
    // 用户家庭关系Mapper
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
            throw new CAuthException(UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "当前登录状态无效");
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
        patient.setIdCardCiphertext(idCardNo); // 设置身份证号
        patient.setEmergencyContact(request.getEmergencyContact()); // 设置紧急联系人
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
     * 更新当前账号下的有效非本人家庭成员。
     *
     * @param patientId 就诊人 ID
     * @param request 更新家庭成员请求
     * @return 更新后的家庭成员信息
     * @throws CAuthException 账号失效、成员不存在、本人不可更新、身份证重复或并发状态变更时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FamilyMemberUpdateVO updateFamilyMember(Long patientId, FamilyMemberUpdateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        // 家庭参数校验
        validateUpdateRequest(request);
        // 锁定 C端用户行，使归属校验、身份证去重和资料更新在同一临界区内完成
        if (familyMemberMapper.lockUserForFamilyMutation(userId) == null) {
            throw new CAuthException(UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "当前登录状态无效");
        }
        FamilyMemberRecord existing = familyMemberMapper.selectActiveMember(userId, patientId);
        if (existing == null) {
            throw new CAuthException(INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "家庭成员不存在或已解绑");
        }
        // 本人不可更新
        if (SELF.getValue().equals(existing.getRelationship())) {
            throw new CAuthException(ORDER_CLOSED_OR_STATUS_INVALID,
                    HttpStatus.CONFLICT, "本人信息不能通过家庭成员接口更新");
        }

        String idCardNo = request.getIdCardNo() == null
                ? existing.getIdCardNo() : normalizeIdCardNo(request.getIdCardNo());
        if (StringUtils.hasText(idCardNo)
                && familyMemberMapper.existsActiveIdCard(userId, idCardNo, patientId)) {
            throw duplicateMember("身份证号已绑定有效家庭成员");
        }

        OffsetDateTime now = OffsetDateTime.now();
        Patient patient = buildUpdatedPatient(patientId, request, existing, idCardNo, now);
        if (patientMapper.updateById(patient) != 1) {
            throw stateConflict("家庭成员资料已发生变化，请刷新后重试");
        }

        PatientUserRelation relation = buildUpdatedRelation(userId, patientId, request, existing, now);
        if (relationMapper.updateById(relation) != 1) {
            throw stateConflict("家庭成员关系已发生变化，请刷新后重试");
        }

        return FamilyMemberUpdateVO.builder()
                .patientId(patientId)
                .name(patient.getName())
                .relation(relation.getRelationship())
                .relationName(relationName(relation.getRelationship()))
                .gender(patient.getGender())
                .birthday(patient.getDateOfBirth())
                .phone(maskPhone(patient.getPhoneCiphertext()))
                .isDefault(relation.getIsDefault())
                .updatedAt(now)
                .build();
    }

    /**
     * 停用当前账号下的有效非本人家庭成员关系。
     *
     * @param patientId 就诊人 ID
     * @return 解绑结果
     * @throws CAuthException 账号失效、成员不存在、本人不可解绑或关系状态已变化时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public FamilyMemberUnbindVO unbindFamilyMember(Long patientId) {
        Long userId = CUserContext.getRequired().userId();
        // 锁定 C端用户行，确保解绑与同账号的新增、更新操作串行执行
        if (familyMemberMapper.lockUserForFamilyMutation(userId) == null) {
            throw new CAuthException(UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "当前登录状态无效");
        }
        FamilyMemberRecord existing = familyMemberMapper.selectActiveMember(userId, patientId);
        if (existing == null) {
            throw new CAuthException(INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "家庭成员不存在或已解绑");
        }
        // 本人不可解绑
        if (SELF.getValue().equals(existing.getRelationship())) {
            throw new CAuthException(ORDER_CLOSED_OR_STATUS_INVALID,
                    HttpStatus.CONFLICT, "本人信息不能通过家庭成员接口解绑");
        }

        OffsetDateTime now = OffsetDateTime.now();
        // 仅停用关系记录，患者实体和历史医疗数据保持不变
        if (familyMemberMapper.softDeleteActiveRelation(existing.getRelationId(), userId, now) != 1) {
            throw stateConflict("家庭成员关系已发生变化，请刷新后重试");
        }
        return FamilyMemberUnbindVO.builder()
                .patientId(patientId)
                .unbound(true)
                .unboundAt(now)
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
                .idCardNo(maskIdCardNo(record.getIdCardNo())) // 仅向 H5 返回脱敏身份证号
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
                .filter(item -> item.getValue().equals(relationship)) // 关系编码匹配
                .findFirst() //
                .map(PatientRelationshipEnum::getDisplayName) // 获取中文展示名称
                .orElse(OTHER.getDisplayName()); // 默认为“其他”
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
     * 按身份证号脱敏规则隐藏中间位。
     *
     * @param idCardNo 身份证号原始值
     * @return 脱敏身份证号或 null
     */
    private String maskIdCardNo(String idCardNo) {
        if (!StringUtils.hasText(idCardNo)) {
            return null;
        }
        if (idCardNo.length() <= 7) {
            return "***";
        }
        return idCardNo.substring(0, 3) + "*".repeat(idCardNo.length() - 7)
                + idCardNo.substring(idCardNo.length() - 4);
    }

    /**
     * 校验新增家庭成员的业务约束。
     *
     * @param request 新增家庭成员请求
     * @throws CAuthException 关系、性别或出生日期不合法时抛出
     */
    private void validateCreateRequest(FamilyMemberCreateRequest request) {
        if (!isNonSelfRelation(request.getRelation())) {
            throw new CAuthException(INVALID_PARAMETER,
                    HttpStatus.BAD_REQUEST, "家庭关系不合法或不能为本人");
        }
        if (StringUtils.hasText(request.getGender()) && !isGender(request.getGender())) {
            throw new CAuthException(INVALID_PARAMETER,
                    HttpStatus.BAD_REQUEST, "性别编码不合法");
        }
        if (request.getBirthday() != null && request.getBirthday().isAfter(LocalDate.now())) {
            throw new CAuthException(INVALID_PARAMETER,
                    HttpStatus.BAD_REQUEST, "出生日期不能晚于当天");
        }
    }

    /**
     * 校验更新家庭成员的业务约束。
     *
     * @param request 更新家庭成员请求
     * @throws CAuthException 关系、性别或出生日期不合法时抛出
     */
    private void validateUpdateRequest(FamilyMemberUpdateRequest request) {
        if (!isNonSelfRelation(request.getRelation())) {
            throw new CAuthException(INVALID_PARAMETER,
                    HttpStatus.BAD_REQUEST, "家庭关系不合法或不能为本人");
        }
        if (request.getGender() != null && !isGender(request.getGender())) {
            throw new CAuthException(INVALID_PARAMETER,
                    HttpStatus.BAD_REQUEST, "性别编码不合法");
        }
        if (request.getBirthday() != null && request.getBirthday().isAfter(LocalDate.now())) {
            throw new CAuthException(INVALID_PARAMETER,
                    HttpStatus.BAD_REQUEST, "出生日期不能晚于当天");
        }
    }

    /**
     * 组装保留未传字段后的就诊人更新实体。
     *
     * @param patientId 就诊人 ID
     * @param request 更新家庭成员请求
     * @param existing 当前有效成员记录
     * @param idCardNo 规范化后的身份证号
     * @param updatedAt 更新时间
     * @return 待更新就诊人实体
     */
    private Patient buildUpdatedPatient(Long patientId, FamilyMemberUpdateRequest request,
                                        FamilyMemberRecord existing, String idCardNo, OffsetDateTime updatedAt) {
        Patient patient = new Patient();
        patient.setId(patientId);
        patient.setName(request.getName().trim());
        patient.setGender(request.getGender() == null ? existing.getGender() : request.getGender());
        patient.setDateOfBirth(request.getBirthday() == null ? existing.getBirthday() : request.getBirthday());
        patient.setPhoneCiphertext(request.getPhone() == null ? existing.getPhone() : request.getPhone());
        patient.setIdCardCiphertext(idCardNo);
        patient.setEmergencyContact(request.getEmergencyContact() == null
                ? existing.getEmergencyContact() : request.getEmergencyContact());
        patient.setUpdatedAt(updatedAt);
        return patient;
    }

    /**
     * 组装家庭关系更新实体，保留原有默认就诊人标记。
     *
     * @param userId 当前 C端用户 ID
     * @param patientId 就诊人 ID
     * @param request 更新家庭成员请求
     * @param existing 当前有效成员记录
     * @param updatedAt 更新时间
     * @return 待更新关系实体
     */
    private PatientUserRelation buildUpdatedRelation(Long userId, Long patientId,
                                                     FamilyMemberUpdateRequest request,
                                                     FamilyMemberRecord existing, OffsetDateTime updatedAt) {
        PatientUserRelation relation = new PatientUserRelation();
        relation.setId(existing.getRelationId());
        relation.setUserId(userId);
        relation.setPatientId(patientId);
        relation.setRelationship(request.getRelation());
        relation.setIsDefault(existing.getIsDefault());
        relation.setUpdatedAt(updatedAt);
        return relation;
    }

    /**
     * 判断关系编码是否为允许创建的非本人关系。
     *
     * @param relation 关系编码
     * @return 是非本人关系时返回 true
     */
    private boolean isNonSelfRelation(String relation) {
        return Arrays.stream(PatientRelationshipEnum.values())
                .anyMatch(item -> item.getValue().equals(relation) //只要流中任意一个元素满足给定条件就立即返回
                        && item != SELF);
        //只要存在一个关系编码等于传入的 relation 且该枚举值不是 SELF(本人),就返回 true,
        // 说明这是一个允许创建的非本人关系;如果遍历完所有枚举值都没有匹配项,则返回 false。
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
        return new CAuthException(DUPLICATE_REQUEST, HttpStatus.CONFLICT, message);
    }

    /**
     * 创建关系状态已变化时的并发冲突异常。
     *
     * @param message 用户可读提示
     * @return HTTP 409 业务异常
     */
    private CAuthException stateConflict(String message) {
        return new CAuthException(BUSINESS_STATUS_CONFLICT, HttpStatus.CONFLICT, message);
    }
}

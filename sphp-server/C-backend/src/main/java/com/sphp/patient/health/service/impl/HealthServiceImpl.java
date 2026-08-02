package com.sphp.patient.health.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.health.entity.PatientAllergy;
import com.sphp.patient.health.entity.PatientMedicalHistory;
import com.sphp.patient.health.dto.AllergyCreateRequest;
import com.sphp.patient.health.dto.AllergyUpdateRequest;
import com.sphp.patient.health.dto.MedicalHistoryCreateRequest;
import com.sphp.patient.health.mapper.HealthPatientMapper;
import com.sphp.patient.health.mapper.HealthPatientProfileRecord;
import com.sphp.patient.health.mapper.PatientAllergyMapper;
import com.sphp.patient.health.mapper.PatientMedicalHistoryMapper;
import com.sphp.patient.health.service.HealthService;
import com.sphp.patient.health.vo.AllergyItemVO;
import com.sphp.patient.health.vo.AllergyCreateVO;
import com.sphp.patient.health.vo.AllergyUpdateVO;
import com.sphp.patient.health.vo.MedicalHistoryCreateVO;
import com.sphp.patient.health.vo.HealthProfileVO;
import com.sphp.patient.health.vo.HealthRecordVO;
import com.sphp.patient.health.vo.MedicalHistoryItemVO;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.OffsetDateTime;

/**
 * C端健康档案服务实现。
 */
@Service
@RequiredArgsConstructor
public class HealthServiceImpl implements HealthService {

    private final HealthPatientMapper healthPatientMapper;
    private final PatientAllergyMapper allergyMapper;
    private final PatientMedicalHistoryMapper historyMapper;

    /**
     * 查询当前账号可访问就诊人的健康档案。
     *
     * @param patientId 可选就诊人 ID，未传时查询本人
     * @return 健康档案资料、过敏史、既往史和摘要
     * @throws CAuthException 就诊人不存在或不属于当前账号时抛出
     */
    @Override
    public HealthRecordVO getHealthRecord(Long patientId) {
        Long targetPatientId = resolveAccessiblePatientId(patientId);
        HealthPatientProfileRecord profile = healthPatientMapper.selectActiveProfile(targetPatientId);
        if (profile == null) {
            throw notFound("就诊人不存在或已停用");
        }
        List<AllergyItemVO> allergies = allergyMapper.selectList(Wrappers.<PatientAllergy>lambdaQuery()
                        .eq(PatientAllergy::getPatientId, targetPatientId)
                        .isNull(PatientAllergy::getDeletedAt)
                        .orderByAsc(PatientAllergy::getCreatedAt)
                        .orderByAsc(PatientAllergy::getId))
                .stream()
                .map(this::toAllergyItemVO)
                .toList();
        List<MedicalHistoryItemVO> medicalHistories = historyMapper.selectList(
                        Wrappers.<PatientMedicalHistory>lambdaQuery()
                                .eq(PatientMedicalHistory::getPatientId, targetPatientId)
                                .isNull(PatientMedicalHistory::getDeletedAt)
                                .orderByAsc(PatientMedicalHistory::getCreatedAt)
                                .orderByAsc(PatientMedicalHistory::getId))
                .stream()
                .map(this::toMedicalHistoryItemVO)
                .toList();
        return HealthRecordVO.builder()
                .profile(HealthProfileVO.builder()
                        .id(profile.id())
                        .name(profile.name())
                        .gender(profile.gender())
                        .build())
                .allergies(allergies)
                .medicalHistories(medicalHistories)
                .summary("已记录" + allergies.size() + "项过敏史和" + medicalHistories.size() + "项既往史")
                .build();
    }

    /**
     * 为当前账号可访问就诊人新增过敏史。
     *
     * @param request 新增过敏史请求
     * @return 新建过敏史信息
     * @throws CAuthException 就诊人不存在、无权访问或写入失败时抛出
     */
    @Override
    public AllergyCreateVO createAllergy(AllergyCreateRequest request) {
        Long targetPatientId = resolveAccessiblePatientId(request.getPatientId());
        PatientAllergy allergy = new PatientAllergy();
        allergy.setPatientId(targetPatientId);
        allergy.setAllergen(request.getAllergen());
        allergy.setReaction(request.getReaction());
        // 插入结果必须为一条，避免数据库异常被包装为伪成功响应
        if (allergyMapper.insert(allergy) != 1) {
            throw new CAuthException(ErrorCodeEnum.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "过敏史保存失败");
        }
        return AllergyCreateVO.builder()
                .id(allergy.getId())
                .allergen(allergy.getAllergen())
                .reaction(allergy.getReaction())
                .build();
    }

    /**
     * 更新当前账号可访问就诊人的过敏史。
     *
     * @param allergyId 过敏史 ID，所属就诊人由服务端反查
     * @param request 更新过敏史请求
     * @return 更新后的过敏史信息
     * @throws CAuthException 过敏史不存在、已删除或条件更新失败时抛出
     */
    @Override
    public AllergyUpdateVO updateAllergy(Long allergyId, AllergyUpdateRequest request) {
        PatientAllergy existing = allergyMapper.selectOne(Wrappers.<PatientAllergy>lambdaQuery()
                .eq(PatientAllergy::getId, allergyId)
                .isNull(PatientAllergy::getDeletedAt));
        if (existing == null) {
            throw notFound("过敏史不存在或已删除");
        }
        Long targetPatientId = requireAccessiblePatientId(existing.getPatientId());

        OffsetDateTime now = OffsetDateTime.now();
        PatientAllergy updated = new PatientAllergy();
        updated.setId(allergyId);
        updated.setPatientId(targetPatientId);
        updated.setAllergen(request.getAllergen());
        updated.setReaction(request.getReaction() == null ? existing.getReaction() : request.getReaction());
        updated.setUpdatedAt(now);
        // 使用患者范围和未删除条件更新，避免资源在并发场景下被越权或重复修改
        int affected = allergyMapper.update(updated, Wrappers.<PatientAllergy>lambdaUpdate()
                .eq(PatientAllergy::getId, allergyId)
                .eq(PatientAllergy::getPatientId, targetPatientId)
                .isNull(PatientAllergy::getDeletedAt));
        if (affected != 1) {
            throw notFound("过敏史不存在或已删除");
        }
        return AllergyUpdateVO.builder()
                .id(allergyId)
                .allergen(updated.getAllergen())
                .reaction(updated.getReaction())
                .updatedAt(now)
                .build();
    }

    /**
     * 为当前账号可访问就诊人新增既往史。
     *
     * @param request 新增既往史请求
     * @return 新建既往史信息
     * @throws CAuthException 就诊人不存在、无权访问或写入失败时抛出
     */
    @Override
    public MedicalHistoryCreateVO createMedicalHistory(MedicalHistoryCreateRequest request) {
        Long targetPatientId = resolveAccessiblePatientId(request.getPatientId());
        PatientMedicalHistory history = new PatientMedicalHistory();
        history.setPatientId(targetPatientId);
        history.setContent(request.getContent());
        history.setOccurredAt(request.getOccurredAt());
        // 插入结果必须为一条，避免数据库异常被包装为伪成功响应
        if (historyMapper.insert(history) != 1) {
            throw new CAuthException(ErrorCodeEnum.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, "既往史保存失败");
        }
        return MedicalHistoryCreateVO.builder()
                .id(history.getId())
                .content(history.getContent())
                .occurredAt(history.getOccurredAt())
                .build();
    }

    /**
     * 解析并校验当前账号可访问的目标就诊人。
     *
     * @param patientId 请求指定的就诊人 ID，可为 null
     * @return 通过归属校验的就诊人 ID
     * @throws CAuthException 本人关系缺失、就诊人不存在或无访问权限时抛出
     */
    private Long resolveAccessiblePatientId(Long patientId) {
        Long userId = CUserContext.getRequired().userId();
        if (patientId == null) {
            Long selfPatientId = healthPatientMapper.selectSelfPatientId(userId);
            if (selfPatientId == null) {
                throw notFound("当前账号未找到有效本人就诊人");
            }
            return selfPatientId;
        }
        return requireAccessiblePatientId(patientId);
    }

    /**
     * 校验当前 C端账号是否可访问指定的有效就诊人。
     *
     * @param patientId 就诊人 ID
     * @return 已校验的就诊人 ID
     * @throws CAuthException 就诊人不存在或不属于当前账号时抛出
     */
    private Long requireAccessiblePatientId(Long patientId) {
        Long userId = CUserContext.getRequired().userId();
        if (!healthPatientMapper.existsActivePatient(patientId)) {
            throw notFound("就诊人不存在或已停用");
        }
        if (!healthPatientMapper.hasActivePatientRelation(userId, patientId)) {
            throw new CAuthException(ErrorCodeEnum.UNAUTHORIZED, HttpStatus.FORBIDDEN, "无权访问该就诊人健康档案");
        }
        return patientId;
    }

    /**
     * 转换过敏史列表项，避免输出实体中的服务端字段。
     *
     * @param allergy 过敏史实体
     * @return 过敏史列表项
     */
    private AllergyItemVO toAllergyItemVO(PatientAllergy allergy) {
        return AllergyItemVO.builder()
                .id(allergy.getId())
                .allergen(allergy.getAllergen())
                .reaction(allergy.getReaction())
                .build();
    }

    /**
     * 转换既往史列表项，避免输出实体中的服务端字段。
     *
     * @param history 既往史实体
     * @return 既往史列表项
     */
    private MedicalHistoryItemVO toMedicalHistoryItemVO(PatientMedicalHistory history) {
        return MedicalHistoryItemVO.builder()
                .id(history.getId())
                .content(history.getContent())
                .occurredAt(history.getOccurredAt())
                .build();
    }

    /**
     * 创建资源不存在异常。
     *
     * @param message 面向调用方的提示
     * @return HTTP 404 业务异常
     */
    private CAuthException notFound(String message) {
        return new CAuthException(ErrorCodeEnum.INVALID_USER_INPUT, HttpStatus.NOT_FOUND, message);
    }
}

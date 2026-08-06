package com.sphp.admin.patient.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sphp.admin.common.CurrentUserService;
import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.doctor.entity.Patient;
import com.sphp.admin.doctor.entity.PatientAllergy;
import com.sphp.admin.doctor.entity.PatientMedicalHistory;
import com.sphp.admin.doctor.mapper.BPatientAllergyMapper;
import com.sphp.admin.doctor.mapper.BPatientMapper;
import com.sphp.admin.doctor.mapper.BPatientMedicalHistoryMapper;
import com.sphp.admin.patient.entity.FollowUpPlan;
import com.sphp.admin.patient.entity.MedicationPlan;
import com.sphp.admin.patient.mapper.FollowUpPlanMapper;
import com.sphp.admin.patient.mapper.MedicationPlanMapper;
import com.sphp.admin.patient.mapper.PatientDataMapper;
import com.sphp.admin.patient.service.PatientService;
import com.sphp.admin.patient.vo.AllergyVO;
import com.sphp.admin.patient.vo.FollowUpPlanVO;
import com.sphp.admin.patient.vo.MedicalHistoryVO;
import com.sphp.admin.patient.vo.MedicationPlanVO;
import com.sphp.admin.patient.vo.PatientDetailVO;
import com.sphp.admin.patient.vo.PatientListVO;
import com.sphp.admin.patient.vo.PatientMedicationVO;
import com.sphp.admin.patient.vo.PatientPrescriptionVO;
import com.sphp.admin.patient.vo.PatientVisitVO;
import com.sphp.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

/**
 * 患者管理服务实现（管理员视角）。
 *
 * <p>患者列表范围：通过 consult_record → doctor.hospital_id 关联，
 * 仅返回在本院就诊过的患者；所有操作基于当前登录管理员所属医院（{@code hospital_id}）
 * 做数据隔离。详情页附加过敏史（{@code patient_allergy}）与既往史（{@code patient_medical_history}），
 * 当前用药页附加 ACTIVE/PAUSED 状态的用药计划与未完成的随访计划。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientServiceImpl implements PatientService {

    /** 资源不存在 / 已软删（A0402） */
    private static final String ERR_RESOURCE_NOT_FOUND = "A0402";

    /** 用药计划状态：进行中（计入当前用药列表） */
    private static final String MEDICATION_STATUS_ACTIVE = "ACTIVE";
    /** 用药计划状态：暂停中（计入当前用药列表，等待医生恢复） */
    private static final String MEDICATION_STATUS_PAUSED = "PAUSED";

    /** 随访计划状态：已完成（从当前随访列表排除） */
    private static final String FOLLOWUP_STATUS_COMPLETED = "COMPLETED";
    /** 随访计划状态：已取消（从当前随访列表排除） */
    private static final String FOLLOWUP_STATUS_CANCELLED = "CANCELLED";

    /** 加密手机号脱敏占位（密文存储无法直接脱敏，统一返回） */
    private static final String PHONE_MASK_PLACEHOLDER = "***";

    /** 每页大小上限（与 Controller clampSize 一致；common 模块统一前暂留本地） */
    private static final int MAX_PAGE_SIZE = 100;

    private final CurrentUserService currentUserService;
    private final PatientDataMapper patientDataMapper;
    private final BPatientMapper patientMapper;
    private final BPatientAllergyMapper allergyMapper;
    private final BPatientMedicalHistoryMapper medicalHistoryMapper;
    private final MedicationPlanMapper medicationPlanMapper;
    private final FollowUpPlanMapper followUpPlanMapper;

    @Override
    public PageResult<PatientListVO> page(String name, int page, int size) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        size = Math.max(1, Math.min(size, MAX_PAGE_SIZE));

        Page<PatientListVO> result = patientDataMapper.selectPatientPage(
                new Page<>(page, size), hospitalId,
                StringUtils.hasText(name) ? name : null);

        // 计算年龄后清空生日字段，避免敏感个人信息暴露
        result.getRecords().forEach(vo -> {
            vo.setAge(calcAge(vo.getDateOfBirth()));
            vo.setDateOfBirth(null);
        });
        return PageResult.of(result.getTotal(), result.getRecords(), page, size);
    }

    @Override
    public PatientDetailVO detail(Long id) {
        Patient patient = getPatient(id);

        List<PatientAllergy> allergies = allergyMapper.selectList(
                Wrappers.<PatientAllergy>lambdaQuery()
                        .eq(PatientAllergy::getPatientId, id)
                        .isNull(PatientAllergy::getDeletedAt));

        List<PatientMedicalHistory> histories = medicalHistoryMapper.selectList(
                Wrappers.<PatientMedicalHistory>lambdaQuery()
                        .eq(PatientMedicalHistory::getPatientId, id)
                        .isNull(PatientMedicalHistory::getDeletedAt));

        return PatientDetailVO.builder()
                .id(patient.getId())
                .name(patient.getName())
                .gender(patient.getGender())
                .dateOfBirth(patient.getDateOfBirth())
                .phone(maskPhone(patient.getPhoneCiphertext()))
                .emergencyContact(patient.getEmergencyContact())
                .allergies(allergies.stream()
                        .map(a -> AllergyVO.builder()
                                .id(a.getId())
                                .allergen(a.getAllergen())
                                .reaction(a.getReaction())
                                .severity(a.getSeverity())
                                .build())
                        .toList())
                .medicalHistories(histories.stream()
                        .map(h -> MedicalHistoryVO.builder()
                                .id(h.getId())
                                .content(h.getContent())
                                .occurredAt(h.getOccurredAt())
                                .build())
                        .toList())
                .build();
    }

    @Override
    public PageResult<PatientVisitVO> visits(Long patientId, int page, int size) {
        size = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        getPatient(patientId);

        Page<PatientVisitVO> result = patientDataMapper.selectVisitPage(
                new Page<>(page, size), patientId);
        return PageResult.of(result.getTotal(), result.getRecords(), page, size);
    }

    @Override
    public PageResult<PatientPrescriptionVO> prescriptions(Long patientId, int page, int size) {
        size = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        getPatient(patientId);

        Page<PatientPrescriptionVO> result = patientDataMapper.selectPrescriptionPage(
                new Page<>(page, size), patientId);
        return PageResult.of(result.getTotal(), result.getRecords(), page, size);
    }

    @Override
    public PatientMedicationVO medications(Long patientId) {
        getPatient(patientId);

        // 当前用药：进行中 + 暂停中
        List<MedicationPlan> plans = medicationPlanMapper.selectList(
                Wrappers.<MedicationPlan>lambdaQuery()
                        .eq(MedicationPlan::getPatientId, patientId)
                        .in(MedicationPlan::getStatus,
                                MEDICATION_STATUS_ACTIVE, MEDICATION_STATUS_PAUSED)
                        .isNull(MedicationPlan::getDeletedAt)
                        .orderByDesc(MedicationPlan::getCreatedAt));

        // 当前随访：排除已完成、已取消
        List<FollowUpPlan> followUps = followUpPlanMapper.selectList(
                Wrappers.<FollowUpPlan>lambdaQuery()
                        .eq(FollowUpPlan::getPatientId, patientId)
                        .notIn(FollowUpPlan::getStatus,
                                FOLLOWUP_STATUS_COMPLETED, FOLLOWUP_STATUS_CANCELLED)
                        .isNull(FollowUpPlan::getDeletedAt)
                        .orderByDesc(FollowUpPlan::getCreatedAt));

        return PatientMedicationVO.builder()
                .medicationPlans(plans.stream()
                        .map(m -> MedicationPlanVO.builder()
                                .id(m.getId())
                                .drugName(m.getDrugNameSnapshot())
                                .dosage(m.getDosage())
                                .frequency(m.getFrequency())
                                .usageMethod(m.getUsageMethod())
                                .status(m.getStatus())
                                .nextRemindAt(m.getNextRemindAt())
                                .createdAt(m.getCreatedAt())
                                .build())
                        .toList())
                .followUpPlans(followUps.stream()
                        .map(f -> FollowUpPlanVO.builder()
                                .id(f.getId())
                                .followUpType(f.getFollowUpType())
                                .content(f.getContent())
                                .dueAt(f.getDueAt())
                                .status(f.getStatus())
                                .createdAt(f.getCreatedAt())
                                .build())
                        .toList())
                .build();
    }

    /**
     * 按 ID 查询患者，不存在或已软删统一返回 {@value #ERR_RESOURCE_NOT_FOUND}。
     *
     * @param id 患者 ID
     * @return 有效患者实体
     * @throws BusinessException 当患者不存在或已软删时抛出
     */
    private Patient getPatient(Long id) {
        Patient patient = patientMapper.selectById(id);
        if (patient == null || patient.getDeletedAt() != null) {
            throw new BusinessException(ERR_RESOURCE_NOT_FOUND, "患者不存在");
        }
        return patient;
    }

    /**
     * 根据出生日期计算年龄（按当前本地日期）。
     *
     * @param dateOfBirth 出生日期
     * @return 完整年数；{@code null} 入参返回 {@code null}
     */
    private Integer calcAge(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return null;
        }
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    /**
     * 手机号脱敏。手机号以密文存储，无法在服务端做精准脱敏，
     * 统一返回 {@link #PHONE_MASK_PLACEHOLDER} 占位避免泄露明文长度。
     *
     * @param phoneCiphertext 手机号密文（可空）
     * @return 脱敏占位字符串；入参为空返回 {@code null}
     */
    private String maskPhone(String phoneCiphertext) {
        if (!StringUtils.hasText(phoneCiphertext)) {
            return null;
        }
        return PHONE_MASK_PLACEHOLDER;
    }
}

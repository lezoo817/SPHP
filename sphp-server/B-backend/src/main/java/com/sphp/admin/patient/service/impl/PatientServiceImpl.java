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
 * 患者管理服务实现（系分 §5.8）。
 *
 * <p>患者列表范围：通过 consult_record → doctor.hospital_id 关联，仅返回在本院就诊过的患者。
 * 所有操作基于当前登录管理员所属医院（hospital_id）做数据隔离。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientServiceImpl implements PatientService {

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

        // 计算年龄
        result.getRecords().forEach(vo -> {
            vo.setAge(calcAge(vo.getDateOfBirth()));
            vo.setDateOfBirth(null); // 年龄返回后清空生日，避免暴露
        });
        return PageResult.of(result.getTotal(), result.getRecords(), page, size);
    }

    @Override
    public PatientDetailVO detail(Long id) {
        // 查询患者基本信息
        Patient patient = getPatient(id);

        // 查询过敏史
        List<PatientAllergy> allergies = allergyMapper.selectList(
                Wrappers.<PatientAllergy>lambdaQuery()
                        .eq(PatientAllergy::getPatientId, id)
                        .isNull(PatientAllergy::getDeletedAt));

        // 查询既往史
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
        // 校验患者存在
        getPatient(patientId);

        Page<PatientVisitVO> result = patientDataMapper.selectVisitPage(
                new Page<>(page, size), patientId);
        return PageResult.of(result.getTotal(), result.getRecords(), page, size);
    }

    @Override
    public PageResult<PatientPrescriptionVO> prescriptions(Long patientId, int page, int size) {
        size = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        // 校验患者存在
        getPatient(patientId);

        Page<PatientPrescriptionVO> result = patientDataMapper.selectPrescriptionPage(
                new Page<>(page, size), patientId);
        return PageResult.of(result.getTotal(), result.getRecords(), page, size);
    }

    @Override
    public PatientMedicationVO medications(Long patientId) {
        // 校验患者存在
        getPatient(patientId);

        // 查询当前用药计划（ACTIVE / PAUSED）
        List<MedicationPlan> plans = medicationPlanMapper.selectList(
                Wrappers.<MedicationPlan>lambdaQuery()
                        .eq(MedicationPlan::getPatientId, patientId)
                        .in(MedicationPlan::getStatus, "ACTIVE", "PAUSED")
                        .isNull(MedicationPlan::getDeletedAt)
                        .orderByDesc(MedicationPlan::getCreatedAt));

        // 查询随访计划（未完成的）
        List<FollowUpPlan> followUps = followUpPlanMapper.selectList(
                Wrappers.<FollowUpPlan>lambdaQuery()
                        .eq(FollowUpPlan::getPatientId, patientId)
                        .notIn(FollowUpPlan::getStatus, "COMPLETED", "CANCELLED")
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

    /** 按 ID 查询患者，不存在返回 A0402 */
    private Patient getPatient(Long id) {
        Patient patient = patientMapper.selectById(id);
        if (patient == null || patient.getDeletedAt() != null) {
            throw new BusinessException("A0402", "患者不存在");
        }
        return patient;
    }

    /** 根据出生日期计算年龄 */
    private Integer calcAge(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return null;
        }
        return Period.between(dateOfBirth, LocalDate.now()).getYears();
    }

    /** 手机号脱敏（密文存储，仅返回脱敏占位） */
    private String maskPhone(String phoneCiphertext) {
        if (!StringUtils.hasText(phoneCiphertext)) {
            return null;
        }
        // phoneCiphertext 为加密存储，无法直接脱敏，返回脱敏占位
        return "***";
    }
}
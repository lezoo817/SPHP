package com.sphp.admin.prescription.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sphp.admin.auth.entity.Doctor;
import com.sphp.admin.auth.mapper.DoctorMapper;
import com.sphp.admin.common.CurrentUserService;
import com.sphp.admin.common.DataScope;
import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.doctor.entity.ConsultRecord;
import com.sphp.admin.doctor.entity.Patient;
import com.sphp.admin.doctor.entity.PatientAllergy;
import com.sphp.admin.doctor.mapper.ConsultRecordMapper;
import com.sphp.admin.doctor.mapper.BPatientMapper;
import com.sphp.admin.doctor.mapper.BPatientAllergyMapper;
import com.sphp.admin.hospital.entity.Department;
import com.sphp.admin.hospital.mapper.DepartmentMapper;
import com.sphp.admin.prescription.dto.PrescriptionDetailVO;
import com.sphp.admin.prescription.dto.PrescriptionListVO;
import com.sphp.admin.prescription.dto.PrescriptionSubmitRequest;
import com.sphp.admin.prescription.dto.PrescriptionSubmitVO;
import com.sphp.admin.prescription.dto.RiskWarningVO;
import com.sphp.admin.prescription.entity.Drug;
import com.sphp.admin.prescription.entity.Prescription;
import com.sphp.admin.prescription.entity.PrescriptionItem;
import com.sphp.admin.prescription.mapper.DrugMapper;
import com.sphp.admin.prescription.mapper.PrescriptionItemMapper;
import com.sphp.admin.prescription.mapper.PrescriptionMapper;
import com.sphp.admin.prescription.service.PrescriptionService;
import com.sphp.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 处方管理服务实现（系分 §5.6）。
 *
 * <p>处方提交时执行风险拦截：
 * <ul>
 *   <li>ERROR（红线）：过敏史强匹配 → 抛 3004，不入库</li>
 *   <li>WARNING（提示）：重复用药 / 剂量超常规 → 处方生效，返回告警</li>
 *   <li>AUDIT（审核）：高危药物联用 → 处方进入待审核队列</li>
 *   <li>无风险：直接 APPROVED</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionServiceImpl implements PrescriptionService {

    private static final String ROLE_DOCTOR = "DOCTOR";

    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_SUBMITTED = "SUBMITTED";
    private static final String STATUS_APPROVED = "APPROVED";

    private final PrescriptionMapper prescriptionMapper;
    private final PrescriptionItemMapper prescriptionItemMapper;
    private final DrugMapper drugMapper;
    private final ConsultRecordMapper consultRecordMapper;
    private final BPatientAllergyMapper patientAllergyMapper;
    private final BPatientMapper patientMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final CurrentUserService currentUserService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrescriptionSubmitVO submit(PrescriptionSubmitRequest request) {
        DataScope scope = currentUserService.getCurrentDataScope();
        Long doctorId = scope.doctorId();
        if (doctorId == null) {
            throw new BusinessException("A0443", "当前用户无医生身份，无法开方");
        }

        // 1. 校验问诊记录
        ConsultRecord consult = consultRecordMapper.selectById(request.getConsultId());
        if (consult == null || consult.getDeletedAt() != null) {
            throw new BusinessException("3002", "问诊记录不存在或不可开方");
        }
        if (!STATUS_IN_PROGRESS.equals(consult.getStatus())) {
            throw new BusinessException("3002", "问诊状态不是 IN_PROGRESS，不可开方");
        }
        if (!consult.getDoctorId().equals(doctorId)) {
            throw new BusinessException("3020", "无权查看该处方");
        }

        // 2. 校验药品
        List<PrescriptionSubmitRequest.ItemDTO> items = request.getItems();
        Set<Long> drugIds = items.stream().map(PrescriptionSubmitRequest.ItemDTO::getDrugId).collect(Collectors.toSet());
        Map<Long, Drug> drugMap = drugMapper.selectBatchIds(drugIds).stream()
                .filter(d -> d.getDeletedAt() == null)
                .collect(Collectors.toMap(Drug::getId, d -> d, (a, b) -> a));

        for (PrescriptionSubmitRequest.ItemDTO item : items) {
            Drug drug = drugMap.get(item.getDrugId());
            if (drug == null) {
                throw new BusinessException("3003", "药品不存在或已停用");
            }
            if (!"ENABLED".equals(drug.getStatus())) {
                throw new BusinessException("3003", "药品「" + drug.getName() + "」已停用");
            }
        }

        // 3. 风险拦截
        List<RiskWarningVO> warnings = new ArrayList<>();
        Long patientId = consult.getPatientId();

        // 3.1 ERROR 红线：过敏史匹配
        List<PatientAllergy> allergies = patientAllergyMapper.selectList(
                Wrappers.<PatientAllergy>lambdaQuery()
                        .eq(PatientAllergy::getPatientId, patientId)
                        .isNull(PatientAllergy::getDeletedAt));

        for (PrescriptionSubmitRequest.ItemDTO item : items) {
            Drug drug = drugMap.get(item.getDrugId());
            if (drug == null) continue;
            for (PatientAllergy allergy : allergies) {
                if (matchesAllergen(drug, allergy)) {
                    log.warn("红线拦截：患者{}对{}过敏，处方含{}", patientId, allergy.getAllergen(), drug.getName());
                    throw new BusinessException("3004",
                            "红线规则拦截：患者对「" + allergy.getAllergen() + "」过敏，处方含「" + drug.getName() + "」，禁止提交");
                }
            }
        }

        // 3.2 WARNING：重复用药检测（同一成分不同药品）
        Set<String> drugNames = items.stream()
                .map(item -> drugMap.get(item.getDrugId()))
                .filter(Objects::nonNull)
                .map(Drug::getName)
                .collect(Collectors.toSet());
        if (drugNames.size() < items.size()) {
            warnings.add(RiskWarningVO.builder()
                    .level("WARNING")
                    .rule("重复用药检测")
                    .message("处方中存在重复或相似药品，请确认是否需联合使用")
                    .build());
        }

        // 3.3 AUDIT：高危药物检测（简化：含特定关键词的药品进入审核）
        boolean hasHighRiskDrug = items.stream()
                .map(item -> drugMap.get(item.getDrugId()))
                .filter(Objects::nonNull)
                .anyMatch(d -> containsHighRiskKeyword(d.getName()));

        // 4. 判定处方状态
        boolean auditRequired = false;
        String prescriptionStatus;
        if (hasHighRiskDrug) {
            prescriptionStatus = STATUS_SUBMITTED;
            auditRequired = true;
            warnings.add(RiskWarningVO.builder()
                    .level("AUDIT")
                    .rule("高危药物联用")
                    .message("处方命中审核级规则，需人工审核")
                    .build());
        } else {
            prescriptionStatus = STATUS_APPROVED;
        }

        // 5. 入库
        Prescription prescription = new Prescription();
        prescription.setConsultId(request.getConsultId());
        prescription.setDoctorId(doctorId);
        prescription.setPatientId(patientId);
        prescription.setStatus(prescriptionStatus);
        if (STATUS_APPROVED.equals(prescriptionStatus)) {
            prescription.setIssuedAt(OffsetDateTime.now());
        }
        prescriptionMapper.insert(prescription);

        for (PrescriptionSubmitRequest.ItemDTO item : items) {
            PrescriptionItem pi = new PrescriptionItem();
            pi.setPrescriptionId(prescription.getId());
            pi.setDrugId(item.getDrugId());
            pi.setDosage(item.getDosage());
            pi.setFrequency(item.getFrequency());
            pi.setUsageMethod(item.getUsageMethod());
            pi.setDays(item.getDays());
            pi.setQuantity(item.getQuantity());
            prescriptionItemMapper.insert(pi);
        }

        log.info("提交处方 prescriptionId={}, status={}, auditRequired={}, drugCount={}",
                prescription.getId(), prescriptionStatus, auditRequired, items.size());

        return PrescriptionSubmitVO.builder()
                .id(prescription.getId())
                .status(prescriptionStatus)
                .auditRequired(auditRequired)
                .riskWarnings(warnings)
                .build();
    }

    // ==================== 5.6.2 处方列表 ====================

    @Override
    public PageResult<PrescriptionListVO> page(Long consultId, Long patientId, String status, int page, int size) {
        DataScope scope = currentUserService.getCurrentDataScope();
        LambdaQueryWrapper<Prescription> wrapper = Wrappers.<Prescription>lambdaQuery()
                .eq(consultId != null, Prescription::getConsultId, consultId)
                .eq(patientId != null, Prescription::getPatientId, patientId)
                .isNull(Prescription::getDeletedAt)
                .apply("doctor_id IN (SELECT id FROM doctor WHERE hospital_id = {0} AND deleted_at IS NULL)",
                        scope.hospitalId());
        if (ROLE_DOCTOR.equals(scope.role())) {
            wrapper.eq(Prescription::getDoctorId, scope.doctorId());
        }
        if (StringUtils.hasText(status)) {
            wrapper.in(Prescription::getStatus, (Object[]) status.split(","));
        }
        wrapper.orderByDesc(Prescription::getCreatedAt);

        Page<Prescription> result = prescriptionMapper.selectPage(new Page<>(page, size), wrapper);
        List<PrescriptionListVO> list = result.getRecords().stream()
                .map(this::toPrescriptionListVO)
                .toList();
        return PageResult.of(result.getTotal(), list, page, size);
    }

    // ==================== 5.6.3 处方详情 ====================

    @Override
    public PrescriptionDetailVO getDetail(Long id) {
        Prescription prescription = getPrescriptionInScope(id);
        Doctor doctor = doctorMapper.selectById(prescription.getDoctorId());
        Department dept = doctor != null && doctor.getDeptId() != null
                ? departmentMapper.selectById(doctor.getDeptId()) : null;
        Patient patient = patientMapper.selectById(prescription.getPatientId());
        List<PrescriptionItem> items = prescriptionItemMapper.selectList(
                Wrappers.<PrescriptionItem>lambdaQuery()
                        .eq(PrescriptionItem::getPrescriptionId, id));
        Map<Long, Drug> drugMap = drugMapper.selectBatchIds(
                        items.stream().map(PrescriptionItem::getDrugId).toList()).stream()
                .collect(Collectors.toMap(Drug::getId, d -> d, (a, b) -> a));

        List<PrescriptionDetailVO.ItemVO> itemVOs = items.stream()
                .map(item -> {
                    Drug d = drugMap.get(item.getDrugId());
                    return PrescriptionDetailVO.ItemVO.builder()
                            .id(item.getId())
                            .drugId(item.getDrugId())
                            .drugName(d != null ? d.getName() : null)
                            .specification(d != null ? d.getSpecification() : null)
                            .dosage(item.getDosage())
                            .frequency(item.getFrequency())
                            .usageMethod(item.getUsageMethod())
                            .days(item.getDays())
                            .quantity(item.getQuantity())
                            .build();
                })
                .toList();

        return PrescriptionDetailVO.builder()
                .id(prescription.getId())
                .consultId(prescription.getConsultId())
                .doctor(PrescriptionDetailVO.DoctorInfo.builder()
                        .id(doctor != null ? doctor.getId() : null)
                        .name(doctor != null ? doctor.getName() : null)
                        .title(doctor != null ? doctor.getTitle() : null)
                        .deptName(dept != null ? dept.getName() : null)
                        .build())
                .patient(patient != null
                        ? PrescriptionDetailVO.PatientInfo.builder()
                        .id(patient.getId()).name(patient.getName())
                        .gender(patient.getGender()).dateOfBirth(patient.getDateOfBirth())
                        .build()
                        : null)
                .status(prescription.getStatus())
                .auditRequired(false)
                .riskWarnings(List.of())
                .items(itemVOs)
                .issuedAt(prescription.getIssuedAt())
                .auditedAt(prescription.getAuditedAt())
                .build();
    }

    /**
     * 校验处方存在且当前用户有权限查看。
     */
    private Prescription getPrescriptionInScope(Long id) {
        Prescription prescription = prescriptionMapper.selectById(id);
        if (prescription == null || prescription.getDeletedAt() != null) {
            throw new BusinessException("A0402", "处方不存在");
        }
        DataScope scope = currentUserService.getCurrentDataScope();
        Doctor doctor = doctorMapper.selectById(prescription.getDoctorId());
        if (doctor == null || doctor.getDeletedAt() != null
                || !doctor.getHospitalId().equals(scope.hospitalId())) {
            throw new BusinessException("3020", "无权查看该处方");
        }
        if (ROLE_DOCTOR.equals(scope.role()) && !scope.doctorId().equals(prescription.getDoctorId())) {
            throw new BusinessException("3020", "无权查看该处方");
        }
        return prescription;
    }

    private PrescriptionListVO toPrescriptionListVO(Prescription p) {
        Doctor doctor = doctorMapper.selectById(p.getDoctorId());
        Patient patient = patientMapper.selectById(p.getPatientId());
        long itemCount = prescriptionItemMapper.selectCount(
                Wrappers.<PrescriptionItem>lambdaQuery()
                        .eq(PrescriptionItem::getPrescriptionId, p.getId()));
        return PrescriptionListVO.builder()
                .id(p.getId())
                .consultId(p.getConsultId())
                .doctorName(doctor != null ? doctor.getName() : null)
                .patientName(patient != null ? patient.getName() : null)
                .status(p.getStatus())
                .itemCount((int) itemCount)
                .issuedAt(p.getIssuedAt())
                .build();
    }

    /**
     * 判断药品是否命中过敏原（简化匹配：药品名包含过敏原关键词）。
     */
    private boolean matchesAllergen(Drug drug, PatientAllergy allergy) {
        if (drug == null || allergy == null || allergy.getAllergen() == null) {
            return false;
        }
        String allergen = allergy.getAllergen().toLowerCase();
        String drugName = drug.getName() != null ? drug.getName().toLowerCase() : "";
        String indication = drug.getContraindication() != null ? drug.getContraindication().toLowerCase() : "";
        return drugName.contains(allergen) || indication.contains(allergen);
    }

    /**
     * 判断药品名是否含高危关键词，需人工审核。
     */
    private boolean containsHighRiskKeyword(String drugName) {
        if (drugName == null) return false;
        String name = drugName.toLowerCase();
        return name.contains("麻醉") || name.contains("精神") || name.contains("毒")
                || name.contains("抗凝") || name.contains("华法林");
    }
}
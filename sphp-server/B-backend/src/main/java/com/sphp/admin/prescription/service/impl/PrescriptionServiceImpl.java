package com.sphp.admin.prescription.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sphp.admin.auth.entity.Doctor;
import com.sphp.admin.auth.mapper.DoctorMapper;
import com.sphp.admin.common.enums.BRoleEnum;
import com.sphp.admin.common.enums.BUserStatusEnum;
import com.sphp.admin.common.CurrentUserService;
import com.sphp.admin.common.DataScope;
import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.doctor.entity.ConsultRecord;
import com.sphp.admin.doctor.entity.Patient;
import com.sphp.admin.doctor.mapper.ConsultRecordMapper;
import com.sphp.admin.doctor.mapper.BPatientMapper;
import com.sphp.admin.hospital.entity.Department;
import com.sphp.admin.hospital.mapper.DepartmentMapper;
import com.sphp.admin.prescription.dto.AuditRequest;
import com.sphp.admin.prescription.dto.PrescriptionDetailVO;
import com.sphp.admin.prescription.dto.PrescriptionListVO;
import com.sphp.admin.prescription.dto.PrescriptionPrecheckVO;
import com.sphp.admin.prescription.dto.PrescriptionSubmitRequest;
import com.sphp.admin.prescription.dto.PrescriptionSubmitVO;
import com.sphp.admin.prescription.dto.RiskWarningVO;
import com.sphp.admin.prescription.entity.Drug;
import com.sphp.admin.prescription.entity.Prescription;
import com.sphp.admin.prescription.entity.PrescriptionItem;
import com.sphp.admin.prescription.mapper.DrugMapper;
import com.sphp.admin.prescription.mapper.PrescriptionItemMapper;
import com.sphp.admin.prescription.mapper.PrescriptionMapper;
import com.sphp.admin.prescription.service.PrescriptionRiskChecker;
import com.sphp.admin.prescription.service.PrescriptionService;
import com.sphp.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 处方管理服务实现（管理员视角）。
 *
 * <p>按当前登录用户所属医院（{@code hospital_id}）做数据隔离过滤；
 * 医生角色（{@link BRoleEnum#DOCTOR}）进一步收窄到本人医生维度的处方，
 * 管理员/科室主任可查询全院或本科室处方。
 *
 * <p>风险拦截统一委托 {@link PrescriptionRiskChecker}：
 * <ul>
 *   <li>ERROR（红线）：过敏/禁忌强匹配 → 抛 ERR_RISK_REDLINE，不入库</li>
 *   <li>WARNING（提示）：重复用药 → 处方生效，返回告警</li>
 *   <li>AUDIT（审核）：高危药品 → 处方进入待审核队列</li>
 *   <li>无风险：直接 APPROVED</li>
 * </ul>
 *
 * @author lezoo17
 * @since 2026-08-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionServiceImpl implements PrescriptionService {

    /** 通用业务冲突（A0401：请求参数或业务前置条件不满足） */
    private static final String ERR_BUSINESS_CONFLICT = "A0401";
    /** 资源不存在 / 越权访问（A0402：通用资源未找到） */
    private static final String ERR_RESOURCE_NOT_FOUND = "A0402";
    /** 无权限（A0443：当前角色不允许操作） */
    private static final String ERR_NO_PERMISSION = "A0443";

    /** 处方域专用错误码 */
    private static final String ERR_CONSULT_INVALID = "3002";
    private static final String ERR_DRUG_INVALID = "3003";
    private static final String ERR_FORBIDDEN = "3020";

    /** 处方状态 */
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_SUBMITTED = "SUBMITTED";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";

    private final PrescriptionMapper prescriptionMapper;
    private final PrescriptionItemMapper prescriptionItemMapper;
    private final DrugMapper drugMapper;
    private final ConsultRecordMapper consultRecordMapper;
    private final BPatientMapper patientMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final CurrentUserService currentUserService;
    private final PrescriptionRiskChecker riskChecker;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrescriptionSubmitVO submit(PrescriptionSubmitRequest request) {
        return createFromItems(request.getConsultId(), request.getItems());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrescriptionSubmitVO createFromItems(Long consultId, List<PrescriptionSubmitRequest.ItemDTO> items) {
        DataScope scope = currentUserService.getCurrentDataScope();
        Long doctorId = scope.doctorId();
        if (doctorId == null) {
            throw new BusinessException(ERR_NO_PERMISSION, "当前用户无医生身份，无法开方");
        }

        // 1. 校验问诊记录
        ConsultRecord consult = consultRecordMapper.selectById(consultId);
        if (consult == null || consult.getDeletedAt() != null) {
            throw new BusinessException(ERR_CONSULT_INVALID, "问诊记录不存在或不可开方");
        }
        if (!STATUS_IN_PROGRESS.equals(consult.getStatus())) {
            throw new BusinessException(ERR_CONSULT_INVALID, "问诊状态不是 IN_PROGRESS，不可开方");
        }
        if (!consult.getDoctorId().equals(doctorId)) {
            throw new BusinessException(ERR_FORBIDDEN, "无权查看该处方");
        }

        // 2. 校验药品
        Set<Long> drugIds = items.stream().map(PrescriptionSubmitRequest.ItemDTO::getDrugId).collect(Collectors.toSet());
        Map<Long, Drug> drugMap = drugMapper.selectByIds(drugIds).stream()
                .filter(d -> d.getDeletedAt() == null)
                .collect(Collectors.toMap(Drug::getId, d -> d, (a, b) -> a));

        for (PrescriptionSubmitRequest.ItemDTO item : items) {
            Drug drug = drugMap.get(item.getDrugId());
            if (drug == null) {
                throw new BusinessException(ERR_DRUG_INVALID, "药品不存在或已停用");
            }
            if (!BUserStatusEnum.isEnabled(drug.getStatus())) {
                throw new BusinessException(ERR_DRUG_INVALID, "药品「" + drug.getName() + "」已停用");
            }
        }

        // 3. 风险拦截（过敏 ERROR / 禁忌 ERROR / 重复用药 WARNING / 高危药品 AUDIT）
        PrescriptionRiskChecker.RiskCheckResult risk = riskChecker.intercept(consult, drugMap, items);

        // 4. 判定处方状态
        boolean auditRequired = risk.isAuditRequired();
        String prescriptionStatus = auditRequired ? STATUS_SUBMITTED : STATUS_APPROVED;

        // 5. 入库（落库风险规则快照，供审核展示与追溯）
        Prescription prescription = new Prescription();
        prescription.setConsultId(consultId);
        prescription.setDoctorId(doctorId);
        prescription.setPatientId(consult.getPatientId());
        prescription.setStatus(prescriptionStatus);
        prescription.setRiskWarnings(risk.getWarnings());
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
                .riskWarnings(risk.getWarnings())
                .build();
    }

    @Override
    public PrescriptionPrecheckVO precheck(Long consultId, List<PrescriptionSubmitRequest.ItemDTO> items) {
        DataScope scope = currentUserService.getCurrentDataScope();
        Long doctorId = scope.doctorId();
        if (doctorId == null) {
            throw new BusinessException(ERR_NO_PERMISSION, "当前用户无医生身份，无法开方");
        }

        // 1. 校验问诊记录（只读预检：仅校验存在与归属，不要求 IN_PROGRESS）
        ConsultRecord consult = consultRecordMapper.selectById(consultId);
        if (consult == null || consult.getDeletedAt() != null) {
            throw new BusinessException(ERR_CONSULT_INVALID, "问诊记录不存在或不可开方");
        }
        if (!consult.getDoctorId().equals(doctorId)) {
            throw new BusinessException(ERR_FORBIDDEN, "无权查看该问诊记录");
        }

        // 2. 载入药品（过滤已删除）
        Set<Long> drugIds = items.stream()
                .map(PrescriptionSubmitRequest.ItemDTO::getDrugId)
                .collect(Collectors.toSet());
        Map<Long, Drug> drugMap = drugMapper.selectByIds(drugIds).stream()
                .filter(d -> d.getDeletedAt() == null)
                .collect(Collectors.toMap(Drug::getId, d -> d, (a, b) -> a));

        // 3. 风险预检（只读，不落库、不拦截）
        List<RiskWarningVO> warnings = riskChecker.precheck(consult, drugMap, items);
        return new PrescriptionPrecheckVO(warnings);
    }

    @Override
    public PageResult<PrescriptionListVO> page(Long consultId, Long patientId, String status, int page, int size) {
        DataScope scope = currentUserService.getCurrentDataScope();
        // 数据权限标识缺失（DEPT_HEAD 无科室）时按空数据返回，避免越权（与排班模块写法一致）
        if (BRoleEnum.DEPT_HEAD.equalsCode(scope.role()) && scope.deptId() == null) {
            return PageResult.of(0L, List.of(), page, size);
        }
        LambdaQueryWrapper<Prescription> wrapper = Wrappers.<Prescription>lambdaQuery()
                .eq(consultId != null, Prescription::getConsultId, consultId)
                .eq(patientId != null, Prescription::getPatientId, patientId)
                .isNull(Prescription::getDeletedAt)
                .apply("doctor_id IN (SELECT id FROM doctor WHERE hospital_id = {0} AND deleted_at IS NULL)",
                        scope.hospitalId());
        if (BRoleEnum.DOCTOR.equalsCode(scope.role())) {
            wrapper.eq(Prescription::getDoctorId, scope.doctorId());
        } else if (BRoleEnum.DEPT_HEAD.equalsCode(scope.role())) {
            // 科室主任：收窄到本科室医生开具的处方（与待审核列表的可见范围保持一致）
            wrapper.apply("doctor_id IN (SELECT id FROM doctor WHERE dept_id = {0} AND deleted_at IS NULL)",
                    scope.deptId());
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
        Map<Long, Drug> drugMap = drugMapper.selectByIds(
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
                .auditRequired(STATUS_SUBMITTED.equals(prescription.getStatus()))
                .riskWarnings(prescription.getRiskWarnings() != null
                        ? prescription.getRiskWarnings() : List.of())
                .rejectReason(prescription.getRejectReason())
                .items(itemVOs)
                .issuedAt(prescription.getIssuedAt())
                .auditedAt(prescription.getAuditedAt())
                .createdAt(prescription.getCreatedAt())
                .build();
    }

    /**
     * 校验处方存在且当前用户有权限查看。
     *
     * <p>校验维度：处方存在 → 处方医生归属当前医院 → 医生角色仅看本人。
     *
     * @param id 处方 ID
     * @return 处方实体
     * @throws BusinessException 不存在 / 跨院 / 跨医生时分别抛 ERR_RESOURCE_NOT_FOUND / ERR_FORBIDDEN
     */
    private Prescription getPrescriptionInScope(Long id) {
        Prescription prescription = prescriptionMapper.selectById(id);
        if (prescription == null || prescription.getDeletedAt() != null) {
            throw new BusinessException(ERR_RESOURCE_NOT_FOUND, "处方不存在");
        }
        DataScope scope = currentUserService.getCurrentDataScope();
        Doctor doctor = doctorMapper.selectById(prescription.getDoctorId());
        if (doctor == null || doctor.getDeletedAt() != null
                || !doctor.getHospitalId().equals(scope.hospitalId())) {
            throw new BusinessException(ERR_FORBIDDEN, "无权查看该处方");
        }
        if (BRoleEnum.DOCTOR.equalsCode(scope.role()) && !scope.doctorId().equals(prescription.getDoctorId())) {
            throw new BusinessException(ERR_FORBIDDEN, "无权查看该处方");
        }
        return prescription;
    }

    private PrescriptionListVO toPrescriptionListVO(Prescription p) {
        Doctor doctor = doctorMapper.selectById(p.getDoctorId());
        Patient patient = patientMapper.selectById(p.getPatientId());
        Department dept = doctor != null && doctor.getDeptId() != null
                ? departmentMapper.selectById(doctor.getDeptId()) : null;
        long itemCount = prescriptionItemMapper.selectCount(
                Wrappers.<PrescriptionItem>lambdaQuery()
                        .eq(PrescriptionItem::getPrescriptionId, p.getId()));
        return PrescriptionListVO.builder()
                .id(p.getId())
                .consultId(p.getConsultId())
                .doctorName(doctor != null ? doctor.getName() : null)
                .patientName(patient != null ? patient.getName() : null)
                .deptName(dept != null ? dept.getName() : null)
                .status(p.getStatus())
                .itemCount((int) itemCount)
                .riskWarnings(p.getRiskWarnings())
                .issuedAt(p.getIssuedAt())
                .build();
    }

    @Override
    public PageResult<PrescriptionListVO> pendingAuditList(int page, int size) {
        DataScope scope = currentUserService.getCurrentDataScope();
        String role = scope.role();
        if (!BRoleEnum.ADMIN.equalsCode(role) && !BRoleEnum.DEPT_HEAD.equalsCode(role)) {
            throw new BusinessException(ERR_NO_PERMISSION, "无审核权限");
        }
        // 数据权限标识缺失（DEPT_HEAD 无科室）时按空数据返回，避免越权（与排班模块写法一致）
        if (BRoleEnum.DEPT_HEAD.equalsCode(role) && scope.deptId() == null) {
            return PageResult.of(0L, List.of(), page, size);
        }

        LambdaQueryWrapper<Prescription> wrapper = Wrappers.<Prescription>lambdaQuery()
                .eq(Prescription::getStatus, STATUS_SUBMITTED)
                .isNull(Prescription::getDeletedAt)
                .apply("doctor_id IN (SELECT id FROM doctor WHERE hospital_id = {0} AND deleted_at IS NULL)",
                        scope.hospitalId());
        if (BRoleEnum.DEPT_HEAD.equalsCode(role)) {
            wrapper.apply("doctor_id IN (SELECT id FROM doctor WHERE dept_id = {0} AND deleted_at IS NULL)",
                    scope.deptId());
        }
        wrapper.orderByDesc(Prescription::getCreatedAt);

        Page<Prescription> result = prescriptionMapper.selectPage(new Page<>(page, size), wrapper);
        List<PrescriptionListVO> list = result.getRecords().stream()
                .map(this::toPrescriptionListVO)
                .toList();
        return PageResult.of(result.getTotal(), list, page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, AuditRequest request) {
        DataScope scope = currentUserService.getCurrentDataScope();
        String role = scope.role();
        if (!BRoleEnum.ADMIN.equalsCode(role) && !BRoleEnum.DEPT_HEAD.equalsCode(role)) {
            throw new BusinessException(ERR_NO_PERMISSION, "无审核权限");
        }

        Prescription prescription = prescriptionMapper.selectById(id);
        if (prescription == null || prescription.getDeletedAt() != null) {
            throw new BusinessException(ERR_RESOURCE_NOT_FOUND, "处方不存在");
        }
        if (!STATUS_SUBMITTED.equals(prescription.getStatus())) {
            throw new BusinessException(ERR_RESOURCE_NOT_FOUND, "处方状态不是待审核，无法审核");
        }

        Doctor doctor = doctorMapper.selectById(prescription.getDoctorId());
        if (doctor == null || doctor.getDeletedAt() != null
                || !doctor.getHospitalId().equals(scope.hospitalId())) {
            throw new BusinessException(ERR_FORBIDDEN, "无权审核该处方");
        }
        if (BRoleEnum.DEPT_HEAD.equalsCode(role) && scope.deptId() != null
                && !scope.deptId().equals(doctor.getDeptId())) {
            throw new BusinessException(ERR_FORBIDDEN, "无权审核本科室以外的处方");
        }

        String action = request.getAction();
        if (STATUS_APPROVED.equals(action)) {
            prescription.setStatus(STATUS_APPROVED);
            prescription.setIssuedAt(OffsetDateTime.now());
            prescription.setAuditedAt(OffsetDateTime.now());
        } else if (STATUS_REJECTED.equals(action)) {
            if (request.getRejectReason() == null || request.getRejectReason().isBlank()) {
                throw new BusinessException(ERR_BUSINESS_CONFLICT, "驳回时必须填写驳回原因");
            }
            prescription.setStatus(STATUS_REJECTED);
            prescription.setRejectReason(request.getRejectReason().trim());
            prescription.setAuditedAt(OffsetDateTime.now());
        }
        prescriptionMapper.updateById(prescription);
        log.info("审核处方 prescriptionId={}, action={}, auditor={}", id, action, role);
    }
}
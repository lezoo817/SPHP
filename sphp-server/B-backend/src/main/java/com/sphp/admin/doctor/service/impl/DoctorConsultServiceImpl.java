package com.sphp.admin.doctor.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sphp.admin.auth.entity.Doctor;
import com.sphp.admin.auth.mapper.DoctorMapper;
import com.sphp.admin.common.CurrentUserService;
import com.sphp.admin.common.DataScope;
import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.doctor.dto.ConsultEndVO;
import com.sphp.admin.doctor.dto.ConsultHistoryDetailVO;
import com.sphp.admin.doctor.dto.ConsultHistoryVO;
import com.sphp.admin.doctor.dto.ConsultStartVO;
import com.sphp.admin.doctor.dto.MessageVO;
import com.sphp.admin.doctor.dto.NoteSaveVO;
import com.sphp.admin.doctor.dto.PatientDetailVO;
import com.sphp.admin.doctor.dto.QueueItemVO;
import com.sphp.admin.doctor.entity.ConsultRecord;
import com.sphp.admin.doctor.entity.ConsultationMessage;
import com.sphp.admin.doctor.entity.Patient;
import com.sphp.admin.doctor.entity.PatientAllergy;
import com.sphp.admin.doctor.entity.PatientMedicalHistory;
import com.sphp.admin.doctor.mapper.BConsultationMessageMapper;
import com.sphp.admin.doctor.mapper.BPatientMapper;
import com.sphp.admin.doctor.mapper.ConsultRecordMapper;
import com.sphp.admin.doctor.mapper.BAppointmentMapper;
import com.sphp.admin.doctor.mapper.BPatientAllergyMapper;
import com.sphp.admin.doctor.mapper.BPatientMedicalHistoryMapper;
import com.sphp.admin.doctor.mapper.QueueRow;
import com.sphp.admin.doctor.mapper.SlotTimeInfo;
import com.sphp.admin.doctor.service.DoctorConsultService;
import com.sphp.admin.prescription.entity.Prescription;
import com.sphp.admin.prescription.entity.PrescriptionItem;
import com.sphp.admin.prescription.mapper.PrescriptionItemMapper;
import com.sphp.admin.prescription.mapper.PrescriptionMapper;
import com.sphp.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 接诊台服务实现（系分 §5.5）。
 *
 * <p>数据权限遵循 §7.2：查询按当前用户角色显式过滤。
 * 写操作（开始/结束接诊、保存病历、发送消息）校验当前医生对问诊记录的操作权限。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorConsultServiceImpl implements DoctorConsultService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_DEPT_HEAD = "DEPT_HEAD";
    private static final String ROLE_DOCTOR = "DOCTOR";

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_NO_SHOW = "NO_SHOW";

    private static final String PRESCRIPTION_DRAFT = "DRAFT";

    private final ConsultRecordMapper consultRecordMapper;
    private final BPatientMapper patientMapper;
    private final BPatientAllergyMapper patientAllergyMapper;
    private final BPatientMedicalHistoryMapper patientMedicalHistoryMapper;
    private final BConsultationMessageMapper consultationMessageMapper;
    private final BAppointmentMapper appointmentMapper;
    private final PrescriptionMapper prescriptionMapper;
    private final PrescriptionItemMapper prescriptionItemMapper;
    private final DoctorMapper doctorMapper;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    // ==================== 5.5.1 待接诊列表 ====================

    @Override
    public PageResult<QueueItemVO> pageQueue(Long deptId, String status, int page, int size) {
        DataScope scope = currentUserService.getCurrentDataScope();
        // 数据权限标识缺失时返回空数据
        if ((ROLE_DEPT_HEAD.equals(scope.role()) && scope.deptId() == null)
                || (ROLE_DOCTOR.equals(scope.role()) && scope.doctorId() == null)) {
            return PageResult.of(0, List.of(), page, size);
        }

        String queryStatus = StringUtils.hasText(status) ? status : STATUS_PENDING;
        List<Long> doctorIds = resolveDoctorIds(scope, deptId);

        if (doctorIds.isEmpty()) {
            return PageResult.of(0, List.of(), page, size);
        }

        // 自动补建缺失的 consult_record：C端挂号成功后未创建问诊记录时，查询前幂等补齐
        ensureConsultRecordsExist(doctorIds);

        // 批量过期已过期的 PENDING 记录与 PAID 挂号订单，保持队列干净
        int expiredCount = consultRecordMapper.batchExpireOldPending(doctorIds);
        int expiredApptCount = appointmentMapper.batchExpireOldPaid(doctorIds, OffsetDateTime.now());
        if (expiredCount > 0 || expiredApptCount > 0) {
            log.info("过期处理：consult_record NO_SHOW {} 条, appointment EXPIRED {} 条", expiredCount, expiredApptCount);
        }

        // 对于 ADMIN/DEPT_HEAD，deptId 可传参过滤；DOCTOR 只看本人
        Long queryDeptId = (ROLE_ADMIN.equals(scope.role()) && deptId != null) ? deptId
                : (ROLE_DEPT_HEAD.equals(scope.role()) ? scope.deptId() : null);

        IPage<QueueRow> result = consultRecordMapper.selectQueuePage(
                new Page<>(page, size), queryStatus, doctorIds, queryDeptId);

        List<QueueItemVO> list = result.getRecords().stream()
                .map(this::toQueueItemVO)
                .toList();
        return PageResult.of(result.getTotal(), list, page, size);
    }

    // ==================== 5.5.2 患者详情 ====================

    @Override
    public PatientDetailVO getPatientDetail(Long consultId) {
        ConsultRecord record = getConsultInScope(consultId);
        Patient patient = patientMapper.selectById(record.getPatientId());
        if (patient == null || patient.getDeletedAt() != null) {
            throw new BusinessException("3001", "患者不存在");
        }

        // 过敏史
        List<PatientAllergy> allergies = patientAllergyMapper.selectList(
                Wrappers.<PatientAllergy>lambdaQuery()
                        .eq(PatientAllergy::getPatientId, patient.getId())
                        .isNull(PatientAllergy::getDeletedAt));

        // 既往史
        List<PatientMedicalHistory> histories = patientMedicalHistoryMapper.selectList(
                Wrappers.<PatientMedicalHistory>lambdaQuery()
                        .eq(PatientMedicalHistory::getPatientId, patient.getId())
                        .isNull(PatientMedicalHistory::getDeletedAt));

        // AI 摘要
        Map<String, Object> aiSummary = parseAiSummary(record.getAiSummary());

        // 近期处方
        List<PatientDetailVO.RecentPrescriptionInfo> recentPrescriptions = loadRecentPrescriptions(patient.getId());

        // 历史就诊记录
        DataScope scope = currentUserService.getCurrentDataScope();
        List<PatientDetailVO.HistoryRecordInfo> historyRecords = loadHistoryRecords(patient.getId(), scope.hospitalId());

        // 脱敏
        String phone = maskPhone(patient.getPhoneCiphertext());
        String emergencyContact = maskPhone(patient.getEmergencyContact());

        return PatientDetailVO.builder()
                .consultId(record.getId())
                .doctorNote(record.getDoctorNote())
                .patient(PatientDetailVO.PatientInfo.builder()
                        .id(patient.getId())
                        .name(patient.getName())
                        .gender(patient.getGender())
                        .dateOfBirth(patient.getDateOfBirth())
                        .phone(phone)
                        .emergencyContact(emergencyContact)
                        .build())
                .allergies(allergies.stream()
                        .map(a -> PatientDetailVO.AllergyInfo.builder()
                                .id(a.getId()).allergen(a.getAllergen())
                                .reaction(a.getReaction()).severity(a.getSeverity())
                                .build())
                        .toList())
                .medicalHistories(histories.stream()
                        .map(h -> PatientDetailVO.MedicalHistoryInfo.builder()
                                .id(h.getId()).content(h.getContent())
                                .occurredAt(h.getOccurredAt())
                                .build())
                        .toList())
                .aiSummary(aiSummary)
                .recentPrescriptions(recentPrescriptions)
                .historyRecords(historyRecords)
                .build();
    }

    // ==================== 5.5.3 开始接诊 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConsultStartVO startConsult(Long consultId) {
        ConsultRecord record = getConsultInScope(consultId);
        if (!STATUS_PENDING.equals(record.getStatus())) {
            throw new BusinessException("3011", "问诊记录状态不可接诊");
        }
        // 校验该医生当前无其他 IN_PROGRESS 接诊
        long inProgress = consultRecordMapper.countInProgressByDoctor(record.getDoctorId());
        if (inProgress > 0) {
            throw new BusinessException("3012", "医生当前存在未结束的接诊记录");
        }

        // 校验当前时间是否在号源时段内
        SlotTimeInfo slotTime = consultRecordMapper.selectSlotTimeByConsultId(consultId);
        if (slotTime == null) {
            throw new BusinessException("A0400", "未找到号源时段信息，无法接诊");
        }
        LocalTime now = LocalTime.now();
        if (now.isBefore(slotTime.getStartTime()) || now.isAfter(slotTime.getEndTime())) {
            throw new BusinessException("A0443", "当前不在接诊时间内（" + slotTime.getStartTime() + "~" + slotTime.getEndTime() + "）");
        }

        record.setStatus(STATUS_IN_PROGRESS);
        record.setStartedAt(OffsetDateTime.now());
        record.setUpdatedAt(OffsetDateTime.now());
        consultRecordMapper.updateById(record);

        log.info("开始接诊 consultId={}, doctorId={}", consultId, record.getDoctorId());
        return ConsultStartVO.builder()
                .consultId(record.getId())
                .status(STATUS_IN_PROGRESS)
                .startedAt(record.getStartedAt())
                .build();
    }

    // ==================== 5.5.4 结束问诊 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConsultEndVO endConsult(Long consultId) {
        ConsultRecord record = getConsultInScope(consultId);
        if (!STATUS_IN_PROGRESS.equals(record.getStatus())) {
            throw new BusinessException("3013", "问诊状态不是 IN_PROGRESS");
        }
        // 校验无未签名的 DRAFT 处方
        long draftPrescriptions = prescriptionMapper.selectCount(Wrappers.<Prescription>lambdaQuery()
                .eq(Prescription::getConsultId, consultId)
                .eq(Prescription::getStatus, PRESCRIPTION_DRAFT)
                .isNull(Prescription::getDeletedAt));
        if (draftPrescriptions > 0) {
            throw new BusinessException("3014", "存在未签名的处方草稿，请先处理");
        }

        record.setStatus(STATUS_COMPLETED);
        record.setEndedAt(OffsetDateTime.now());
        record.setUpdatedAt(OffsetDateTime.now());
        consultRecordMapper.updateById(record);

        // 同步更新挂号订单状态，确保 C 端就诊助手能正确反映就诊完成
        appointmentMapper.completeStatus(record.getAppointmentId(), OffsetDateTime.now());

        log.info("结束问诊 consultId={}, doctorId={}", consultId, record.getDoctorId());
        return ConsultEndVO.builder()
                .consultId(record.getId())
                .status(STATUS_COMPLETED)
                .endedAt(record.getEndedAt())
                .build();
    }

    // ==================== 5.5.5 保存病历 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteSaveVO saveNote(Long consultId, String doctorNote) {
        if (!StringUtils.hasText(doctorNote) || doctorNote.length() > 10000) {
            throw new BusinessException("A0400", "病历内容不能为空且不超过10000字符");
        }
        ConsultRecord record = getConsultInScope(consultId);

        record.setDoctorNote(doctorNote);
        record.setUpdatedAt(OffsetDateTime.now());
        consultRecordMapper.updateById(record);

        log.info("保存病历 consultId={}", consultId);
        return NoteSaveVO.builder()
                .consultId(record.getId())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

    // ==================== 5.5.6 查询消息历史 ====================

    @Override
    public PageResult<MessageVO> pageMessages(Long consultationId, int page, int size) {
        // 校验权限
        getConsultInScope(consultationId);

        Page<ConsultationMessage> result = consultationMessageMapper.selectPage(
                new Page<>(page, size),
                Wrappers.<ConsultationMessage>lambdaQuery()
                        .eq(ConsultationMessage::getConsultId, consultationId)
                        .isNull(ConsultationMessage::getDeletedAt)
                        .orderByAsc(ConsultationMessage::getCreatedAt));

        List<MessageVO> list = result.getRecords().stream()
                .map(m -> MessageVO.builder()
                        .messageId(m.getId())
                        .senderType(m.getSenderType())
                        .content(m.getContent())
                        .createdAt(m.getCreatedAt())
                        .build())
                .toList();
        return PageResult.of(result.getTotal(), list, page, size);
    }

    // ==================== 5.5.7 发送消息 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageVO sendMessage(Long consultationId, String content) {
        if (!StringUtils.hasText(content) || content.length() > 2000) {
            throw new BusinessException("A0400", "消息内容不能为空且不超过2000字符");
        }
        ConsultRecord record = getConsultInScope(consultationId);
        if (!STATUS_IN_PROGRESS.equals(record.getStatus())) {
            throw new BusinessException("3013", "问诊状态不是 IN_PROGRESS，禁止发送消息");
        }

        ConsultationMessage message = new ConsultationMessage();
        message.setConsultId(consultationId);
        message.setSenderType("DOCTOR");
        message.setContent(content);
        consultationMessageMapper.insert(message);

        log.info("发送问诊消息 consultId={}, messageId={}", consultationId, message.getId());
        return MessageVO.builder()
                .messageId(message.getId())
                .senderType("DOCTOR")
                .content(content)
                .createdAt(message.getCreatedAt())
                .build();
    }

    // ==================== 接诊历史 ====================

    @Override
    public PageResult<ConsultHistoryVO> pageHistory(int page, int size) {
        DataScope scope = currentUserService.getCurrentDataScope();
        Long doctorId = scope.doctorId();
        if (doctorId == null) {
            return PageResult.of(0, List.of(), page, size);
        }

        Page<ConsultRecord> result = consultRecordMapper.selectPage(
                new Page<>(page, size),
                Wrappers.<ConsultRecord>lambdaQuery()
                        .eq(ConsultRecord::getDoctorId, doctorId)
                        .ne(ConsultRecord::getStatus, STATUS_PENDING)
                        .isNull(ConsultRecord::getDeletedAt)
                        .orderByDesc(ConsultRecord::getEndedAt, ConsultRecord::getCreatedAt));

        List<ConsultHistoryVO> list = result.getRecords().stream()
                .map(this::toConsultHistoryVO)
                .toList();
        return PageResult.of(result.getTotal(), list, page, size);
    }

    @Override
    public ConsultHistoryDetailVO getHistoryDetail(Long consultId) {
        ConsultRecord record = getConsultInScope(consultId);
        List<Prescription> prescriptions = prescriptionMapper.selectList(
                Wrappers.<Prescription>lambdaQuery()
                        .eq(Prescription::getConsultId, consultId)
                        .isNull(Prescription::getDeletedAt)
                        .orderByDesc(Prescription::getCreatedAt));
        List<ConsultHistoryDetailVO.PrescriptionBrief> briefs = prescriptions.stream()
                .map(p -> {
                    long cnt = prescriptionItemMapper.selectCount(
                            Wrappers.<PrescriptionItem>lambdaQuery()
                                    .eq(com.sphp.admin.prescription.entity.PrescriptionItem::getPrescriptionId, p.getId()));
                    return ConsultHistoryDetailVO.PrescriptionBrief.builder()
                            .id(p.getId()).status(p.getStatus())
                            .itemCount((int) cnt).issuedAt(p.getIssuedAt())
                            .build();
                })
                .toList();
        return ConsultHistoryDetailVO.builder()
                .consultId(record.getId())
                .patientId(record.getPatientId())
                .status(record.getStatus())
                .chiefComplaint(record.getChiefComplaint())
                .doctorNote(record.getDoctorNote())
                .startedAt(record.getStartedAt())
                .endedAt(record.getEndedAt())
                .createdAt(record.getCreatedAt())
                .prescriptions(briefs)
                .build();
    }

    // ==================== 私有方法 ====================

    /**
     * 获取问诊记录并校验当前用户的数据权限。
     */
    private ConsultRecord getConsultInScope(Long consultId) {
        ConsultRecord record = consultRecordMapper.selectById(consultId);
        if (record == null || record.getDeletedAt() != null) {
            throw new BusinessException("A0402", "问诊记录不存在");
        }
        DataScope scope = currentUserService.getCurrentDataScope();
        Doctor doctor = doctorMapper.selectById(record.getDoctorId());
        if (doctor == null || doctor.getDeletedAt() != null
                || !doctor.getHospitalId().equals(scope.hospitalId())) {
            throw new BusinessException("3010", "无权查看该患者");
        }
        if (ROLE_DOCTOR.equals(scope.role()) && !scope.doctorId().equals(record.getDoctorId())) {
            throw new BusinessException("3010", "无权查看该患者");
        }
        if (ROLE_DEPT_HEAD.equals(scope.role())) {
            Doctor consultDoctor = doctorMapper.selectById(record.getDoctorId());
            if (consultDoctor == null || !consultDoctor.getDeptId().equals(scope.deptId())) {
                throw new BusinessException("3010", "无权查看该患者");
            }
        }
        return record;
    }

    /**
     * 自动补建缺失的 consult_record（幂等）。
     * C端挂号成功后未创建 consult_record，B端查询队列前补齐，对双方无侵入。
     */
    private void ensureConsultRecordsExist(List<Long> doctorIds) {
        int inserted = consultRecordMapper.batchCreateIfMissing(doctorIds);
        if (inserted > 0) {
            log.info("自动补建 consult_record {} 条", inserted);
        }
    }

    /**
     * 根据数据权限解析可查询的医生 ID 列表。
     */
    private List<Long> resolveDoctorIds(DataScope scope, Long deptId) {
        return switch (scope.role()) {
            case ROLE_ADMIN -> doctorMapper.selectList(Wrappers.<Doctor>lambdaQuery()
                            .eq(Doctor::getHospitalId, scope.hospitalId())
                            .eq(deptId != null, Doctor::getDeptId, deptId)
                            .isNull(Doctor::getDeletedAt))
                    .stream()
                    .map(Doctor::getId)
                    .toList();
            case ROLE_DEPT_HEAD -> doctorMapper.selectList(Wrappers.<Doctor>lambdaQuery()
                            .eq(Doctor::getDeptId, scope.deptId())
                            .isNull(Doctor::getDeletedAt))
                    .stream()
                    .map(Doctor::getId)
                    .toList();
            case ROLE_DOCTOR -> scope.doctorId() != null
                    ? List.of(scope.doctorId())
                    : List.of();
            default -> List.of();
        };
    }

    /**
     * 将 QueueRow 转换为 QueueItemVO。
     */
    private QueueItemVO toQueueItemVO(QueueRow row) {
        Map<String, Object> aiSummary = parseAiSummary(row.getAiSummary());
        int age = row.getPatientDateOfBirth() != null
                ? Period.between(row.getPatientDateOfBirth(), LocalDate.now()).getYears()
                : 0;
        return QueueItemVO.builder()
                .consultId(row.getConsultId())
                .patientId(row.getPatientId())
                .patientName(row.getPatientName())
                .patientGender(row.getPatientGender())
                .patientAge(age)
                .aiSummary(aiSummary)
                .queueNumber(row.getQueueNumber())
                .appointmentTime(row.getAppointmentTime())
                .status(row.getStatus())
                .slotStartTime(row.getSlotStartTime() != null ? row.getSlotStartTime().toString() : null)
                .slotEndTime(row.getSlotEndTime() != null ? row.getSlotEndTime().toString() : null)
                .build();
    }

    /**
     * 将 ConsultRecord 转换为 ConsultHistoryVO。
     */
    private ConsultHistoryVO toConsultHistoryVO(ConsultRecord r) {
        Patient patient = r.getPatientId() != null ? patientMapper.selectById(r.getPatientId()) : null;
        int age = patient != null && patient.getDateOfBirth() != null
                ? Period.between(patient.getDateOfBirth(), LocalDate.now()).getYears()
                : 0;
        String noteSummary = r.getDoctorNote() != null
                ? r.getDoctorNote().substring(0, Math.min(r.getDoctorNote().length(), 100))
                : null;
        return ConsultHistoryVO.builder()
                .consultId(r.getId())
                .patientId(r.getPatientId())
                .patientName(patient != null ? patient.getName() : null)
                .patientGender(patient != null ? patient.getGender() : null)
                .patientDateOfBirth(patient != null ? patient.getDateOfBirth() : null)
                .chiefComplaint(r.getChiefComplaint())
                .noteSummary(noteSummary)
                .status(r.getStatus())
                .startedAt(r.getStartedAt())
                .endedAt(r.getEndedAt())
                .createdAt(r.getCreatedAt())
                .build();
    }

    /**
     * 解析 ai_summary jsonb 字段为 Map。
     */
    private Map<String, Object> parseAiSummary(String aiSummaryJson) {
        if (!StringUtils.hasText(aiSummaryJson)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(aiSummaryJson, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("解析 ai_summary 失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 加载患者近期处方。
     */
    private List<PatientDetailVO.RecentPrescriptionInfo> loadRecentPrescriptions(Long patientId) {
        List<Prescription> prescriptions = prescriptionMapper.selectList(
                Wrappers.<Prescription>lambdaQuery()
                        .eq(Prescription::getPatientId, patientId)
                        .isNull(Prescription::getDeletedAt)
                        .orderByDesc(Prescription::getCreatedAt)
                        .last("LIMIT 10"));
        return prescriptions.stream()
                .map(p -> PatientDetailVO.RecentPrescriptionInfo.builder()
                        .id(p.getId())
                        .status(p.getStatus())
                        .issuedAt(p.getIssuedAt())
                        .build())
                .toList();
    }

    /**
     * 加载患者在本医院的历史就诊记录（跨医生，不限当前医生）。
     *
     * <p>通过 doctor 表过滤同医院 + 关联医生姓名，让医生了解患者在本院的其他就诊情况。
     */
    private List<PatientDetailVO.HistoryRecordInfo> loadHistoryRecords(Long patientId, Long hospitalId) {
        List<ConsultRecord> records = consultRecordMapper.selectList(
                Wrappers.<ConsultRecord>lambdaQuery()
                        .eq(ConsultRecord::getPatientId, patientId)
                        .isNull(ConsultRecord::getDeletedAt)
                        .orderByDesc(ConsultRecord::getCreatedAt)
                        .last("LIMIT 20"));
        if (records.isEmpty()) {
            return List.of();
        }

        // 批量查询关联医生，只保留本院医生 + 构建医生姓名映射
        List<Long> doctorIds = records.stream()
                .map(ConsultRecord::getDoctorId)
                .distinct()
                .toList();
        Map<Long, String> doctorNameMap = doctorMapper.selectList(
                        Wrappers.<Doctor>lambdaQuery()
                                .in(Doctor::getId, doctorIds)
                                .eq(Doctor::getHospitalId, hospitalId)
                                .isNull(Doctor::getDeletedAt))
                .stream()
                .collect(Collectors.toMap(Doctor::getId, Doctor::getName));

        return records.stream()
                .filter(r -> doctorNameMap.containsKey(r.getDoctorId()))
                .map(r -> PatientDetailVO.HistoryRecordInfo.builder()
                        .date(r.getCreatedAt() != null ? r.getCreatedAt().toLocalDate().toString() : null)
                        .type("病历")
                        .doctorName(doctorNameMap.get(r.getDoctorId()))
                        .summary(r.getDoctorNote() != null
                                ? r.getDoctorNote().substring(0, Math.min(r.getDoctorNote().length(), 50))
                                : null)
                        .status(r.getStatus())
                        .build())
                .toList();
    }

    /**
     * 手机号脱敏：保留前3位后4位，中间 ****。
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
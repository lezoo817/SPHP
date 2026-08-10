package com.sphp.admin.doctor.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sphp.admin.auth.entity.Doctor;
import com.sphp.admin.auth.mapper.DoctorMapper;
import com.sphp.admin.common.CurrentUserService;
import com.sphp.admin.common.DataScope;
import com.sphp.admin.common.constant.OnlineConsultationConstant;
import com.sphp.admin.common.enums.BRoleEnum;
import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.doctor.dto.AllergyCreateRequest;
import com.sphp.admin.doctor.dto.ConsultEndVO;
import com.sphp.admin.doctor.dto.ConsultHistoryDetailVO;
import com.sphp.admin.doctor.dto.ConsultHistoryVO;
import com.sphp.admin.doctor.dto.ConsultStartVO;
import com.sphp.admin.doctor.dto.MessageVO;
import com.sphp.admin.doctor.dto.OnlineConsultationMessageSendRequest;
import com.sphp.admin.doctor.dto.NoteSaveVO;
import com.sphp.admin.doctor.dto.OnlineConsultationDetailVO;
import com.sphp.admin.doctor.dto.OnlineConsultationItemVO;
import com.sphp.admin.doctor.dto.OnlineConsultationMessagePageVO;
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
import com.sphp.admin.prescription.service.PrescriptionService;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import com.sphp.shared.exception.BusinessException;
import com.sphp.shared.event.ConsultationMessageCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 接诊台服务实现（管理员视角）。
 *
 * <p>面向 B 端医生 / 科室主任 / 医院管理员，处理待接诊队列、患者详情、开始/结束接诊、病历保存、问诊消息。
 * 数据隔离边界：所有读操作通过当前用户的 {@link DataScope}（医院 / 科室 / 医生）显式过滤可见问诊记录；
 * 写操作（开始 / 结束 / 病历 / 消息）必须先校验当前用户对目标问诊的归属与状态机合法性。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorConsultServiceImpl implements DoctorConsultService {

    /** 问诊记录状态：待接诊（C 端挂号已支付，B 端尚未开始） */
    private static final String STATUS_PENDING = "PENDING";
    /** 问诊记录状态：进行中（医生已开始接诊） */
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 问诊记录状态：已完成（医生已结束问诊） */
    private static final String STATUS_COMPLETED = "COMPLETED";
    /** 问诊记录状态：未到 / 过期未就诊 */
    private static final String STATUS_NO_SHOW = "NO_SHOW";

    /** 处方状态：草稿（未签名） */
    private static final String PRESCRIPTION_DRAFT = "DRAFT";

    /** 问诊消息发送方类型：医生 */
    private static final String SENDER_DOCTOR = "DOCTOR";

    /** 病历文本最大长度（与服务端校验、NoteSaveRequest 一致） */
    private static final int MAX_NOTE_LENGTH = 10_000;
    /** 问诊消息最大长度（与 MessageSendRequest 一致） */
    private static final int MAX_MESSAGE_LENGTH = 2_000;
    /** 近期处方展示上限（按创建时间倒序截取） */
    private static final int RECENT_PRESCRIPTION_LIMIT = 10;
    /** 历史就诊记录展示上限 */
    private static final int HISTORY_RECORD_LIMIT = 20;
    /** 病历摘要截断长度（ConsultHistoryVO） */
    private static final int NOTE_SUMMARY_MAX_LENGTH = 100;
    /** 历史就诊病历摘要截断长度（PatientDetailVO.HistoryRecordInfo） */
    private static final int HISTORY_NOTE_SUMMARY_MAX_LENGTH = 50;
    /** 手机号脱敏保留前缀长度 */
    private static final int PHONE_MASK_PREFIX = 3;
    /** 手机号脱敏保留后缀长度 */
    private static final int PHONE_MASK_SUFFIX = 4;
    /** 手机号脱敏最短长度（低于该长度不脱敏，避免越界） */
    private static final int PHONE_MASK_MIN_LENGTH = 7;

    // ============ 业务错误码（30xx 接诊台域） ============

    /** 患者不存在（3001） */
    private static final String ERR_PATIENT_NOT_FOUND = "3001";
    /** 无权访问该问诊 / 患者（3010） */
    private static final String ERR_NO_PERMISSION = "3010";
    /** 问诊记录状态不可接诊（3011） */
    private static final String ERR_CONSULT_STATUS_INVALID = "3011";
    /** 医生当前存在未结束的接诊（3012） */
    private static final String ERR_DOCTOR_BUSY = "3012";
    /** 问诊状态不是 IN_PROGRESS，禁止对应操作（3013） */
    private static final String ERR_CONSULT_NOT_IN_PROGRESS = "3013";
    /** 存在未签名的处方草稿（3014） */
    private static final String ERR_HAS_DRAFT_PRESCRIPTION = "3014";

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
    private final PrescriptionService prescriptionService;
    private final ApplicationEventPublisher eventPublisher;

    /** 在线问诊允许查询的状态。 */
    private static final Set<String> ONLINE_STATUSES = Set.of(
            OnlineConsultationConstant.STATUS_PENDING,
            OnlineConsultationConstant.STATUS_IN_PROGRESS,
            OnlineConsultationConstant.STATUS_COMPLETED);

    @Override
    public PageResult<OnlineConsultationItemVO> pageOnlineConsultations(String status, int page, int size) {
        String queryStatus = StringUtils.hasText(status)
                ? status.trim().toUpperCase()
                : OnlineConsultationConstant.STATUS_PENDING;
        if (!ONLINE_STATUSES.contains(queryStatus)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAMETER, "在线问诊状态不合法");
        }
        DataScope scope = currentUserService.getCurrentDataScope();
        List<Long> doctorIds = resolveDoctorIds(scope, null);
        if (doctorIds.isEmpty()) {
            return PageResult.of(0, List.of(), page, size);
        }

        // 在线问诊以 appointment_id 为空作为唯一边界，不与挂号接诊混查。
        Page<ConsultRecord> result = consultRecordMapper.selectPage(
                new Page<>(page, size),
                Wrappers.<ConsultRecord>lambdaQuery()
                        .in(ConsultRecord::getDoctorId, doctorIds)
                        .isNull(ConsultRecord::getAppointmentId)
                        .eq(ConsultRecord::getStatus, queryStatus)
                        .isNull(ConsultRecord::getDeletedAt)
                        .orderByDesc(ConsultRecord::getPreConsultationSubmittedAt, ConsultRecord::getId));
        List<OnlineConsultationItemVO> list = result.getRecords().stream()
                .map(this::toOnlineConsultationItem)
                .toList();
        return PageResult.of(result.getTotal(), list, page, size);
    }

    @Override
    public OnlineConsultationDetailVO getOnlineConsultationDetail(Long consultId) {
        ConsultRecord record = getOnlineConsultInScope(consultId);
        PatientDetailVO patientDetail = getPatientDetail(consultId);
        List<MessageVO> messages = pageMessages(consultId, 1, 100).getList();
        return OnlineConsultationDetailVO.builder()
                .consultId(record.getId())
                .appointmentId(record.getAppointmentId())
                .status(record.getStatus())
                .chiefComplaint(record.getChiefComplaint())
                .historyOfPresentIllness(record.getHistoryOfPresentIllness())
                .submittedAt(record.getPreConsultationSubmittedAt())
                .doctorRepliedAt(record.getDoctorRepliedAt())
                .patientDetail(patientDetail)
                .messages(messages)
                .prescriptions(prescriptionService.page(consultId, null, null, 1, 100).getList())
                .canStart(OnlineConsultationConstant.STATUS_PENDING.equals(record.getStatus()))
                .canReply(OnlineConsultationConstant.STATUS_IN_PROGRESS.equals(record.getStatus())
                        && record.getDoctorRepliedAt() == null)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConsultStartVO startOnlineConsult(Long consultId) {
        ConsultRecord record = getOnlineConsultInScope(consultId);
        Long doctorId = requireAssignedDoctor(record);
        OffsetDateTime startedAt = OffsetDateTime.now();
        // 状态条件更新保证重复点击不会重复进入编辑流程。
        if (consultRecordMapper.startOnlineConsult(consultId, doctorId, startedAt) != 1) {
            throw new BusinessException(ERR_CONSULT_STATUS_INVALID, "在线问诊状态不可开始回复");
        }
        return ConsultStartVO.builder()
                .consultId(consultId)
                .status(OnlineConsultationConstant.STATUS_IN_PROGRESS)
                .startedAt(startedAt)
                .build();
    }

    /**
     * 发送在线问诊医生文字消息。
     *
     * @param consultId 问诊记录 ID
     * @param request 消息请求
     * @return 已保存消息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageVO sendOnlineConsultationMessage(Long consultId, OnlineConsultationMessageSendRequest request) {
        ConsultRecord record = consultRecordMapper.lockOnlineConsult(consultId);
        if (record == null) {
            throw new BusinessException(ERR_CONSULT_STATUS_INVALID, "在线问诊不存在");
        }
        Long doctorId = requireAssignedDoctor(record);
        if (!OnlineConsultationConstant.STATUS_IN_PROGRESS.equals(record.getStatus())) {
            throw new BusinessException(ERR_CONSULT_STATUS_INVALID, "在线问诊状态不是 IN_PROGRESS，禁止发送消息");
        }
        String content = request.getContent().trim();
        ConsultationMessage existing = consultationMessageMapper.selectOne(
                Wrappers.<ConsultationMessage>lambdaQuery().eq(ConsultationMessage::getConsultId, consultId)
                        .eq(ConsultationMessage::getClientMessageId, request.getClientMessageId())
                        .isNull(ConsultationMessage::getDeletedAt));
        if (existing != null) {
            if (!OnlineConsultationConstant.SENDER_DOCTOR.equals(existing.getSenderType())
                    || !content.equals(existing.getContent())) {
                throw new BusinessException(ERR_CONSULT_STATUS_INVALID, "clientMessageId 已用于其他消息");
            }
            return toMessageVO(existing);
        }
        OffsetDateTime now = OffsetDateTime.now();
        ConsultationMessage message = new ConsultationMessage();
        message.setConsultId(consultId);
        message.setSenderType(OnlineConsultationConstant.SENDER_DOCTOR);
        message.setContent(content);
        message.setClientMessageId(request.getClientMessageId());
        message.setMessageType("TEXT");
        message.setCreatedAt(now);
        if (consultationMessageMapper.insert(message) != 1) {
            throw new BusinessException(ErrorCodeEnum.SYSTEM_ERROR, "医生消息保存失败");
        }
        // 事务提交后触发 C 端实时推送和站内通知，事件不传递消息正文。
        eventPublisher.publishEvent(ConsultationMessageCreatedEvent.of(message.getId(), consultId,
                message.getSenderType(), doctorId, record.getPatientId(), now));
        return toMessageVO(message);
    }

    /**
     * 结束在线问诊。
     *
     * @param consultId 问诊记录 ID
     * @return 问诊结束结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConsultEndVO endOnlineConsult(Long consultId) {
        ConsultRecord record = consultRecordMapper.lockOnlineConsult(consultId);
        if (record == null) {
            throw new BusinessException(ERR_CONSULT_STATUS_INVALID, "在线问诊不存在");
        }
        Long doctorId = requireAssignedDoctor(record);
        if (!OnlineConsultationConstant.STATUS_IN_PROGRESS.equals(record.getStatus())) {
            throw new BusinessException(ERR_CONSULT_STATUS_INVALID, "在线问诊状态不可结束");
        }
        OffsetDateTime endedAt = OffsetDateTime.now();
        if (consultRecordMapper.completeOnlineConsult(consultId, doctorId, endedAt) != 1) {
            throw new BusinessException(ERR_CONSULT_STATUS_INVALID, "在线问诊状态不可结束");
        }
        return ConsultEndVO.builder().consultId(consultId)
                .status(OnlineConsultationConstant.STATUS_COMPLETED)
                .endedAt(endedAt)
                .build();
    }

    /**
     * 游标查询在线问诊消息。
     *
     * @param consultId 问诊记录 ID
     * @param afterId 向后补拉游标
     * @param beforeId 向前加载游标
     * @param size 每页数量
     * @return 消息游标分页结果
     */
    @Override
    public OnlineConsultationMessagePageVO pageOnlineConsultationMessages(Long consultId, Long afterId,
                                                                           Long beforeId, int size) {
        getOnlineConsultInScope(consultId);
        if (afterId != null && beforeId != null) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAMETER, "afterId 与 beforeId 不能同时传递");
        }
        var query = Wrappers.<ConsultationMessage>lambdaQuery().eq(ConsultationMessage::getConsultId, consultId)
                .isNull(ConsultationMessage::getDeletedAt);
        if (afterId != null) {
            query.gt(ConsultationMessage::getId, afterId).orderByAsc(ConsultationMessage::getId);
            return buildOnlineMessagePage(consultationMessageMapper.selectList(query.last("LIMIT " + (size + 1))), size);
        }
        query.lt(beforeId != null, ConsultationMessage::getId, beforeId).orderByDesc(ConsultationMessage::getId);
        List<ConsultationMessage> records = consultationMessageMapper.selectList(query.last("LIMIT " + (size + 1)));
        boolean hasMore = records.size() > size;
        if (hasMore) {
            records = records.subList(0, size);
        }
        Collections.reverse(records);
        return OnlineConsultationMessagePageVO.builder().messages(records.stream().map(this::toMessageVO).toList())
                .hasMore(hasMore).build();
    }

    @Override
    public PageResult<QueueItemVO> pageQueue(Long deptId, String status, int page, int size) {
        DataScope scope = currentUserService.getCurrentDataScope();
        // 数据权限标识缺失时返回空数据
        if ((BRoleEnum.DEPT_HEAD.equalsCode(scope.role()) && scope.deptId() == null)
                || (BRoleEnum.DOCTOR.equalsCode(scope.role()) && scope.doctorId() == null)) {
            return PageResult.of(0, List.of(), page, size);
        }

        String queryStatus = StringUtils.hasText(status) ? status : STATUS_PENDING;
        List<Long> doctorIds = resolveDoctorIds(scope, deptId);

        if (doctorIds.isEmpty()) {
            return PageResult.of(0, List.of(), page, size);
        }

        // 自动补建缺失的 consult_record：C 端挂号成功后未创建问诊记录时，查询前幂等补齐
        ensureConsultRecordsExist(doctorIds);

        // 批量过期已过期的 PENDING 记录与 PAID 挂号订单，保持队列干净
        int expiredCount = consultRecordMapper.batchExpireOldPending(doctorIds);
        int expiredApptCount = appointmentMapper.batchExpireOldPaid(doctorIds, OffsetDateTime.now());
        if (expiredCount > 0 || expiredApptCount > 0) {
            log.info("过期处理：consult_record NO_SHOW {} 条, appointment EXPIRED {} 条", expiredCount, expiredApptCount);
        }

        // 对于 ADMIN/DEPT_HEAD，deptId 可传参过滤；DOCTOR 只看本人
        Long queryDeptId = (BRoleEnum.ADMIN.equalsCode(scope.role()) && deptId != null) ? deptId
                : (BRoleEnum.DEPT_HEAD.equalsCode(scope.role()) ? scope.deptId() : null);

        IPage<QueueRow> result = consultRecordMapper.selectQueuePage(
                new Page<>(page, size), queryStatus, doctorIds, queryDeptId);

        List<QueueItemVO> list = result.getRecords().stream()
                .map(this::toQueueItemVO)
                .toList();
        return PageResult.of(result.getTotal(), list, page, size);
    }

    @Override
    public PatientDetailVO getPatientDetail(Long consultId) {
        // 医院级只读校验：接诊历史跨医生可见，患者详情随之放开到本院边界（写操作仍走严格 getConsultInScope）
        ConsultRecord record = getConsultInHospitalScope(consultId);
        Patient patient = patientMapper.selectById(record.getPatientId());
        if (patient == null || patient.getDeletedAt() != null) {
            throw new BusinessException(ERR_PATIENT_NOT_FOUND, "患者不存在");
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addPatientAllergy(Long consultId, AllergyCreateRequest request) {
        // 复用问诊归属校验：存在 + 未软删 + 医生属本院 + 角色边界（医生本人/科室主任本室/管理员全院）
        ConsultRecord record = getConsultInScope(consultId);
        // 仅接诊中可补录：结束后患者档案进入只读态，禁止再修改过敏史（前端同步按状态隐藏入口）
        if (!STATUS_IN_PROGRESS.equals(record.getStatus())) {
            throw new BusinessException(ERR_CONSULT_NOT_IN_PROGRESS, "仅接诊中可补录过敏史");
        }
        Patient patient = patientMapper.selectById(record.getPatientId());
        if (patient == null || patient.getDeletedAt() != null) {
            throw new BusinessException(ERR_PATIENT_NOT_FOUND, "患者不存在");
        }

        PatientAllergy allergy = new PatientAllergy();
        allergy.setPatientId(patient.getId());
        allergy.setAllergen(request.getAllergen().trim());
        allergy.setReaction(request.getReaction());
        allergy.setSeverity(request.getSeverity().trim());
        patientAllergyMapper.insert(allergy);

        log.info("接诊台补录过敏史 allergyId={}, patientId={}, allergen={}, consultId={}",
                allergy.getId(), patient.getId(), allergy.getAllergen(), consultId);
        return allergy.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConsultStartVO startConsult(Long consultId) {
        ConsultRecord record = getConsultInScope(consultId);
        requireAppointmentConsult(record);
        if (!STATUS_PENDING.equals(record.getStatus())) {
            throw new BusinessException(ERR_CONSULT_STATUS_INVALID, "问诊记录状态不可接诊");
        }
        // 校验该医生当前无其他 IN_PROGRESS 接诊
        long inProgress = consultRecordMapper.countInProgressByDoctor(record.getDoctorId());
        if (inProgress > 0) {
            throw new BusinessException(ERR_DOCTOR_BUSY, "医生当前存在未结束的接诊记录");
        }

        // 校验当前时间是否在号源时段内
        SlotTimeInfo slotTime = consultRecordMapper.selectSlotTimeByConsultId(consultId);
        if (slotTime == null) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAMETER, "未找到号源时段信息，无法接诊");
        }
        LocalTime now = LocalTime.now();
        if (now.isBefore(slotTime.getStartTime()) || now.isAfter(slotTime.getEndTime())) {
            throw new BusinessException(ErrorCodeEnum.ORDER_CLOSED_OR_STATUS_INVALID,
                    "当前不在接诊时间内（" + slotTime.getStartTime() + "~" + slotTime.getEndTime() + "）");
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConsultEndVO endConsult(Long consultId) {
        ConsultRecord record = getConsultInScope(consultId);
        requireAppointmentConsult(record);
        if (!STATUS_IN_PROGRESS.equals(record.getStatus())) {
            throw new BusinessException(ERR_CONSULT_NOT_IN_PROGRESS, "问诊状态不是 IN_PROGRESS");
        }
        // 校验无未签名的 DRAFT 处方
        long draftPrescriptions = prescriptionMapper.selectCount(Wrappers.<Prescription>lambdaQuery()
                .eq(Prescription::getConsultId, consultId)
                .eq(Prescription::getStatus, PRESCRIPTION_DRAFT)
                .isNull(Prescription::getDeletedAt));
        if (draftPrescriptions > 0) {
            throw new BusinessException(ERR_HAS_DRAFT_PRESCRIPTION, "存在未签名的处方草稿，请先处理");
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NoteSaveVO saveNote(Long consultId, String doctorNote) {
        if (!StringUtils.hasText(doctorNote) || doctorNote.length() > MAX_NOTE_LENGTH) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAMETER, "病历内容不能为空且不超过10000字符");
        }
        ConsultRecord record = getConsultInScope(consultId);
        requireAppointmentConsult(record);

        record.setDoctorNote(doctorNote);
        record.setUpdatedAt(OffsetDateTime.now());
        consultRecordMapper.updateById(record);

        log.info("保存病历 consultId={}", consultId);
        return NoteSaveVO.builder()
                .consultId(record.getId())
                .updatedAt(record.getUpdatedAt())
                .build();
    }

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageVO sendMessage(Long consultationId, String content) {
        if (!StringUtils.hasText(content) || content.length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAMETER, "消息内容不能为空且不超过2000字符");
        }
        ConsultRecord record = getConsultInScope(consultationId);
        requireAppointmentConsult(record);
        if (!STATUS_IN_PROGRESS.equals(record.getStatus())) {
            throw new BusinessException(ERR_CONSULT_NOT_IN_PROGRESS, "问诊状态不是 IN_PROGRESS，禁止发送消息");
        }

        ConsultationMessage message = new ConsultationMessage();
        message.setConsultId(consultationId);
        message.setSenderType(SENDER_DOCTOR);
        message.setContent(content);
        consultationMessageMapper.insert(message);

        log.info("发送问诊消息 consultId={}, messageId={}", consultationId, message.getId());
        return MessageVO.builder()
                .messageId(message.getId())
                .senderType(SENDER_DOCTOR)
                .content(content)
                .createdAt(message.getCreatedAt())
                .build();
    }

    @Override
    public PageResult<ConsultHistoryVO> pageHistory(int page, int size) {
        DataScope scope = currentUserService.getCurrentDataScope();
        // 查询本医院全部医生（ADMIN/DEPT_HEAD/DOCTOR 均可见本医院接诊历史，便于跨医生协同查看）
        List<Long> doctorIds = doctorMapper.selectList(
                        Wrappers.<Doctor>lambdaQuery()
                                .eq(Doctor::getHospitalId, scope.hospitalId())
                                .isNull(Doctor::getDeletedAt))
                .stream()
                .map(Doctor::getId)
                .toList();
        if (doctorIds.isEmpty()) {
            return PageResult.of(0, List.of(), page, size);
        }

        Page<ConsultRecord> result = consultRecordMapper.selectPage(
                new Page<>(page, size),
                Wrappers.<ConsultRecord>lambdaQuery()
                        .in(ConsultRecord::getDoctorId, doctorIds)
                        .isNotNull(ConsultRecord::getAppointmentId)
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
        // 医院级只读校验：与 pageHistory 的全院历史列表可见范围保持一致（跨医生协同查看）
        ConsultRecord record = getConsultInHospitalScope(consultId);
        Doctor doctor = doctorMapper.selectById(record.getDoctorId());
        String doctorName = (doctor != null && doctor.getDeletedAt() == null) ? doctor.getName() : null;
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
                .doctorName(doctorName)
                .startedAt(record.getStartedAt())
                .endedAt(record.getEndedAt())
                .createdAt(record.getCreatedAt())
                .prescriptions(briefs)
                .build();
    }

    /**
     * 获取问诊记录并校验当前用户的数据权限。
     *
     * <p>校验链路：
     * <ol>
     *   <li>问诊记录存在且未软删</li>
     *   <li>问诊所属医生属于当前用户所在医院（跨医院隔离）</li>
     *   <li>当前角色为 {@link BRoleEnum#DOCTOR} 时，问诊所属医生必须为本人</li>
     *   <li>当前角色为 {@link BRoleEnum#DEPT_HEAD} 时，问诊所属医生必须属于本人管辖科室</li>
     *   <li>当前角色为 {@link BRoleEnum#ADMIN} 时，仅受医院边界约束</li>
     * </ol>
     *
     * @param consultId 问诊记录 ID
     * @return 问诊记录实体
     * @throws BusinessException 不存在或越权时抛出
     */
    private ConsultRecord getConsultInScope(Long consultId) {
        ConsultRecord record = consultRecordMapper.selectById(consultId);
        if (record == null || record.getDeletedAt() != null) {
            throw new BusinessException(ErrorCodeEnum.INVALID_USER_INPUT, "问诊记录不存在");
        }
        DataScope scope = currentUserService.getCurrentDataScope();
        Doctor doctor = doctorMapper.selectById(record.getDoctorId());
        if (doctor == null || doctor.getDeletedAt() != null
                || !doctor.getHospitalId().equals(scope.hospitalId())) {
            throw new BusinessException(ERR_NO_PERMISSION, "无权查看该患者");
        }
        if (BRoleEnum.DOCTOR.equalsCode(scope.role()) && !scope.doctorId().equals(record.getDoctorId())) {
            throw new BusinessException(ERR_NO_PERMISSION, "无权查看该患者");
        }
        if (BRoleEnum.DEPT_HEAD.equalsCode(scope.role())) {
            Doctor consultDoctor = doctorMapper.selectById(record.getDoctorId());
            if (consultDoctor == null || !consultDoctor.getDeptId().equals(scope.deptId())) {
                throw new BusinessException(ERR_NO_PERMISSION, "无权查看该患者");
            }
        }
        return record;
    }

    /**
     * 获取问诊记录并仅校验当前用户所在医院边界（不限制医生 / 科室）。
     *
     * <p>用于接诊历史 / 患者详情的只读场景：历史列表本就按本院全部医生展示（跨医生协同查看），
     * 详情只读校验到医院一级与 {@link #pageHistory} 的可见范围保持一致。
     * 写操作仍走 {@link #getConsultInScope} 的严格角色校验。
     *
     * @param consultId 问诊记录 ID
     * @return 问诊记录实体
     * @throws BusinessException 记录不存在 / 已软删 / 问诊医生不属于当前医院时抛出
     */
    private ConsultRecord getConsultInHospitalScope(Long consultId) {
        ConsultRecord record = consultRecordMapper.selectById(consultId);
        if (record == null || record.getDeletedAt() != null) {
            throw new BusinessException(ErrorCodeEnum.INVALID_USER_INPUT, "问诊记录不存在");
        }
        DataScope scope = currentUserService.getCurrentDataScope();
        Doctor doctor = doctorMapper.selectById(record.getDoctorId());
        if (doctor == null || doctor.getDeletedAt() != null
                || !doctor.getHospitalId().equals(scope.hospitalId())) {
            throw new BusinessException(ERR_NO_PERMISSION, "无权查看该患者");
        }
        return record;
    }

    /**
     * 获取当前用户可访问的无挂号在线问诊。
     *
     * @param consultId 问诊记录 ID
     * @return 在线问诊记录
     * @throws BusinessException 记录属于挂号接诊时抛出
     */
    private ConsultRecord getOnlineConsultInScope(Long consultId) {
        ConsultRecord record = getConsultInScope(consultId);
        if (record.getAppointmentId() != null) {
            throw new BusinessException(ERR_CONSULT_STATUS_INVALID, "该记录不是在线问诊");
        }
        return record;
    }

    /**
     * 将消息实体转换为 B 端响应对象。
     *
     * @param message 问诊消息实体
     * @return 消息响应对象
     */
    private MessageVO toMessageVO(ConsultationMessage message) {
        return MessageVO.builder().messageId(message.getId()).senderType(message.getSenderType())
                .content(message.getContent()).createdAt(message.getCreatedAt()).build();
    }

    /**
     * 从多取一条的正序消息计算游标分页状态。
     *
     * @param records 消息实体列表
     * @param size 请求页大小
     * @return 消息游标分页结果
     */
    private OnlineConsultationMessagePageVO buildOnlineMessagePage(List<ConsultationMessage> records, int size) {
        boolean hasMore = records.size() > size;
        if (hasMore) {
            records = records.subList(0, size);
        }
        return OnlineConsultationMessagePageVO.builder().messages(records.stream().map(this::toMessageVO).toList())
                .hasMore(hasMore).build();
    }

    /**
     * 校验写操作由问诊绑定医生本人执行。
     *
     * @param record 在线问诊记录
     * @return 当前医生 ID
     * @throws BusinessException 当前账号不是绑定医生时抛出
     */
    private Long requireAssignedDoctor(ConsultRecord record) {
        DataScope scope = currentUserService.getCurrentDataScope();
        if (scope.doctorId() == null || !scope.doctorId().equals(record.getDoctorId())) {
            throw new BusinessException(ERR_NO_PERMISSION, "仅问诊绑定医生可以回复");
        }
        return scope.doctorId();
    }

    /**
     * 阻止在线问诊绕过独立状态机调用挂号接诊写接口。
     *
     * @param record 问诊记录
     * @throws BusinessException 无挂号订单时抛出
     */
    private void requireAppointmentConsult(ConsultRecord record) {
        if (record.getAppointmentId() == null) {
            throw new BusinessException(ERR_CONSULT_STATUS_INVALID, "在线问诊请使用在线问诊操作接口");
        }
    }

    /**
     * 将在线问诊记录转换为列表项。
     *
     * @param record 在线问诊记录
     * @return 在线问诊列表项
     */
    private OnlineConsultationItemVO toOnlineConsultationItem(ConsultRecord record) {
        Patient patient = patientMapper.selectById(record.getPatientId());
        return OnlineConsultationItemVO.builder()
                .consultId(record.getId())
                .patientId(record.getPatientId())
                .patientName(patient == null ? null : patient.getName())
                .patientGender(patient == null ? null : patient.getGender())
                .status(record.getStatus())
                .aiSummary(parseAiSummary(record.getAiSummary()))
                .chiefComplaint(record.getChiefComplaint())
                .submittedAt(record.getPreConsultationSubmittedAt())
                .doctorRepliedAt(record.getDoctorRepliedAt())
                .canStart(OnlineConsultationConstant.STATUS_PENDING.equals(record.getStatus()))
                .canReply(OnlineConsultationConstant.STATUS_IN_PROGRESS.equals(record.getStatus()))
                .build();
    }

    /**
     * 自动补建缺失的 consult_record（幂等）。
     *
     * <p>C 端挂号成功后若未创建 consult_record，B 端查询队列前自动补齐，对两端无侵入。
     */
    private void ensureConsultRecordsExist(List<Long> doctorIds) {
        int inserted = consultRecordMapper.batchCreateIfMissing(doctorIds);
        if (inserted > 0) {
            log.info("自动补建 consult_record {} 条", inserted);
        }
    }

    /**
     * 根据数据权限解析可查询的医生 ID 列表。
     *
     * <p>ADMIN 返回同医院全部医生（可按 deptId 过滤）；DEPT_HEAD 返回本管辖科室医生；
     * DOCTOR 仅返回本人；其他角色返回空集合。
     *
     * @param scope 当前用户数据权限
     * @param deptId 可选科室过滤（仅 ADMIN 生效）
     * @return 医生 ID 列表（空列表表示无可见数据）
     */
    private List<Long> resolveDoctorIds(DataScope scope, Long deptId) {
        // 用枚举 equalsCode 分支（null-safe），替代 switch 字面量 case：角色值只保留 BRoleEnum 单一来源
        if (BRoleEnum.ADMIN.equalsCode(scope.role())) {
            return doctorMapper.selectList(Wrappers.<Doctor>lambdaQuery()
                            .eq(Doctor::getHospitalId, scope.hospitalId())
                            .eq(deptId != null, Doctor::getDeptId, deptId)
                            .isNull(Doctor::getDeletedAt))
                    .stream()
                    .map(Doctor::getId)
                    .toList();
        }
        if (BRoleEnum.DEPT_HEAD.equalsCode(scope.role())) {
            return doctorMapper.selectList(Wrappers.<Doctor>lambdaQuery()
                            .eq(Doctor::getDeptId, scope.deptId())
                            .isNull(Doctor::getDeletedAt))
                    .stream()
                    .map(Doctor::getId)
                    .toList();
        }
        if (BRoleEnum.DOCTOR.equalsCode(scope.role())) {
            return scope.doctorId() != null
                    ? List.of(scope.doctorId())
                    : List.of();
        }
        return List.of();
    }

    /**
     * 将 QueueRow 转换为 QueueItemVO，并按出生日期推算年龄。
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
     * 将 ConsultRecord 转换为 ConsultHistoryVO，附挂患者姓名 / 性别 / 出生日期与病历摘要。
     */
    private ConsultHistoryVO toConsultHistoryVO(ConsultRecord r) {
        Patient patient = r.getPatientId() != null ? patientMapper.selectById(r.getPatientId()) : null;
        int age = patient != null && patient.getDateOfBirth() != null
                ? Period.between(patient.getDateOfBirth(), LocalDate.now()).getYears()
                : 0;
        String noteSummary = r.getDoctorNote() != null
                ? r.getDoctorNote().substring(0, Math.min(r.getDoctorNote().length(), NOTE_SUMMARY_MAX_LENGTH))
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
     * 解析 ai_summary jsonb 字段为 Map；解析失败时返回空 Map 并记 warn 日志（不影响主流程）。
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
     * 加载患者近期处方（按创建时间倒序，截取 {@value RECENT_PRESCRIPTION_LIMIT} 条）。
     */
    private List<PatientDetailVO.RecentPrescriptionInfo> loadRecentPrescriptions(Long patientId) {
        List<Prescription> prescriptions = prescriptionMapper.selectList(
                Wrappers.<Prescription>lambdaQuery()
                        .eq(Prescription::getPatientId, patientId)
                        .isNull(Prescription::getDeletedAt)
                        .orderByDesc(Prescription::getCreatedAt)
                        .last("LIMIT " + RECENT_PRESCRIPTION_LIMIT));
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
     *
     * @param patientId  患者 ID
     * @param hospitalId 当前用户所在医院 ID
     * @return 历史就诊记录（最多 {@value HISTORY_RECORD_LIMIT} 条）
     */
    private List<PatientDetailVO.HistoryRecordInfo> loadHistoryRecords(Long patientId, Long hospitalId) {
        List<ConsultRecord> records = consultRecordMapper.selectList(
                Wrappers.<ConsultRecord>lambdaQuery()
                        .eq(ConsultRecord::getPatientId, patientId)
                        .isNull(ConsultRecord::getDeletedAt)
                        .orderByDesc(ConsultRecord::getCreatedAt)
                        .last("LIMIT " + HISTORY_RECORD_LIMIT));
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
                                ? r.getDoctorNote().substring(0, Math.min(r.getDoctorNote().length(), HISTORY_NOTE_SUMMARY_MAX_LENGTH))
                                : null)
                        .status(r.getStatus())
                        .build())
                .toList();
    }

    /**
     * 手机号脱敏：保留前 {@value PHONE_MASK_PREFIX} 位 + 后 {@value PHONE_MASK_SUFFIX} 位，中间 4 个星号。
     * 长度不足时原样返回，避免越界。
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < PHONE_MASK_MIN_LENGTH) {
            return phone;
        }
        return phone.substring(0, PHONE_MASK_PREFIX) + "****" + phone.substring(phone.length() - PHONE_MASK_SUFFIX);
    }
}

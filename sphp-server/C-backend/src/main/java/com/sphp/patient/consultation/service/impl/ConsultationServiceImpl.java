package com.sphp.patient.consultation.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.common.constant.ConsultationConstant;
import com.sphp.patient.common.enums.ConsultationMessageSenderTypeEnum;
import com.sphp.patient.common.enums.ConsultationPrescriptionStatusEnum;
import com.sphp.patient.common.enums.ConsultationStatusEnum;
import com.sphp.patient.common.enums.RegisteringAppointmentStatusEnum;
import com.sphp.patient.consultation.dto.ConsultationMessageSendRequest;
import com.sphp.patient.consultation.dto.PreConsultationSaveRequest;
import com.sphp.patient.consultation.entity.ConsultationMessage;
import com.sphp.patient.consultation.entity.ConsultationRecord;
import com.sphp.patient.consultation.event.ConsultationMessageSentEvent;
import com.sphp.patient.consultation.mapper.ConsultationAppointmentRecord;
import com.sphp.patient.consultation.mapper.ConsultationDataMapper;
import com.sphp.patient.consultation.mapper.ConsultationListRecord;
import com.sphp.patient.consultation.mapper.ConsultationDetailRecord;
import com.sphp.patient.consultation.mapper.ConsultationMessageRecord;
import com.sphp.patient.consultation.mapper.ConsultationMessageMapper;
import com.sphp.patient.consultation.mapper.ConsultationPrescriptionRecord;
import com.sphp.patient.consultation.mapper.ConsultationPrescriptionDetailRecord;
import com.sphp.patient.consultation.mapper.ConsultationPrescriptionItemRecord;
import com.sphp.patient.consultation.mapper.ConsultationPrescriptionResourceRecord;
import com.sphp.patient.consultation.service.ConsultationService;
import com.sphp.patient.consultation.vo.PreConsultationSaveVO;
import com.sphp.patient.consultation.vo.ConsultationPageVO;
import com.sphp.patient.consultation.vo.ConsultationAttachmentVO;
import com.sphp.patient.consultation.vo.ConsultationDetailVO;
import com.sphp.patient.consultation.vo.ConsultationMessageSendVO;
import com.sphp.patient.consultation.vo.ConsultationPrescriptionPageVO;
import com.sphp.patient.consultation.vo.ConsultationPrescriptionDetailVO;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * C端问诊与处方查询服务实现。
 */
@Service
@RequiredArgsConstructor
public class ConsultationServiceImpl implements ConsultationService {

    private final ConsultationDataMapper consultationDataMapper;
    private final ConsultationMessageMapper consultationMessageMapper;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 创建、保存或提交当前账号可访问就诊人的预问诊。
     *
     * @param request 预问诊请求参数
     * @return 保存后的问诊信息
     * @throws CAuthException 就诊人、挂号订单或问诊状态不满足要求时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PreConsultationSaveVO savePreConsultation(PreConsultationSaveRequest request) {
        Long userId = CUserContext.getRequired().userId();
        Long patientId = resolveAccessiblePatient(userId, request.getPatientId());
        // 先锁定挂号订单，避免同一订单的草稿创建和提交出现并发覆盖。
        ConsultationAppointmentRecord appointment = consultationDataMapper.lockConsultationAppointment(request.getAppointmentId());
        if (appointment == null) {
            throw notFound("挂号订单不存在");
        }
        if (!patientId.equals(appointment.patientId())) {
            throw forbidden("无权使用该挂号订单创建预问诊");
        }
        if (!RegisteringAppointmentStatusEnum.PAID.name().equals(appointment.status())) {
            throw statusConflict("挂号订单未支付或当前状态不允许创建预问诊");
        }

        OffsetDateTime now = OffsetDateTime.now();
        ConsultationRecord existing = consultationDataMapper.selectConsultationByAppointmentForUpdate(appointment.id());
        boolean submit = Boolean.TRUE.equals(request.getSubmit());
        String attachmentsJson = serializeAttachments(request);
        if (existing == null) {
            ConsultationRecord record = buildConsultationRecord(request, appointment, attachmentsJson, now, submit);
            // 使用显式 JSONB 写入，确保 PostgreSQL 不把 JSON 字符串当作普通字符列处理。
            if (consultationDataMapper.insertConsultationRecord(record) != 1) {
                throw systemError("预问诊保存失败");
            }
            return buildPreConsultationSaveResult(record);
        }
        if (!ConsultationStatusEnum.DRAFT.name().equals(existing.getStatus())) {
            throw statusConflict("当前问诊状态不允许保存或提交预问诊");
        }

        existing.setStatus(submit ? ConsultationStatusEnum.PENDING.name() : ConsultationStatusEnum.DRAFT.name());
        existing.setChiefComplaint(request.getChiefComplaint());
        existing.setHistoryOfPresentIllness(request.getHistoryOfPresentIllness());
        existing.setAttachmentsJson(attachmentsJson);
        existing.setUpdatedAt(now);
        if (submit) {
            existing.setPreConsultationSubmittedAt(now);
        }
        // 使用 DRAFT 状态条件更新，防止医生接诊后的状态被旧草稿请求回写。
        if (consultationDataMapper.updateConsultationDraft(existing, ConsultationStatusEnum.DRAFT.name()) != 1) {
            throw statusConflict("当前问诊状态已变化，请刷新后重试");
        }
        return buildPreConsultationSaveResult(existing);
    }

    /**
     * 分页查询当前账号指定就诊人的问诊记录。
     *
     * @param patientId 可选就诊人 ID，未传时查询本人
     * @param status 可选问诊状态
     * @param pageNo 可选页码
     * @param pageSize 可选页大小
     * @return 问诊记录分页响应
     * @throws CAuthException 患者归属、状态或分页参数不满足要求时抛出
     */
    @Override
    public ConsultationPageVO listConsultations(Long patientId, String status, Integer pageNo, Integer pageSize) {
        Long targetPatientId = resolveAccessiblePatient(CUserContext.getRequired().userId(), patientId);
        validateConsultationStatus(status);
        int resolvedPageNo = pageNo == null ? ConsultationConstant.DEFAULT_PAGE_NO : pageNo;
        int resolvedPageSize = pageSize == null ? ConsultationConstant.DEFAULT_PAGE_SIZE : pageSize;
        if (resolvedPageSize > ConsultationConstant.MAX_PAGE_SIZE) {
            throw parameterOutOfRange("pageSize 不能超过" + ConsultationConstant.MAX_PAGE_SIZE);
        }
        long offset = (long) (resolvedPageNo - 1) * resolvedPageSize;
        // 列表始终使用已通过归属校验的患者 ID，避免查询其他账号的问诊记录。
        List<ConsultationPageVO.Item> records = consultationDataMapper
                .selectConsultationList(targetPatientId, status, resolvedPageSize, offset)
                .stream()
                .map(this::toConsultationListItem)
                .toList();
        return ConsultationPageVO.builder()
                .pageNo(resolvedPageNo)
                .pageSize(resolvedPageSize)
                .total(consultationDataMapper.countConsultationList(targetPatientId, status))
                .records(records)
                .build();
    }

    /**
     * 查询当前账号可访问的问诊详情与文字消息。
     *
     * @param consultationId 问诊记录 ID
     * @return 问诊详情与文字消息
     * @throws CAuthException 问诊不存在或当前账号无权访问时抛出
     */
    @Override
    public ConsultationDetailVO getConsultationDetail(Long consultationId) {
        ConsultationDetailRecord record = consultationDataMapper.selectConsultationDetail(consultationId);
        if (record == null) {
            throw notFound("问诊记录不存在");
        }
        // 详情先按资源反查患者，再判断当前账号是否持有有效患者关系。
        resolveAccessiblePatient(CUserContext.getRequired().userId(), record.patientId());
        List<ConsultationDetailVO.Message> messages = consultationDataMapper.selectConsultationMessages(consultationId)
                .stream()
                .map(this::toConsultationMessage)
                .toList();
        return ConsultationDetailVO.builder()
                .id(record.id())
                .status(record.status())
                .doctor(ConsultationDetailVO.Doctor.builder()
                        .id(record.doctorId())
                        .name(record.doctorName())
                        .title(record.doctorTitle())
                        .build())
                .preConsultation(ConsultationDetailVO.PreConsultation.builder()
                        .chiefComplaint(record.chiefComplaint())
                        .historyOfPresentIllness(record.historyOfPresentIllness())
                        .attachments(deserializeAttachments(record.attachmentsJson()))
                        .savedAt(record.savedAt())
                        .submittedAt(record.submittedAt())
                        .build())
                .messages(messages)
                .prescriptionIds(consultationDataMapper.selectConsultationApprovedPrescriptionIds(consultationId))
                .build();
    }

    /**
     * 向进行中的问诊发送患者文字消息。
     *
     * @param consultationId 问诊记录 ID
     * @param request 文字消息请求参数
     * @return 已发送消息信息
     * @throws CAuthException 问诊不存在、患者越权或问诊状态不允许发送时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConsultationMessageSendVO sendConsultationMessage(Long consultationId,
                                                              ConsultationMessageSendRequest request) {
        Long userId = CUserContext.getRequired().userId();
        // 锁定问诊记录，防止医生结束问诊与患者发消息同时通过状态校验。
        ConsultationDetailRecord consultation = consultationDataMapper.lockConsultationDetail(consultationId);
        if (consultation == null) {
            throw notFound("问诊记录不存在");
        }
        resolveAccessiblePatient(userId, consultation.patientId());
        if (!ConsultationStatusEnum.IN_PROGRESS.name().equals(consultation.status())) {
            throw messageStatusConflict(consultation.status());
        }

        OffsetDateTime now = OffsetDateTime.now();
        ConsultationMessage message = new ConsultationMessage();
        message.setConsultationId(consultationId);
        message.setSenderType(ConsultationMessageSenderTypeEnum.PATIENT.name());
        message.setContent(request.getContent());
        message.setCreatedAt(now);
        if (consultationMessageMapper.insert(message) != 1) {
            throw systemError("问诊消息发送失败");
        }
        // 事务提交后再将不含文本内容的业务事件转发给医生端订阅方。
        eventPublisher.publishEvent(ConsultationMessageSentEvent.of(
                consultationId, consultation.doctorId(), consultation.patientId(), userId));
        return ConsultationMessageSendVO.builder()
                .messageId(message.getId())
                .consultationId(consultationId)
                .senderType(message.getSenderType())
                .content(message.getContent())
                .createdAt(now)
                .build();
    }

    /**
     * 分页查询当前账号指定就诊人的已批准处方。
     *
     * @param patientId 可选就诊人 ID，未传时查询本人
     * @param pageNo 可选页码
     * @param pageSize 可选页大小
     * @return 已批准处方分页响应
     * @throws CAuthException 患者归属或分页参数不满足要求时抛出
     */
    @Override
    public ConsultationPrescriptionPageVO listPrescriptions(Long patientId, Integer pageNo, Integer pageSize) {
        Long targetPatientId = resolveAccessiblePatient(CUserContext.getRequired().userId(), patientId);
        int resolvedPageNo = pageNo == null ? ConsultationConstant.DEFAULT_PAGE_NO : pageNo;
        int resolvedPageSize = pageSize == null ? ConsultationConstant.DEFAULT_PAGE_SIZE : pageSize;
        if (resolvedPageSize > ConsultationConstant.MAX_PAGE_SIZE) {
            throw parameterOutOfRange("pageSize 不能超过" + ConsultationConstant.MAX_PAGE_SIZE);
        }
        long offset = (long) (resolvedPageNo - 1) * resolvedPageSize;
        // Mapper 固定过滤 APPROVED，草稿和审核中的处方不会进入患者接口。
        List<ConsultationPrescriptionPageVO.Item> records = consultationDataMapper
                .selectApprovedPrescriptionList(targetPatientId, resolvedPageSize, offset)
                .stream()
                .map(this::toPrescriptionListItem)
                .toList();
        return ConsultationPrescriptionPageVO.builder()
                .pageNo(resolvedPageNo)
                .pageSize(resolvedPageSize)
                .total(consultationDataMapper.countApprovedPrescriptionList(targetPatientId))
                .records(records)
                .build();
    }

    /**
     * 查询当前账号可访问的已批准处方详情与药品明细。
     *
     * @param prescriptionId 处方 ID
     * @return 已批准处方详情
     * @throws CAuthException 处方不存在、未批准或当前账号无权访问时抛出
     */
    @Override
    public ConsultationPrescriptionDetailVO getPrescriptionDetail(Long prescriptionId) {
        ConsultationPrescriptionResourceRecord resource = consultationDataMapper
                .selectConsultationPrescriptionResource(prescriptionId);
        if (resource == null) {
            throw notFound("处方不存在");
        }
        // 先按处方资源反查患者，避免仅凭处方 ID 读取其他账号信息。
        resolveAccessiblePatient(CUserContext.getRequired().userId(), resource.patientId());
        if (!ConsultationPrescriptionStatusEnum.APPROVED.name().equals(resource.status())) {
            // 未批准处方对患者端不可见，统一作为不存在处理，避免泄漏审核状态。
            throw notFound("处方不存在");
        }
        ConsultationPrescriptionDetailRecord detail = consultationDataMapper.selectApprovedPrescriptionDetail(prescriptionId);
        if (detail == null) {
            throw notFound("处方不存在");
        }
        List<ConsultationPrescriptionDetailVO.Item> items = consultationDataMapper
                .selectConsultationPrescriptionItems(prescriptionId)
                .stream()
                .map(this::toPrescriptionDetailItem)
                .toList();
        return ConsultationPrescriptionDetailVO.builder()
                .id(detail.id())
                .status(ConsultationPrescriptionStatusEnum.APPROVED.name())
                .doctorName(detail.doctorName())
                .doctor(ConsultationPrescriptionDetailVO.Doctor.builder()
                        .id(detail.doctorId())
                        .name(detail.doctorName())
                        .title(detail.doctorTitle())
                        .build())
                .items(items)
                .build();
    }

    /**
     * 解析当前账号可访问的就诊人，未传时固定使用本人。
     *
     * @param userId 当前 C端用户 ID
     * @param requestedPatientId 可选就诊人 ID
     * @return 已通过归属校验的就诊人 ID
     * @throws CAuthException 就诊人不存在、停用或无权访问时抛出
     */
    private Long resolveAccessiblePatient(Long userId, Long requestedPatientId) {
        Long patientId = requestedPatientId == null
                ? consultationDataMapper.selectConsultationSelfPatientId(userId)
                : requestedPatientId;
        if (patientId == null || !consultationDataMapper.existsConsultationActivePatient(patientId)) {
            throw notFound("就诊人不存在或已停用");
        }
        if (!consultationDataMapper.hasConsultationActivePatientRelation(userId, patientId)) {
            throw forbidden("无权访问该就诊人");
        }
        return patientId;
    }

    /**
     * 校验问诊状态筛选值。
     *
     * @param status 可选问诊状态
     * @throws CAuthException 状态不在允许范围时抛出
     */
    private void validateConsultationStatus(String status) {
        if (status == null || status.isBlank()) {
            return;
        }
        boolean allowed = java.util.Arrays.stream(ConsultationStatusEnum.values())
                .anyMatch(item -> item.name().equals(status));
        if (!allowed) {
            throw parameterOutOfRange("问诊状态不在允许范围内");
        }
    }

    /**
     * 将问诊列表数据库投影转换为响应项。
     *
     * @param record 问诊列表投影
     * @return 问诊列表响应项
     */
    private ConsultationPageVO.Item toConsultationListItem(ConsultationListRecord record) {
        return ConsultationPageVO.Item.builder()
                .id(record.id())
                .appointmentId(record.appointmentId())
                .doctorName(record.doctorName())
                .status(record.status())
                .updatedAt(record.updatedAt())
                .build();
    }

    /**
     * 将消息查询投影转换为响应项。
     *
     * @param record 消息查询投影
     * @return 消息响应项
     */
    private ConsultationDetailVO.Message toConsultationMessage(ConsultationMessageRecord record) {
        return ConsultationDetailVO.Message.builder()
                .id(record.id())
                .senderType(record.senderType())
                .content(record.content())
                .createdAt(record.createdAt())
                .build();
    }

    /**
     * 将已批准处方查询投影转换为列表展示项。
     *
     * @param record 处方列表投影
     * @return 处方列表响应项
     */
    private ConsultationPrescriptionPageVO.Item toPrescriptionListItem(ConsultationPrescriptionRecord record) {
        return ConsultationPrescriptionPageVO.Item.builder()
                .id(record.id())
                .consultationId(record.consultationId())
                .doctorName(record.doctorName())
                .status(ConsultationPrescriptionStatusEnum.APPROVED.name())
                .issuedAt(record.issuedAt())
                .build();
    }

    /**
     * 将处方药品查询投影转换为详情响应项。
     *
     * @param record 处方药品明细投影
     * @return 处方药品详情项
     */
    private ConsultationPrescriptionDetailVO.Item toPrescriptionDetailItem(ConsultationPrescriptionItemRecord record) {
        return ConsultationPrescriptionDetailVO.Item.builder()
                .drugId(record.drugId())
                .drugName(record.drugName())
                .specification(record.specification())
                .dosage(record.dosage())
                .frequency(record.frequency())
                .usage(record.usage())
                .durationDays(record.durationDays())
                .build();
    }

    /**
     * 根据当前问诊状态创建消息发送冲突异常。
     *
     * @param status 当前问诊状态
     * @return HTTP 409 业务异常
     */
    private CAuthException messageStatusConflict(String status) {
        if (ConsultationStatusEnum.PENDING.name().equals(status)) {
            return new CAuthException(ErrorCodeEnum.ILLEGAL_INPUT_CONTENT, HttpStatus.CONFLICT,
                    "医生尚未开始问诊，暂不能发送消息");
        }
        return statusConflict("当前问诊状态不允许发送消息");
    }

    /**
     * 将数据库 JSONB 附件数组转换为前端展示项。
     *
     * @param attachmentsJson 附件 JSON 数组文本
     * @return 附件展示项列表
     * @throws CAuthException 存量附件数据异常时抛出
     */
    private List<ConsultationAttachmentVO> deserializeAttachments(String attachmentsJson) {
        if (attachmentsJson == null || attachmentsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(attachmentsJson, new TypeReference<List<ConsultationAttachmentVO>>() { });
        } catch (JsonProcessingException exception) {
            throw systemError("预问诊附件读取失败");
        }
    }

    /**
     * 组装待保存的预问诊记录。
     *
     * @param request 预问诊请求参数
     * @param appointment 已锁定挂号订单
     * @param attachmentsJson 附件 JSON 数组
     * @param now 当前时间
     * @param submit 是否提交为待接诊
     * @return 新问诊记录实体
     */
    private ConsultationRecord buildConsultationRecord(PreConsultationSaveRequest request,
                                                        ConsultationAppointmentRecord appointment,
                                                        String attachmentsJson, OffsetDateTime now,
                                                        boolean submit) {
        ConsultationRecord record = new ConsultationRecord();
        record.setAppointmentId(appointment.id());
        record.setDoctorId(appointment.doctorId());
        record.setPatientId(appointment.patientId());
        record.setStatus(submit ? ConsultationStatusEnum.PENDING.name() : ConsultationStatusEnum.DRAFT.name());
        record.setChiefComplaint(request.getChiefComplaint());
        record.setHistoryOfPresentIllness(request.getHistoryOfPresentIllness());
        record.setAttachmentsJson(attachmentsJson);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setPreConsultationSubmittedAt(submit ? now : null);
        return record;
    }

    /**
     * 将附件请求数组序列化为数据库 JSONB 文本。
     *
     * @param request 预问诊请求参数
     * @return JSON 数组文本
     * @throws CAuthException 附件序列化失败时抛出
     */
    private String serializeAttachments(PreConsultationSaveRequest request) {
        try {
            return objectMapper.writeValueAsString(request.getAttachments() == null ? List.of() : request.getAttachments());
        } catch (JsonProcessingException exception) {
            throw systemError("预问诊附件处理失败");
        }
    }

    /**
     * 将问诊记录转换为预问诊保存响应。
     *
     * @param record 已保存问诊记录
     * @return 预问诊保存响应
     */
    private PreConsultationSaveVO buildPreConsultationSaveResult(ConsultationRecord record) {
        return PreConsultationSaveVO.builder()
                .consultationId(record.getId())
                .status(record.getStatus())
                .savedAt(record.getUpdatedAt())
                .submittedAt(record.getPreConsultationSubmittedAt())
                .build();
    }

    /**
     * 创建资源不存在异常。
     *
     * @param message 面向客户端的提示
     * @return HTTP 404 业务异常
     */
    private CAuthException notFound(String message) {
        return new CAuthException(ErrorCodeEnum.INVALID_USER_INPUT, HttpStatus.NOT_FOUND, message);
    }

    /**
     * 创建患者归属越权异常。
     *
     * @param message 面向客户端的提示
     * @return HTTP 403 业务异常
     */
    private CAuthException forbidden(String message) {
        return new CAuthException(ErrorCodeEnum.UNAUTHORIZED, HttpStatus.FORBIDDEN, message);
    }

    /**
     * 创建状态冲突异常。
     *
     * @param message 面向客户端的提示
     * @return HTTP 409 业务异常
     */
    private CAuthException statusConflict(String message) {
        return new CAuthException(ErrorCodeEnum.ORDER_CLOSED_OR_STATUS_INVALID, HttpStatus.CONFLICT, message);
    }

    /**
     * 创建参数超出允许范围异常。
     *
     * @param message 面向客户端的提示
     * @return HTTP 400 业务异常
     */
    private CAuthException parameterOutOfRange(String message) {
        return new CAuthException(ErrorCodeEnum.PARAMETER_OUT_OF_RANGE, HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 创建系统异常。
     *
     * @param message 面向客户端的提示
     * @return HTTP 500 业务异常
     */
    private CAuthException systemError(String message) {
        return new CAuthException(ErrorCodeEnum.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}

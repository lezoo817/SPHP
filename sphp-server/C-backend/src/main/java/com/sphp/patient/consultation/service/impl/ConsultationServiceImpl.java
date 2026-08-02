package com.sphp.patient.consultation.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.common.enums.ConsultationStatusEnum;
import com.sphp.patient.common.enums.RegisteringAppointmentStatusEnum;
import com.sphp.patient.consultation.dto.PreConsultationSaveRequest;
import com.sphp.patient.consultation.entity.ConsultationRecord;
import com.sphp.patient.consultation.mapper.ConsultationAppointmentRecord;
import com.sphp.patient.consultation.mapper.ConsultationDataMapper;
import com.sphp.patient.consultation.service.ConsultationService;
import com.sphp.patient.consultation.vo.PreConsultationSaveVO;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
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
    private final ObjectMapper objectMapper;

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
     * 创建系统异常。
     *
     * @param message 面向客户端的提示
     * @return HTTP 500 业务异常
     */
    private CAuthException systemError(String message) {
        return new CAuthException(ErrorCodeEnum.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}

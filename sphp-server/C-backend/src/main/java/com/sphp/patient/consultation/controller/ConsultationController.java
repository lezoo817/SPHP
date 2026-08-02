package com.sphp.patient.consultation.controller;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.consultation.dto.PreConsultationSaveRequest;
import com.sphp.patient.consultation.dto.ConsultationMessageSendRequest;
import com.sphp.patient.consultation.service.ConsultationService;
import com.sphp.patient.consultation.vo.PreConsultationSaveVO;
import com.sphp.patient.consultation.vo.ConsultationPageVO;
import com.sphp.patient.consultation.vo.ConsultationDetailVO;
import com.sphp.patient.consultation.vo.ConsultationMessageSendVO;
import com.sphp.patient.support.idempotency.CIdempotencyService;
import com.sphp.patient.support.idempotency.IdempotencyPayload;
import com.sphp.shared.common.constant.HeaderConstant;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C端在线问诊与处方查询接口。
 */
@RestController
@Validated
@RequestMapping("/c/v1")
@Tag(name = "C端在线问诊", description = "预问诊、文字消息和处方查询")
@RequiredArgsConstructor
public class ConsultationController {

    private final ConsultationService consultationService;
    private final CIdempotencyService idempotencyService;

    /**
     * 创建、保存或提交预问诊。
     *
     * @param idempotencyKey 客户端幂等键
     * @param request 预问诊请求参数
     * @return 保存后的问诊信息
     */
    @PostMapping("/consultations/pre-consultations")
    @Operation(summary = "创建或保存预问诊")
    public Result<PreConsultationSaveVO> savePreConsultation(
            @RequestHeader(HeaderConstant.IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey,
            @Valid @RequestBody PreConsultationSaveRequest request) {
        Long userId = CUserContext.getRequired().userId();
        String message = Boolean.TRUE.equals(request.getSubmit()) ? "预问诊已提交" : "预问诊草稿已保存";
        // 以用户、路由和请求摘要隔离幂等结果，重试不重复写入问诊记录。
        IdempotencyPayload<PreConsultationSaveVO> payload = idempotencyService.execute(
                userId,
                "/c/v1/consultations/pre-consultations",
                idempotencyKey,
                request,
                PreConsultationSaveVO.class,
                () -> new IdempotencyPayload<>(message, consultationService.savePreConsultation(request)));
        return Result.success(payload.message(), payload.data());
    }

    /**
     * 分页查询当前账号可访问就诊人的问诊记录。
     *
     * @param patientId 可选就诊人 ID，未传时查询本人
     * @param status 可选问诊状态
     * @param pageNo 可选页码
     * @param pageSize 可选页大小
     * @return 问诊记录分页响应
     */
    @GetMapping("/consultations")
    @Operation(summary = "查询问诊记录列表")
    public Result<ConsultationPageVO> listConsultations(
            @RequestParam(required = false) @jakarta.validation.constraints.Positive(message = "patientId 必须为正数") Long patientId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @jakarta.validation.constraints.Positive(message = "pageNo 必须为正数") Integer pageNo,
            @RequestParam(required = false) @jakarta.validation.constraints.Positive(message = "pageSize 必须为正数") Integer pageSize) {
        return Result.success("查询成功", consultationService.listConsultations(patientId, status, pageNo, pageSize));
    }

    /**
     * 查询当前账号可访问的问诊详情和全部文字消息。
     *
     * @param consultationId 问诊记录 ID
     * @return 问诊详情响应
     */
    @GetMapping("/consultations/{consultationId}")
    @Operation(summary = "查询问诊详情和文字消息")
    public Result<ConsultationDetailVO> getConsultationDetail(
            @PathVariable @jakarta.validation.constraints.Positive(message = "consultationId 必须为正数") Long consultationId) {
        return Result.success("查询成功", consultationService.getConsultationDetail(consultationId));
    }

    /**
     * 向当前账号可访问的进行中问诊发送患者文字消息。
     *
     * @param consultationId 问诊记录 ID
     * @param idempotencyKey 客户端幂等键
     * @param request 文字消息请求参数
     * @return 已发送消息信息
     */
    @PostMapping("/consultations/{consultationId}/messages")
    @Operation(summary = "发送文字问诊消息")
    public Result<ConsultationMessageSendVO> sendConsultationMessage(
            @PathVariable @jakarta.validation.constraints.Positive(message = "consultationId 必须为正数") Long consultationId,
            @RequestHeader(HeaderConstant.IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey,
            @Valid @RequestBody ConsultationMessageSendRequest request) {
        Long userId = CUserContext.getRequired().userId();
        // 路径中包含问诊 ID，保证不同问诊的同名幂等键互不影响。
        IdempotencyPayload<ConsultationMessageSendVO> payload = idempotencyService.execute(
                userId,
                "/c/v1/consultations/" + consultationId + "/messages",
                idempotencyKey,
                request,
                ConsultationMessageSendVO.class,
                () -> new IdempotencyPayload<>("消息已发送",
                        consultationService.sendConsultationMessage(consultationId, request)));
        return Result.success(payload.message(), payload.data());
    }
}

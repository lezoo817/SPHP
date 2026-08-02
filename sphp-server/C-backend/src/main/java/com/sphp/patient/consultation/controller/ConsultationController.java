package com.sphp.patient.consultation.controller;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.consultation.dto.PreConsultationSaveRequest;
import com.sphp.patient.consultation.service.ConsultationService;
import com.sphp.patient.consultation.vo.PreConsultationSaveVO;
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
}

package com.sphp.patient.registration.controller;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.registration.dto.RegisteringAppointmentCreateRequest;
import com.sphp.patient.registration.service.RegisteringService;
import com.sphp.patient.registration.vo.RegisteringAppointmentCreateVO;
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
 * C端挂号订单与模拟支付接口。
 */
@RestController
@Validated
@RequestMapping("/c/v1")
@Tag(name = "C端挂号与支付", description = "创建挂号订单、候补和模拟支付")
@RequiredArgsConstructor
public class RegisteringController {

    private final RegisteringService registeringService;
    private final CIdempotencyService idempotencyService;

    /**
     * 创建挂号锁定订单并返回待支付信息。
     *
     * @param idempotencyKey 客户端幂等键
     * @param request 创建挂号请求参数
     * @return 锁号订单响应
     */
    @PostMapping("/appointments")
    @Operation(summary = "创建挂号锁定订单")
    public Result<RegisteringAppointmentCreateVO> registeringCreateAppointment(
            @RequestHeader(HeaderConstant.IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey,
            @Valid @RequestBody RegisteringAppointmentCreateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        // 幂等键按用户和接口隔离，成功重放时复用首次锁号结果。
        IdempotencyPayload<RegisteringAppointmentCreateVO> payload = idempotencyService.execute(
                userId,
                "/c/v1/appointments",
                idempotencyKey,
                request,
                RegisteringAppointmentCreateVO.class,
                () -> new IdempotencyPayload<>("号源锁定成功，请在15分钟内完成支付",
                        registeringService.registeringCreateAppointment(request)));
        return Result.success(payload.message(), payload.data());
    }
}

package com.sphp.patient.registration.controller;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.registration.dto.RegisteringAppointmentCreateRequest;
import com.sphp.patient.registration.service.RegisteringService;
import com.sphp.patient.registration.vo.RegisteringAppointmentCreateVO;
import com.sphp.patient.registration.dto.RegisteringPaymentSimulateRequest;
import com.sphp.patient.registration.dto.RegisteringWaitlistCreateRequest;
import com.sphp.patient.registration.vo.RegisteringAppointmentCancelVO;
import com.sphp.patient.registration.vo.RegisteringAppointmentDetailVO;
import com.sphp.patient.registration.vo.RegisteringAppointmentListVO;
import com.sphp.patient.registration.vo.RegisteringPaymentStatusVO;
import com.sphp.patient.registration.vo.RegisteringPaymentSuccessVO;
import com.sphp.patient.registration.vo.RegisteringWaitlistCreateVO;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.constraints.Positive;

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

    /** 查询当前账号指定就诊人的挂号订单列表。 */
    @GetMapping("/appointments") @Operation(summary = "查询挂号订单列表")
    public Result<RegisteringAppointmentListVO> registeringListAppointments(
            @RequestParam(required = false) @Positive(message = "patientId 必须为正数") Long patientId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @Positive(message = "pageNo 必须为正数") Integer pageNo,
            @RequestParam(required = false) @Positive(message = "pageSize 必须为正数") Integer pageSize) {
        return Result.success("查询成功", registeringService.registeringListAppointments(patientId, status, pageNo, pageSize));
    }

    /** 查询当前账号可访问的挂号订单详情。 */
    @GetMapping("/appointments/{appointmentId}") @Operation(summary = "查询挂号订单详情")
    public Result<RegisteringAppointmentDetailVO> registeringGetAppointment(@PathVariable @Positive Long appointmentId) {
        return Result.success("查询成功", registeringService.registeringGetAppointment(appointmentId));
    }

    /** 取消当前账号可访问的未支付挂号订单。 */
    @PostMapping("/appointments/{appointmentId}/cancel") @Operation(summary = "取消未支付挂号订单")
    public Result<RegisteringAppointmentCancelVO> registeringCancelAppointment(
            @PathVariable @Positive Long appointmentId,
            @RequestHeader(HeaderConstant.IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey) {
        Long userId = CUserContext.getRequired().userId();
        IdempotencyPayload<RegisteringAppointmentCancelVO> payload = idempotencyService.execute(userId,
                "/c/v1/appointments/" + appointmentId + "/cancel", idempotencyKey, appointmentId,
                RegisteringAppointmentCancelVO.class, () -> new IdempotencyPayload<>("挂号订单已取消",
                        registeringService.registeringCancelAppointment(appointmentId)));
        return Result.success(payload.message(), payload.data());
    }

    /** 创建当前账号就诊人的挂号候补登记。 */
    @PostMapping("/waitlists") @Operation(summary = "创建候补登记")
    public Result<RegisteringWaitlistCreateVO> registeringCreateWaitlist(
            @RequestHeader(HeaderConstant.IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey,
            @Valid @RequestBody RegisteringWaitlistCreateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        IdempotencyPayload<RegisteringWaitlistCreateVO> payload = idempotencyService.execute(userId, "/c/v1/waitlists",
                idempotencyKey, request, RegisteringWaitlistCreateVO.class, () -> new IdempotencyPayload<>("候补登记成功",
                        registeringService.registeringCreateWaitlist(request)));
        return Result.success(payload.message(), payload.data());
    }

    /** 模拟支付当前账号的挂号支付单。 */
    @PostMapping("/payments/{paymentId}/simulate-pay") @Operation(summary = "模拟支付挂号订单")
    public Result<RegisteringPaymentSuccessVO> registeringSimulatePayment(@PathVariable @Positive Long paymentId,
            @RequestHeader(HeaderConstant.IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey,
            @Valid @RequestBody RegisteringPaymentSimulateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        IdempotencyPayload<RegisteringPaymentSuccessVO> payload = idempotencyService.execute(userId,
                "/c/v1/payments/" + paymentId + "/simulate-pay", idempotencyKey, request,
                RegisteringPaymentSuccessVO.class, () -> new IdempotencyPayload<>("支付成功",
                        registeringService.registeringSimulatePayment(paymentId, request)));
        return Result.success(payload.message(), payload.data());
    }

    /** 查询当前账号的挂号支付单状态。 */
    @GetMapping("/payments/{paymentId}") @Operation(summary = "查询支付状态")
    public Result<RegisteringPaymentStatusVO> registeringGetPayment(@PathVariable @Positive Long paymentId) {
        return Result.success("查询成功", registeringService.registeringGetPayment(paymentId));
    }
}

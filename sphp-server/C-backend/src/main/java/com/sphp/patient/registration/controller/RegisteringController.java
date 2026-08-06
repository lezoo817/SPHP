package com.sphp.patient.registration.controller;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.registration.dto.RegisteringAppointmentCreateRequest;
import com.sphp.patient.registration.dto.RegisteringAppointmentCancelRequest;
import com.sphp.patient.registration.service.RegisteringService;
import com.sphp.patient.registration.vo.RegisteringAppointmentCreateVO;
import com.sphp.patient.registration.dto.RegisteringWaitlistCreateRequest;
import com.sphp.patient.registration.vo.RegisteringAppointmentCancelVO;
import com.sphp.patient.registration.vo.RegisteringAppointmentDetailVO;
import com.sphp.patient.registration.vo.RegisteringAppointmentListVO;
import com.sphp.patient.registration.vo.RegisteringDoctorBookingStatusVO;
import com.sphp.patient.registration.vo.RegisteringPaymentStatusVO;
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

import static com.sphp.shared.common.constant.HeaderConstant.IDEMPOTENCY_KEY;

/**
 * C端挂号订单与模拟支付接口。
 */
@RestController
@Validated
@RequestMapping("/c/v1")
@Tag(name = "C端挂号与支付", description = "创建挂号订单、候补和模拟支付")
@RequiredArgsConstructor
public class RegisteringController {
    // 挂号服务
    private final RegisteringService registeringService;
    // 幂等键服务
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
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey,
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

    /**
     * 查询当前账号可访问的挂号订单列表。
     * @param patientId 挂号订单所属账号
     * @param status 挂号订单状态
     * @param pageNo 页码
     * @param pageSize 每页数量
     * @return 挂号订单列表
     */
    @GetMapping("/appointments") @Operation(summary = "查询挂号订单列表")
    public Result<RegisteringAppointmentListVO> registeringListAppointments(
            @RequestParam(required = false) @Positive(message = "patientId 必须为正数") Long patientId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @Positive(message = "pageNo 必须为正数") Integer pageNo,
            @RequestParam(required = false) @Positive(message = "pageSize 必须为正数") Integer pageSize) {
        return Result.success("查询成功", registeringService.registeringListAppointments(patientId, status, pageNo, pageSize));
    }

    /**
     * 查询当前账号对指定医生的成功预约状态。
     *
     * @param doctorId 医生 ID
     * @return 是否已支付或完成预约
     */
    @GetMapping("/appointments/doctor-booking-status")
    @Operation(summary = "查询医生重复预约状态")
    public Result<RegisteringDoctorBookingStatusVO> registeringGetDoctorBookingStatus(
            @RequestParam @Positive(message = "doctorId 必须为正数") Long doctorId) {
        return Result.success("查询成功", registeringService.registeringGetDoctorBookingStatus(doctorId));
    }

    /**
     * 查询挂号订单详情。
     * @param appointmentId 挂号订单ID
     * @return 挂号订单详情
     */
    @GetMapping("/appointments/{appointmentId}") @Operation(summary = "查询挂号订单详情")
    public Result<RegisteringAppointmentDetailVO> registeringGetAppointment(@PathVariable @Positive Long appointmentId) {
        return Result.success("查询成功", registeringService.registeringGetAppointment(appointmentId));
    }

    /**
     * 取消挂号订单。
     * @param appointmentId 挂号订单ID
     * @param idempotencyKey 幂等键
     * @param request 已支付挂号的登录密码；未支付订单可不传请求体
     * @return 挂号订单取消结果
     */
    @PostMapping("/appointments/{appointmentId}/cancel") @Operation(summary = "取消挂号订单")
    public Result<RegisteringAppointmentCancelVO> registeringCancelAppointment(
            @PathVariable @Positive Long appointmentId,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey,
            @Valid @RequestBody(required = false) RegisteringAppointmentCancelRequest request) {
        Long userId = CUserContext.getRequired().userId();
        // 幂等键按用户和接口隔离，成功重放时复用首次取消结果。
        IdempotencyPayload<RegisteringAppointmentCancelVO> payload = idempotencyService.execute(userId,
                "/c/v1/appointments/" + appointmentId + "/cancel", idempotencyKey,
                request == null ? appointmentId : request,
                RegisteringAppointmentCancelVO.class, () -> new IdempotencyPayload<>("挂号订单已取消",
                        registeringService.registeringCancelAppointment(appointmentId, request)));
        return Result.success(payload.message(), payload.data());
    }

    /**
     * 创建候补登记并返回待支付信息。
     * @param idempotencyKey 幂等键
     * @param request 候补登记请求参数
     * @return 候补登记响应
     */
    @PostMapping("/waitlists") @Operation(summary = "创建候补登记")
    public Result<RegisteringWaitlistCreateVO> registeringCreateWaitlist(
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey,
            @Valid @RequestBody RegisteringWaitlistCreateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        IdempotencyPayload<RegisteringWaitlistCreateVO> payload = idempotencyService.execute(userId, "/c/v1/waitlists",
                idempotencyKey, request, RegisteringWaitlistCreateVO.class, () -> new IdempotencyPayload<>("候补登记成功",
                        registeringService.registeringCreateWaitlist(request)));
        return Result.success(payload.message(), payload.data());
    }

    /**
     * 模拟支付并返回支付结果。
     * @param paymentId 支付单ID
     * @return 支付结果
     */
    @GetMapping("/payments/{paymentId}") @Operation(summary = "查询支付状态")
    public Result<RegisteringPaymentStatusVO> registeringGetPayment(@PathVariable @Positive Long paymentId) {
        return Result.success("查询成功", registeringService.registeringGetPayment(paymentId));
    }
}

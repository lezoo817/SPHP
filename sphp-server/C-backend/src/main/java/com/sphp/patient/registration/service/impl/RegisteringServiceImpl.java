package com.sphp.patient.registration.service.impl;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.common.enums.RegisteringAppointmentStatusEnum;
import com.sphp.patient.common.enums.RegisteringPaymentStatusEnum;
import com.sphp.patient.common.enums.RegisteringWaitlistStatusEnum;
import com.sphp.patient.registration.config.RegistrationProperties;
import com.sphp.patient.registration.dto.RegisteringAppointmentCreateRequest;
import com.sphp.patient.registration.entity.RegisteringAppointment;
import com.sphp.patient.registration.entity.RegisteringPaymentOrder;
import com.sphp.patient.registration.entity.RegisteringWaitlist;
import com.sphp.patient.registration.mapper.RegisteringAppointmentMapper;
import com.sphp.patient.registration.mapper.RegisteringDataMapper;
import com.sphp.patient.registration.mapper.RegisteringPaymentOrderMapper;
import com.sphp.patient.registration.mapper.RegisteringSlotLockRecord;
import com.sphp.patient.registration.mapper.RegisteringAppointmentRecord;
import com.sphp.patient.registration.mapper.RegisteringPaymentRecord;
import com.sphp.patient.registration.mapper.RegisteringWaitlistMapper;
import com.sphp.patient.registration.service.RegisteringService;
import com.sphp.patient.registration.support.RegisteringSlotLockService;
import com.sphp.patient.registration.vo.RegisteringAppointmentCreateVO;
import com.sphp.patient.registration.vo.RegisteringAppointmentListVO;
import com.sphp.patient.registration.vo.RegisteringAppointmentDetailVO;
import com.sphp.patient.registration.vo.RegisteringAppointmentCancelVO;
import com.sphp.patient.registration.vo.RegisteringWaitlistCreateVO;
import com.sphp.patient.registration.vo.RegisteringPaymentSuccessVO;
import com.sphp.patient.registration.vo.RegisteringPaymentStatusVO;
import com.sphp.patient.registration.dto.RegisteringWaitlistCreateRequest;
import com.sphp.patient.registration.dto.RegisteringPaymentSimulateRequest;
import com.sphp.patient.registration.event.RegisteringAppointmentLockedEvent;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import org.mindrot.jbcrypt.BCrypt;

/**
 * C端挂号订单与支付服务实现。
 */
@Service
@RequiredArgsConstructor
public class RegisteringServiceImpl implements RegisteringService {

    private final RegisteringDataMapper dataMapper;
    private final RegisteringAppointmentMapper appointmentMapper;
    private final RegisteringPaymentOrderMapper paymentMapper;
    private final RegisteringSlotLockService slotLockService;
    private final RegisteringWaitlistMapper waitlistMapper;
    private final RegistrationProperties registrationProperties;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 创建挂号锁定订单并生成待支付单。
     *
     * @param request 创建挂号请求参数
     * @return 锁号成功后的订单与支付单信息
     * @throws CAuthException 就诊人、医院链路、号源或状态不满足要求时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisteringAppointmentCreateVO registeringCreateAppointment(RegisteringAppointmentCreateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        Long patientId = registeringResolveAccessiblePatient(userId, request.getPatientId());
        RegisteringSlotLockRecord slot = dataMapper.selectRegisteringSlotLockInfo(request.getHospitalId(), request.getSlotId());
        registeringValidateSlot(slot);
        long availableCount = dataMapper.countRegisteringAvailableSnapshots(slot.slotId());
        Duration ttl = Duration.ofSeconds(Math.max(registrationProperties.getPaymentTimeout(), 1));
        // 先以 Lua 预扣余量，再在数据库事务中锁定具体快照，双层校验防止超卖。
        if (!slotLockService.registeringLock(slot.slotId(), availableCount, ttl)) {
            throw new CAuthException(ErrorCodeEnum.HIGH_CONCURRENCY_INVENTORY_CONFLICT,
                    HttpStatus.CONFLICT, "当前时段号源已约满");
        }
        try {
            OffsetDateTime now = OffsetDateTime.now();
            Long snapshotId = dataMapper.registeringLockOneSnapshot(slot.slotId(), patientId, now);
            if (snapshotId == null) {
                throw new CAuthException(ErrorCodeEnum.HIGH_CONCURRENCY_INVENTORY_CONFLICT,
                        HttpStatus.CONFLICT, "当前时段号源已约满");
            }
            OffsetDateTime expireAt = now.plusSeconds(Math.max(registrationProperties.getPaymentTimeout(), 1));
            RegisteringAppointment appointment = new RegisteringAppointment();
            appointment.setSlotSnapshotId(snapshotId);
            appointment.setPatientId(patientId);
            appointment.setDoctorId(slot.doctorId());
            appointment.setStatus(RegisteringAppointmentStatusEnum.UNPAID.name());
            appointment.setAmountCent(slot.feeCent());
            appointment.setExpireAt(expireAt);
            if (appointmentMapper.insert(appointment) != 1) {
                throw systemError("挂号订单创建失败");
            }
            RegisteringPaymentOrder payment = new RegisteringPaymentOrder();
            payment.setAppointmentId(appointment.getId());
            payment.setPayerUserId(userId);
            payment.setAmountCent(slot.feeCent());
            payment.setStatus(RegisteringPaymentStatusEnum.PENDING.name());
            payment.setExpireAt(expireAt);
            if (paymentMapper.insert(payment) != 1) {
                throw systemError("支付单创建失败");
            }
            // 事务提交后由监听器投递延迟消息，支付完成前自动触发超时检查。
            eventPublisher.publishEvent(RegisteringAppointmentLockedEvent.registeringOf(appointment.getId(), userId));
            return RegisteringAppointmentCreateVO.builder()
                    .appointmentId(appointment.getId())
                    .status(appointment.getStatus())
                    .amountCent(appointment.getAmountCent())
                    .expireAt(expireAt)
                    .paymentId(payment.getId())
                    .build();
        } catch (RuntimeException exception) {
            // 任一数据库步骤失败都归还 Lua 已预扣余量，避免形成不可购买的幽灵锁定。
            slotLockService.registeringUnlock(slot.slotId());
            throw exception;
        }
    }

    /** 查询当前账号指定就诊人的挂号订单分页列表。 */
    @Override
    public RegisteringAppointmentListVO registeringListAppointments(Long patientId, String status, Integer pageNo, Integer pageSize) {
        Long userId = CUserContext.getRequired().userId();
        Long targetPatientId = registeringResolveAccessiblePatient(userId, patientId);
        registeringValidateAppointmentStatus(status);
        int resolvedPageNo = pageNo == null ? 1 : pageNo;
        int resolvedPageSize = pageSize == null ? 20 : pageSize;
        if (resolvedPageSize > 100) throw new CAuthException(ErrorCodeEnum.PARAMETER_OUT_OF_RANGE, HttpStatus.BAD_REQUEST, "pageSize 不能超过100");
        List<RegisteringAppointmentListVO.Item> records = dataMapper.selectRegisteringAppointments(targetPatientId, status,
                        resolvedPageSize, (long) (resolvedPageNo - 1) * resolvedPageSize).stream()
                .map(this::registeringToListItem).toList();
        return RegisteringAppointmentListVO.builder().pageNo(resolvedPageNo).pageSize(resolvedPageSize)
                .total(dataMapper.countRegisteringAppointments(targetPatientId, status)).records(records).build();
    }

    /** 查询当前账号可访问的挂号订单详情。 */
    @Override
    public RegisteringAppointmentDetailVO registeringGetAppointment(Long appointmentId) {
        RegisteringAppointmentRecord record = registeringRequireOwnedAppointment(appointmentId);
        return registeringToDetail(record);
    }

    /** 取消当前账号可访问的未支付挂号订单。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisteringAppointmentCancelVO registeringCancelAppointment(Long appointmentId) {
        RegisteringAppointmentRecord record = registeringRequireOwnedAppointment(appointmentId);
        OffsetDateTime now = OffsetDateTime.now();
        // 条件更新确保支付、超时消费者和主动取消只有一个请求能完成状态流转。
        if (dataMapper.registeringCancelUnpaidAppointment(appointmentId, now) != 1) {
            throw new CAuthException(ErrorCodeEnum.ORDER_CLOSED_OR_STATUS_INVALID, HttpStatus.CONFLICT, "当前挂号订单不可取消");
        }
        dataMapper.registeringClosePendingPayment(appointmentId, now);
        if (dataMapper.registeringReleaseLockedSnapshot(record.snapshotId(), now) == 1) slotLockService.registeringUnlock(record.slotId());
        return RegisteringAppointmentCancelVO.builder().appointmentId(appointmentId).status(RegisteringAppointmentStatusEnum.CANCELLED.name()).build();
    }

    /** 创建当前账号就诊人的挂号候补登记。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisteringWaitlistCreateVO registeringCreateWaitlist(RegisteringWaitlistCreateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        Long patientId = registeringResolveAccessiblePatient(userId, request.getPatientId());
        // 锁定时段行后再校验余量和队列号，保证同一时段候补号连续且不重复。
        RegisteringSlotLockRecord slot = dataMapper.lockRegisteringWaitlistSlot(request.getSlotId());
        registeringValidateSlot(slot);
        if (dataMapper.countRegisteringAvailableSnapshots(request.getSlotId()) > 0) {
            throw new CAuthException(ErrorCodeEnum.ORDER_CLOSED_OR_STATUS_INVALID, HttpStatus.CONFLICT, "当前时段仍可预约，无需候补");
        }
        if (dataMapper.existsRegisteringActiveWaitlist(patientId, request.getSlotId())) {
            throw new CAuthException(ErrorCodeEnum.DUPLICATE_REQUEST, HttpStatus.CONFLICT, "已登记该时段候补");
        }
        int queueNo = dataMapper.selectRegisteringNextQueueNo(request.getSlotId());
        RegisteringWaitlist waitlist = new RegisteringWaitlist();
        waitlist.setPatientId(patientId); waitlist.setSlotId(request.getSlotId()); waitlist.setQueueNo(queueNo);
        waitlist.setStatus(RegisteringWaitlistStatusEnum.WAITING.name());
        if (waitlistMapper.insert(waitlist) != 1) throw systemError("候补登记失败");
        return RegisteringWaitlistCreateVO.builder().waitlistId(waitlist.getId()).slotId(waitlist.getSlotId())
                .status(waitlist.getStatus()).queueNo(queueNo).build();
    }

    /** 模拟支付当前账号的挂号支付单。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisteringPaymentSuccessVO registeringSimulatePayment(Long paymentId, RegisteringPaymentSimulateRequest request) {
        RegisteringPaymentRecord payment = registeringRequireOwnedPayment(paymentId);
        OffsetDateTime now = OffsetDateTime.now();
        if (payment.expireAt().isBefore(now) || payment.expireAt().isEqual(now)) {
            // 即使延迟消息尚未投递，支付入口也必须阻止过期订单继续付款。
            registeringExpirePayment(payment, now);
            throw new CAuthException(ErrorCodeEnum.PAYMENT_TIMEOUT, HttpStatus.CONFLICT, "支付单已过期");
        }
        if (!RegisteringPaymentStatusEnum.PENDING.name().equals(payment.paymentStatus())
                || !RegisteringAppointmentStatusEnum.UNPAID.name().equals(payment.appointmentStatus())) {
            throw new CAuthException(ErrorCodeEnum.ORDER_CLOSED_OR_STATUS_INVALID, HttpStatus.CONFLICT, "支付单状态不允许付款");
        }
        // BCrypt 仅在内存中比对登录密码，禁止 trim、日志和缓存原文。
        if (!BCrypt.checkpw(request.getLoginPassword(), payment.passwordHash())) {
            throw new CAuthException(ErrorCodeEnum.PASSWORD_VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "支付密码校验失败");
        }
        if (dataMapper.registeringMarkPaymentSuccess(paymentId, now) != 1
                || dataMapper.registeringMarkAppointmentPaid(payment.appointmentId(), now) != 1
                || dataMapper.registeringMarkSnapshotSold(payment.snapshotId(), now) != 1) {
            throw new CAuthException(ErrorCodeEnum.ORDER_CLOSED_OR_STATUS_INVALID, HttpStatus.CONFLICT, "支付单状态已变化");
        }
        return RegisteringPaymentSuccessVO.builder().paymentId(paymentId).status(RegisteringPaymentStatusEnum.SUCCESS.name()).paidAt(now).build();
    }

    /** 查询当前账号的挂号支付单状态。 */
    @Override
    public RegisteringPaymentStatusVO registeringGetPayment(Long paymentId) {
        RegisteringPaymentRecord payment = registeringRequireOwnedPayment(paymentId);
        return RegisteringPaymentStatusVO.builder().id(payment.id()).appointmentOrderId(payment.appointmentId())
                .amountCent(payment.amountCent()).status(payment.paymentStatus()).paidAt(payment.paidAt()).expireAt(payment.expireAt()).build();
    }

    /**
     * 解析并校验当前账号可访问的就诊人，未传时使用本人。
     *
     * @param userId 当前 C端用户 ID
     * @param requestedPatientId 请求指定的就诊人 ID
     * @return 已授权就诊人 ID
     * @throws CAuthException 就诊人不存在或无访问权限时抛出
     */
    private Long registeringResolveAccessiblePatient(Long userId, Long requestedPatientId) {
        Long patientId = requestedPatientId == null ? dataMapper.selectRegisteringSelfPatientId(userId) : requestedPatientId;
        if (patientId == null || !dataMapper.existsRegisteringActivePatient(patientId)) {
            throw new CAuthException(ErrorCodeEnum.INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "就诊人不存在或已停用");
        }
        if (!dataMapper.hasActivePatientRelation(userId, patientId)) {
            throw new CAuthException(ErrorCodeEnum.UNAUTHORIZED, HttpStatus.FORBIDDEN, "无权访问该就诊人");
        }
        return patientId;
    }

    /** 校验挂号订单状态筛选值。 */
    private void registeringValidateAppointmentStatus(String status) {
        if (status != null && Arrays.stream(RegisteringAppointmentStatusEnum.values()).noneMatch(item -> item.name().equals(status)))
            throw new CAuthException(ErrorCodeEnum.PARAMETER_OUT_OF_RANGE, HttpStatus.BAD_REQUEST, "订单状态不在允许范围内");
    }

    /** 查询订单并基于订单患者反查当前账号归属。 */
    private RegisteringAppointmentRecord registeringRequireOwnedAppointment(Long appointmentId) {
        RegisteringAppointmentRecord record = dataMapper.selectRegisteringAppointment(appointmentId);
        if (record == null) throw new CAuthException(ErrorCodeEnum.INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "挂号订单不存在");
        registeringResolveAccessiblePatient(CUserContext.getRequired().userId(), record.patientId());
        return record;
    }

    /** 查询支付单并校验付款人和订单患者归属。 */
    private RegisteringPaymentRecord registeringRequireOwnedPayment(Long paymentId) {
        RegisteringPaymentRecord payment = dataMapper.selectRegisteringPayment(paymentId);
        if (payment == null) throw new CAuthException(ErrorCodeEnum.INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "支付单不存在");
        Long userId = CUserContext.getRequired().userId();
        if (!userId.equals(payment.payerUserId())) throw new CAuthException(ErrorCodeEnum.UNAUTHORIZED, HttpStatus.FORBIDDEN, "无权访问该支付单");
        registeringResolveAccessiblePatient(userId, payment.patientId());
        return payment;
    }

    /** 将超时支付单按条件关闭并释放对应号源。 */
    private void registeringExpirePayment(RegisteringPaymentRecord payment, OffsetDateTime now) {
        if (dataMapper.registeringCancelUnpaidAppointment(payment.appointmentId(), now) == 1) {
            dataMapper.registeringClosePendingPayment(payment.appointmentId(), now);
            if (dataMapper.registeringReleaseLockedSnapshot(payment.snapshotId(), now) == 1) slotLockService.registeringUnlock(payment.slotId());
        }
    }

    /** 将订单联表记录转换为列表展示项。 */
    private RegisteringAppointmentListVO.Item registeringToListItem(RegisteringAppointmentRecord record) {
        return RegisteringAppointmentListVO.Item.builder().id(record.id()).doctorName(record.doctorName())
                .departmentName(record.departmentName()).startTime(registeringToOffset(record.scheduleDate(), record.startTime()))
                .status(record.status()).amountCent(record.amountCent()).expireAt(record.expireAt()).build();
    }

    /** 将订单联表记录转换为详情响应。 */
    private RegisteringAppointmentDetailVO registeringToDetail(RegisteringAppointmentRecord record) {
        return RegisteringAppointmentDetailVO.builder().id(record.id()).status(record.status())
                .doctor(RegisteringAppointmentDetailVO.Doctor.builder().id(record.doctorId()).name(record.doctorName()).departmentName(record.departmentName()).build())
                .slot(RegisteringAppointmentDetailVO.Slot.builder().id(record.slotId())
                        .startTime(registeringToOffset(record.scheduleDate(), record.startTime()))
                        .endTime(registeringToOffset(record.scheduleDate(), record.endTime())).build())
                .amountCent(record.amountCent()).expireAt(record.expireAt())
                .payment(RegisteringAppointmentDetailVO.Payment.builder().id(record.paymentId()).status(record.paymentStatus()).build()).build();
    }

    /** 按业务时区组合排班日期和时段时间。 */
    private OffsetDateTime registeringToOffset(LocalDate date, java.time.LocalTime time) {
        return date.atTime(time).atZone(com.sphp.patient.common.constant.RegistrationConstant.BUSINESS_ZONE_ID).toOffsetDateTime();
    }

    /**
     * 校验时段是否属于可预约的已发布排班且未开始。
     *
     * @param slot 时段锁号记录
     * @throws CAuthException 时段不存在、医院链路不匹配或不可预约时抛出
     */
    private void registeringValidateSlot(RegisteringSlotLockRecord slot) {
        if (slot == null) {
            throw new CAuthException(ErrorCodeEnum.INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "号源时段不存在或已停用");
        }
        OffsetDateTime startAt = slot.scheduleDate().atTime(slot.startTime())
                .atZone(com.sphp.patient.common.constant.RegistrationConstant.BUSINESS_ZONE_ID).toOffsetDateTime();
        if (!"PUBLISHED".equals(slot.scheduleStatus()) || !"ENABLED".equals(slot.doctorStatus())
                || !startAt.isAfter(OffsetDateTime.now())) {
            throw new CAuthException(ErrorCodeEnum.ORDER_CLOSED_OR_STATUS_INVALID,
                    HttpStatus.CONFLICT, "当前时段不可预约");
        }
    }

    /**
     * 创建内部系统异常。
     *
     * @param message 面向客户端的提示信息
     * @return 系统异常
     */
    private CAuthException systemError(String message) {
        return new CAuthException(ErrorCodeEnum.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}

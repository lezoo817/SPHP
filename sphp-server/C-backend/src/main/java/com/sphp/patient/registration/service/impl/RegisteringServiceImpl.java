package com.sphp.patient.registration.service.impl;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.common.enums.RegisteringAppointmentStatusEnum;
import com.sphp.patient.common.enums.RegisteringPaymentStatusEnum;
import com.sphp.patient.common.enums.RegisteringWaitlistStatusEnum;
import com.sphp.patient.registration.config.RegistrationProperties;
import com.sphp.patient.registration.dto.RegisteringAppointmentCreateRequest;
import com.sphp.patient.registration.dto.RegisteringAppointmentCancelRequest;
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
import com.sphp.patient.registration.support.RegisteringWaitlistPromotionService;
import com.sphp.patient.registration.vo.RegisteringAppointmentCreateVO;
import com.sphp.patient.registration.vo.RegisteringAppointmentListVO;
import com.sphp.patient.registration.vo.RegisteringDoctorBookingStatusVO;
import com.sphp.patient.registration.vo.RegisteringAppointmentDetailVO;
import com.sphp.patient.registration.vo.RegisteringAppointmentCancelVO;
import com.sphp.patient.registration.vo.RegisteringWaitlistCreateVO;
import com.sphp.patient.registration.vo.RegisteringPaymentSuccessVO;
import com.sphp.patient.registration.vo.RegisteringPaymentStatusVO;
import com.sphp.patient.registration.dto.RegisteringWaitlistCreateRequest;
import com.sphp.patient.registration.dto.RegisteringPaymentSimulateRequest;
import com.sphp.patient.registration.event.RegisteringAppointmentLockedEvent;
import com.sphp.patient.notification.mq.producer.NotificationEventProducer;
import com.sphp.patient.common.enums.NotificationTypeEnum;
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

import static com.sphp.patient.common.constant.RegistrationConstant.BUSINESS_ZONE_ID;
import static com.sphp.patient.common.enums.NotificationTypeEnum.APPOINTMENT;
import static com.sphp.patient.common.enums.RegisteringAppointmentStatusEnum.CANCELLED;
import static com.sphp.patient.common.enums.RegisteringAppointmentStatusEnum.UNPAID;
import static com.sphp.patient.common.enums.RegisteringPaymentStatusEnum.PENDING;
import static com.sphp.patient.common.enums.RegisteringWaitlistStatusEnum.WAITING;
import static com.sphp.shared.common.enums.ErrorCodeEnum.*;

/**
 * C端挂号订单与支付服务实现。
 */
@Service
@RequiredArgsConstructor
public class RegisteringServiceImpl implements RegisteringService {
    // 数据访问
    private final RegisteringDataMapper dataMapper;
    // 挂号订单
    private final RegisteringAppointmentMapper appointmentMapper;
    // 挂号支付
    private final RegisteringPaymentOrderMapper paymentMapper;
    // 号源锁
    private final RegisteringSlotLockService slotLockService;
    // 候补晋级与通知
    private final RegisteringWaitlistPromotionService waitlistPromotionService;
    // 候补
    private final RegisteringWaitlistMapper waitlistMapper;
    // 挂号配置
    private final RegistrationProperties registrationProperties;
    // 事件
    private final ApplicationEventPublisher eventPublisher;
    // 通知
    private final NotificationEventProducer notificationEventProducer;

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
        // 可访问的就诊人
        Long patientId = registeringResolveAccessiblePatient(userId, request.getPatientId());
        RegisteringSlotLockRecord slot = dataMapper.selectRegisteringSlotLockInfo(request.getHospitalId(), request.getSlotId());
        // 验证号源
        registeringValidateSlot(slot);
        // 患者行锁与有效待就诊挂号检查必须先于 Redis 预扣，避免同一就诊人重复预约占用号源。
        registeringEnsurePatientCanBookDoctor(patientId, slot.doctorId());
        long availableCount = dataMapper.countRegisteringAvailableSnapshots(slot.slotId());
        // 支付超时
        Duration ttl = Duration.ofSeconds(Math.max(registrationProperties.getPaymentTimeout(), 1));
        // 先以 Lua 预扣余量，再在数据库事务中锁定具体快照，双层校验防止超卖。
        if (!slotLockService.registeringLock(slot.slotId(), availableCount, ttl)) {
            throw new CAuthException(HIGH_CONCURRENCY_INVENTORY_CONFLICT,
                    HttpStatus.CONFLICT, "当前时段号源已约满");
        }
        try {
            OffsetDateTime now = OffsetDateTime.now(BUSINESS_ZONE_ID);
            // 锁定一个快照
            Long snapshotId = dataMapper.registeringLockOneSnapshot(slot.slotId(), patientId, now);
            // 没有锁定到快照
            if (snapshotId == null) {
                throw new CAuthException(HIGH_CONCURRENCY_INVENTORY_CONFLICT,
                        HttpStatus.CONFLICT, "当前时段号源已约满");
            }
            // 支付不得超过时段结束；常规 900 秒窗口与结束时间取较早值。
            OffsetDateTime expireAt = registeringResolvePaymentExpireAt(slot, now);
            RegisteringAppointment appointment = new RegisteringAppointment();
            appointment.setSlotSnapshotId(snapshotId);
            appointment.setPatientId(patientId);
            appointment.setDoctorId(slot.doctorId());
            appointment.setStatus(UNPAID.name()); // 待支付挂号
            appointment.setAmountCent(slot.feeCent());
            appointment.setExpireAt(expireAt);
            if (appointmentMapper.insert(appointment) != 1) {
                throw systemError("挂号订单创建失败");
            }
            RegisteringPaymentOrder payment = new RegisteringPaymentOrder();
            payment.setAppointmentId(appointment.getId());
            payment.setPayerUserId(userId);
            payment.setAmountCent(slot.feeCent());
            payment.setStatus(PENDING.name()); // 待支付订单
            payment.setExpireAt(expireAt);
            if (paymentMapper.insert(payment) != 1) {
                throw systemError("支付单创建失败");
            }
            // 候补人通过正常锁号成功后结束其候补状态，普通挂号不会命中任何记录。
            waitlistPromotionService.registeringFulfillNotifiedWaitlist(userId, patientId, slot.slotId(), now);
            // 事务提交后由监听器投递延迟消息，支付完成前自动触发超时检查。
            eventPublisher.publishEvent(RegisteringAppointmentLockedEvent.registeringOf(appointment.getId(), userId, expireAt));
            // 锁号成功后异步生成待支付通知，通知写入不会阻塞订单主事务。
            notificationEventProducer.publishNotification(
                    "APPOINTMENT_LOCKED",
                    appointment.getId(),
                    userId,
                    patientId,
                    APPOINTMENT,
                    "挂号订单待支付",
                    "请在规定时间内完成支付。"
            );
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

    /**
     * 列出挂号订单。
     * @param patientId 就诊人 ID
     * @param status 订单状态
     * @param pageNo 页码
     * @param pageSize 每页数量
     * @return 挂号订单列表
     */
    @Override
    public RegisteringAppointmentListVO registeringListAppointments(Long patientId, String status, Integer pageNo, Integer pageSize) {
        Long userId = CUserContext.getRequired().userId();
        // 可访问的就诊人
        Long targetPatientId = registeringResolveAccessiblePatient(userId, patientId);
        // 订单状态
        registeringValidateAppointmentStatus(status);
        int resolvedPageNo = pageNo == null ? 1 : pageNo;
        int resolvedPageSize = pageSize == null ? 20 : pageSize;
        if (resolvedPageSize > 100) throw new CAuthException(PARAMETER_OUT_OF_RANGE, HttpStatus.BAD_REQUEST, "pageSize 不能超过100");

        List<RegisteringAppointmentListVO.Item> records = dataMapper.selectRegisteringAppointments(targetPatientId, status,
                        resolvedPageSize, (long) (resolvedPageNo - 1) * resolvedPageSize).stream()
                .map(this::registeringToListItem).toList();
        return RegisteringAppointmentListVO.builder()
                .pageNo(resolvedPageNo)
                .pageSize(resolvedPageSize)
                .total(dataMapper.countRegisteringAppointments(targetPatientId, status))
                .records(records)
                .build();
    }

    /**
     * 查询当前就诊人是否已有指定医生的有效待就诊挂号。
     *
     * @param doctorId 医生 ID
     * @param patientId 可选就诊人 ID，未传时使用当前账号本人
     * @return 当前就诊人的有效待就诊挂号状态
     */
    @Override
    public RegisteringDoctorBookingStatusVO registeringGetDoctorBookingStatus(Long doctorId, Long patientId) {
        Long userId = CUserContext.getRequired().userId();
        // 先解析可访问就诊人，防止通过预约状态接口探测其他账号的患者挂号情况。
        Long targetPatientId = registeringResolveAccessiblePatient(userId, patientId);
        // 与创建及支付链路复用同一有效挂号查询，保证前端展示规则与最终拦截规则一致。
        boolean booked = dataMapper.existsRegisteringActivePatientDoctorAppointment(targetPatientId, doctorId);
        return RegisteringDoctorBookingStatusVO.builder()
                .doctorId(doctorId)
                .booked(booked)
                .build();
    }

    /***
     * 获取挂号订单详情。
     * @param appointmentId 挂号订单 ID
     * @return 挂号订单详情
     */
    @Override
    public RegisteringAppointmentDetailVO registeringGetAppointment(Long appointmentId) {
        // 可访问的挂号订单
        RegisteringAppointmentRecord record = registeringRequireOwnedAppointment(appointmentId);
        return registeringToDetail(record);
    }

    /**
     * 取消挂号订单。
     * @param appointmentId 挂号订单 ID
     * @return 挂号订单取消结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisteringAppointmentCancelVO registeringCancelAppointment(Long appointmentId,
                                                                        RegisteringAppointmentCancelRequest request) {
        // 可访问的挂号订单
        RegisteringAppointmentRecord record = registeringRequireOwnedAppointment(appointmentId);
        OffsetDateTime now = OffsetDateTime.now(BUSINESS_ZONE_ID);
        if (UNPAID.name().equals(record.status())) {
            // 条件更新确保支付、超时消费者和主动取消只有一个请求能完成状态流转。
            if (dataMapper.registeringCancelUnpaidAppointment(appointmentId, now) != 1) {
                throw new CAuthException(ORDER_CLOSED_OR_STATUS_INVALID, HttpStatus.CONFLICT, "当前挂号订单不可取消");
            }
            dataMapper.registeringClosePendingPayment(appointmentId, now);
            // 未支付订单只可能释放 LOCKED 快照。
            registeringReleaseAppointmentSnapshot(record, false, now);
        } else if (RegisteringAppointmentStatusEnum.PAID.name().equals(record.status())) {
            // 已支付取消只允许支付账号本人，禁止共享就诊人关系绕过付款密码。
            RegisteringPaymentRecord payment = registeringRequirePaidCancellationPayment(record);
            registeringValidatePaidCancellationPassword(request, payment.passwordHash());
            // SQL 同时校验 PAID 状态和时段尚未开始，避免取消与接诊并发穿透时间边界。
            if (dataMapper.registeringCancelPaidAppointment(appointmentId, now) != 1) {
                throw new CAuthException(ORDER_CLOSED_OR_STATUS_INVALID, HttpStatus.CONFLICT, "预约已开始或当前挂号订单不可取消");
            }
            // 已支付订单保留支付单 SUCCESS，仅将已售号源重新释放。
            registeringReleaseAppointmentSnapshot(record, true, now);
        } else {
            throw new CAuthException(ORDER_CLOSED_OR_STATUS_INVALID, HttpStatus.CONFLICT, "当前挂号订单不可取消");
        }

        return RegisteringAppointmentCancelVO.builder()
                .appointmentId(appointmentId)
                .status(CANCELLED.name()) // 已取消
                .build();
    }

    /**
     * 校验已支付取消关联的支付单必须成功且归属当前登录账号。
     *
     * @param appointment 目标挂号订单
     * @return 当前账号拥有的成功支付单
     * @throws CAuthException 支付单缺失、归属不符或状态不允许取消时抛出
     */
    private RegisteringPaymentRecord registeringRequirePaidCancellationPayment(RegisteringAppointmentRecord appointment) {
        if (appointment.paymentId() == null) {
            throw new CAuthException(ORDER_CLOSED_OR_STATUS_INVALID, HttpStatus.CONFLICT, "挂号支付单不存在");
        }
        // 复用支付单归属查询，确保密码只能由实际付款账号验证。
        RegisteringPaymentRecord payment = registeringRequireOwnedPayment(appointment.paymentId());
        if (!RegisteringPaymentStatusEnum.SUCCESS.name().equals(payment.paymentStatus())
                || !RegisteringAppointmentStatusEnum.PAID.name().equals(payment.appointmentStatus())
                || !appointment.id().equals(payment.appointmentId())) {
            throw new CAuthException(ORDER_CLOSED_OR_STATUS_INVALID, HttpStatus.CONFLICT, "当前挂号订单不可取消");
        }
        return payment;
    }

    /**
     * 校验已支付挂号取消的当前登录密码。
     *
     * @param request 取消请求参数
     * @param passwordHash 当前付款账号的 BCrypt 密码哈希
     * @throws CAuthException 密码缺失或校验失败时抛出
     */
    private void registeringValidatePaidCancellationPassword(RegisteringAppointmentCancelRequest request, String passwordHash) {
        // 已支付取消属于敏感状态变更，缺少密码与密码不正确均拒绝处理。
        if (request == null || request.getLoginPassword() == null || request.getLoginPassword().isBlank()
                || !BCrypt.checkpw(request.getLoginPassword(), passwordHash)) {
            throw new CAuthException(PASSWORD_VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "登录密码校验失败");
        }
    }

    /**
     * 按订单支付状态释放号源快照并晋级候补队列。
     *
     * @param appointment 挂号订单
     * @param paidCancellation 是否为已支付取消
     * @param now 当前业务时间
     * @throws CAuthException 号源快照状态不一致时抛出并回滚订单取消
     */
    private void registeringReleaseAppointmentSnapshot(RegisteringAppointmentRecord appointment, boolean paidCancellation,
                                                       OffsetDateTime now) {
        int released = paidCancellation
                ? dataMapper.registeringReleaseSoldSnapshot(appointment.snapshotId(), now)
                : dataMapper.registeringReleaseLockedSnapshot(appointment.snapshotId(), now);
        // 快照必须成功释放，否则回滚订单状态，避免订单已取消但号源仍不可预约。
        if (released != 1) {
            throw new CAuthException(ORDER_CLOSED_OR_STATUS_INVALID, HttpStatus.CONFLICT, "号源状态已变化，无法取消挂号");
        }
        slotLockService.registeringUnlock(appointment.slotId());
        // 仅在号源快照实际释放后晋级候补，避免重复取消产生重复通知。
        waitlistPromotionService.registeringPromoteAfterSlotReleased(appointment.slotId());
    }

    /**
     * 创建候补挂号订单。
     * @param request 候补挂号请求参数
     * @return 候补挂号结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisteringWaitlistCreateVO registeringCreateWaitlist(RegisteringWaitlistCreateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        // 可访问的就诊人
        Long patientId = registeringResolveAccessiblePatient(userId, request.getPatientId());
        // 锁定时段行后再校验余量和队列号，保证同一时段候补号连续且不重复。
        RegisteringSlotLockRecord slot = dataMapper.lockRegisteringWaitlistSlot(request.getSlotId());
        // 校验时段是否属于可预约的已发布排班且尚未结束。
        registeringValidateSlot(slot);
        if (dataMapper.countRegisteringAvailableSnapshots(request.getSlotId()) > 0) {
            throw new CAuthException(ORDER_CLOSED_OR_STATUS_INVALID, HttpStatus.CONFLICT, "当前时段仍可预约，无需候补");
        }
        // 已存在待处理候补
        if (dataMapper.existsRegisteringActiveWaitlist(patientId, request.getSlotId())) {
            throw new CAuthException(DUPLICATE_REQUEST, HttpStatus.CONFLICT, "已登记该时段候补");
        }
        // 插入候补队列
        int queueNo = dataMapper.selectRegisteringNextQueueNo(request.getSlotId());
        RegisteringWaitlist waitlist = new RegisteringWaitlist();
        waitlist.setUserId(userId);
        waitlist.setPatientId(patientId);
        waitlist.setSlotId(request.getSlotId());
        waitlist.setQueueNo(queueNo); // 队列号
        waitlist.setStatus(WAITING.name()); // 待处理
        if (waitlistMapper.insert(waitlist) != 1) throw systemError("候补登记失败");
        // 通知
        notificationEventProducer.publishNotification(
                "APPOINTMENT_WAITLIST_CREATED", //事件类型
                waitlist.getId(), // 业务 ID
                userId,
                patientId,
                APPOINTMENT, /// 通知类型
                "候补登记成功",
                "已提交候补登记，出现可用号源时将通知您。"
        );
        return RegisteringWaitlistCreateVO.builder()
                .waitlistId(waitlist.getId()) // 候补 ID
                .slotId(waitlist.getSlotId()) // 时段 ID
                .status(waitlist.getStatus())
                .queueNo(queueNo) // 队列号
                .build();
    }

    /**
     * 模拟支付。
     * @param paymentId 支付单 ID
     * @param request 模拟支付请求参数
     * @return 模拟支付结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisteringPaymentSuccessVO registeringSimulatePayment(Long paymentId, RegisteringPaymentSimulateRequest request) {
        // 可访问的支付单
        RegisteringPaymentRecord payment = registeringRequireOwnedPayment(paymentId);
        OffsetDateTime now = OffsetDateTime.now(BUSINESS_ZONE_ID);
        if (payment.expireAt().isBefore(now) || payment.expireAt().isEqual(now)) {
            // 即使延迟消息尚未投递，支付入口也必须阻止过期订单继续付款。
            registeringExpirePayment(payment, now);
            throw new CAuthException(PAYMENT_TIMEOUT, HttpStatus.CONFLICT, "支付单已过期");
        }
        // 支付单状态校验
        if (!PENDING.name().equals(payment.paymentStatus())
                || !UNPAID.name().equals(payment.appointmentStatus())) {
            throw new CAuthException(ORDER_CLOSED_OR_STATUS_INVALID, HttpStatus.CONFLICT, "支付单状态不允许付款");
        }
        // BCrypt 仅在内存中比对登录密码，禁止 trim、日志和缓存原文。
        if (!BCrypt.checkpw(request.getLoginPassword(), payment.passwordHash())) {
            throw new CAuthException(PASSWORD_VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "支付密码校验失败");
        }
        // 串行化同一就诊人的支付确认，防止多个待支付订单并发支付同一医生。
        registeringEnsurePatientCanBookDoctor(payment.patientId(), payment.doctorId());
        // 条件更新确保支付、超时消费者和主动取消只有一个请求能完成状态流转。
        if (dataMapper.registeringMarkPaymentSuccess(paymentId, now) != 1
                || dataMapper.registeringMarkAppointmentPaid(payment.appointmentId(), now) != 1
                || dataMapper.registeringMarkSnapshotSold(payment.snapshotId(), now) != 1) {
            throw new CAuthException(ORDER_CLOSED_OR_STATUS_INVALID, HttpStatus.CONFLICT, "支付单状态已变化");
        }

        // 通知
        notificationEventProducer.publishNotification(
                "APPOINTMENT_PAYMENT_SUCCESS",
                payment.appointmentId(), // 业务 ID
                payment.payerUserId(),  // 支付者
                payment.patientId(),
                APPOINTMENT, // 通知类型
                "挂号支付成功",
                "您的挂号订单已支付成功。"
        );
        return RegisteringPaymentSuccessVO.builder()
                .paymentId(paymentId)
                .status(SUCCESS.name()) // 支付成功
                .paidAt(now)
                .build();
    }

    /**
     * 获取支付单状态。
     * @param paymentId 支付单 ID
     * @return 支付单状态
     */
    @Override
    public RegisteringPaymentStatusVO registeringGetPayment(Long paymentId) {
        RegisteringPaymentRecord payment = registeringRequireOwnedPayment(paymentId);
        return RegisteringPaymentStatusVO.builder()
                .id(payment.id())
                .appointmentOrderId(payment.appointmentId())
                .amountCent(payment.amountCent())
                .status(payment.paymentStatus())
                .paidAt(payment.paidAt())
                .expireAt(payment.expireAt())
                .build();
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
            throw new CAuthException(INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "就诊人不存在或已停用");
        }
        //如果存在关联
        if (!dataMapper.hasActivePatientRelation(userId, patientId)) {
            throw new CAuthException(UNAUTHORIZED, HttpStatus.FORBIDDEN, "无权访问该就诊人");
        }
        return patientId;
    }

    /**
     * 订单状态校验。
     * @param status 订单状态
     */
    private void registeringValidateAppointmentStatus(String status) {
        if (status != null && Arrays.stream(RegisteringAppointmentStatusEnum.values())
                .noneMatch(item -> item.name()
                        .equals(status)
                )
        )
            throw new CAuthException(PARAMETER_OUT_OF_RANGE, HttpStatus.BAD_REQUEST, "订单状态不在允许范围内");
    }

    /**
     * 获取指定挂号订单。
     * @param appointmentId 挂号订单 ID
     * @return 挂号订单
     */
    private RegisteringAppointmentRecord registeringRequireOwnedAppointment(Long appointmentId) {
        RegisteringAppointmentRecord record = dataMapper.selectRegisteringAppointment(appointmentId);
        // 挂号订单不存在
        if (record == null)
            throw new CAuthException(INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "挂号订单不存在");
        // 无权访问该挂号订单
        registeringResolveAccessiblePatient(CUserContext.getRequired().userId(), record.patientId());
        return record;
    }

    /**
     *  获取指定支付单。
     * @param paymentId 支付单 ID
     * @return 支付单
     */
    private RegisteringPaymentRecord registeringRequireOwnedPayment(Long paymentId) {
        RegisteringPaymentRecord payment = dataMapper.selectRegisteringPayment(paymentId);
        // 支付单不存在
        if (payment == null)
            throw new CAuthException(INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "支付单不存在");
        // 解析当前 C 端用户 ID
        Long userId = CUserContext.getRequired().userId();
        // 无权访问该支付单
        if (!userId.equals(payment.payerUserId()))
            throw new CAuthException(UNAUTHORIZED, HttpStatus.FORBIDDEN, "无权访问该支付单");
        // 解析并校验当前账号可访问的就诊人
        registeringResolveAccessiblePatient(userId, payment.patientId());
        return payment;
    }

    /**
     * 串行校验当前就诊人是否已有指定医生的有效待就诊挂号。
     *
     * @param patientId 就诊人 ID
     * @param doctorId 医生 ID
     * @throws CAuthException 就诊人不存在或已有该医生待就诊挂号时抛出
     */
    private void registeringEnsurePatientCanBookDoctor(Long patientId, Long doctorId) {
        // 锁定患者行，使挂号创建与支付确认在同一就诊人范围内串行执行。
        if (dataMapper.registeringLockActivePatient(patientId) == null) {
            throw new CAuthException(INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "就诊人不存在或已停用");
        }
        // 已完成、未到诊、取消和时段结束的记录不会命中该查询，完成就诊后允许再次预约。
        if (dataMapper.existsRegisteringActivePatientDoctorAppointment(patientId, doctorId)) {
            throw new CAuthException(DUPLICATE_REQUEST, HttpStatus.CONFLICT, "当前就诊人已有该医生待就诊挂号，不可重复预约");
        }
    }

    /**
     * 过期支付单。
     * @param payment 支付单
     * @param now 当前时间
     */
    private void registeringExpirePayment(RegisteringPaymentRecord payment, OffsetDateTime now) {
        // 取消未支付订单
        if (dataMapper.registeringCancelUnpaidAppointment(payment.appointmentId(), now) == 1) {
            // 关闭待支付订单
            dataMapper.registeringClosePendingPayment(payment.appointmentId(), now);
            if (dataMapper.registeringReleaseLockedSnapshot(payment.snapshotId(), now) == 1) {
                // 释放锁定的号源并通知候补队列的下一位。
                slotLockService.registeringUnlock(payment.slotId());
                waitlistPromotionService.registeringPromoteAfterSlotReleased(payment.slotId());
            }
        }
    }

    /** 将订单联表记录转换为列表展示项。 */
    private RegisteringAppointmentListVO.Item registeringToListItem(RegisteringAppointmentRecord record) {
        return RegisteringAppointmentListVO.Item.builder()
                .id(record.id())
                .doctorName(record.doctorName())
                .departmentName(record.departmentName())
                .departmentLocation(record.departmentLocation())
                .startTime(registeringToOffset(record.scheduleDate(), record.startTime()))
                .endTime(registeringToOffset(record.scheduleDate(), record.endTime()))
                .status(record.status())
                .amountCent(record.amountCent())
                .expireAt(record.expireAt())
                .build();
    }

    /** 将订单联表记录转换为详情响应。 */
    private RegisteringAppointmentDetailVO registeringToDetail(RegisteringAppointmentRecord record) {
        return RegisteringAppointmentDetailVO.builder()
                .id(record.id())
                .status(record.status())
                .doctor(RegisteringAppointmentDetailVO.Doctor.builder()
                        .id(record.doctorId())
                        .name(record.doctorName())
                        .departmentName(record.departmentName())
                        .departmentLocation(record.departmentLocation())
                        .build())
                .slot(RegisteringAppointmentDetailVO.Slot.builder()
                        .id(record.slotId())
                        .startTime(registeringToOffset(record.scheduleDate(), record.startTime()))
                        .endTime(registeringToOffset(record.scheduleDate(), record.endTime()))
                        .build())
                .amountCent(record.amountCent()).expireAt(record.expireAt())
                .payment(RegisteringAppointmentDetailVO.Payment.builder()
                        .id(record.paymentId())
                        .status(record.paymentStatus())
                        .build())
                .build();
    }

    /** 按业务时区组合排班日期和时段时间。 */
    private OffsetDateTime registeringToOffset(LocalDate date, java.time.LocalTime time) {
        return date.atTime(time).atZone(BUSINESS_ZONE_ID).toOffsetDateTime();
    }

    /**
     * 校验时段是否属于可预约的已发布排班且尚未结束。
     *
     * @param slot 时段锁号记录
     * @throws CAuthException 时段不存在、医院链路不匹配或不可预约时抛出
     */
    private void registeringValidateSlot(RegisteringSlotLockRecord slot) {
        if (slot == null) {
            throw new CAuthException(INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "号源时段不存在或已停用");
        }
        OffsetDateTime endAt = slot.scheduleDate().atTime(slot.endTime())
                .atZone(BUSINESS_ZONE_ID).toOffsetDateTime();
        // 只要尚未到时段结束时间即可挂号，开始时间不再作为预约截止边界。
        if (!"PUBLISHED".equals(slot.scheduleStatus()) || !"ENABLED".equals(slot.doctorStatus())
                || !endAt.isAfter(OffsetDateTime.now(BUSINESS_ZONE_ID))) {
            throw new CAuthException(ORDER_CLOSED_OR_STATUS_INVALID,
                    HttpStatus.CONFLICT, "当前时段不可预约");
        }
    }

    /**
     * 计算挂号支付单的实际截止时间。
     *
     * @param slot 已校验的时段信息
     * @param now 当前业务时间
     * @return 常规支付窗口与时段结束时间中较早的时刻
     */
    private OffsetDateTime registeringResolvePaymentExpireAt(RegisteringSlotLockRecord slot, OffsetDateTime now) {
        OffsetDateTime timeoutAt = now.plusSeconds(Math.max(registrationProperties.getPaymentTimeout(), 1));
        OffsetDateTime endAt = slot.scheduleDate().atTime(slot.endTime())
                .atZone(BUSINESS_ZONE_ID).toOffsetDateTime();
        // 已由时段校验保证结束时间在未来；此处保留最早截止点供支付与超时消息共同使用。
        return endAt.isBefore(timeoutAt) ? endAt : timeoutAt;
    }

    /**
     * 创建内部系统异常。
     *
     * @param message 面向客户端的提示信息
     * @return 系统异常
     */
    private CAuthException systemError(String message) {
        return new CAuthException(SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}

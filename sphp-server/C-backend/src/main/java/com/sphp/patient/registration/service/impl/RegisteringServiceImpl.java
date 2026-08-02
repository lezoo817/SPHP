package com.sphp.patient.registration.service.impl;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.common.enums.RegisteringAppointmentStatusEnum;
import com.sphp.patient.common.enums.RegisteringPaymentStatusEnum;
import com.sphp.patient.registration.config.RegistrationProperties;
import com.sphp.patient.registration.dto.RegisteringAppointmentCreateRequest;
import com.sphp.patient.registration.entity.RegisteringAppointment;
import com.sphp.patient.registration.entity.RegisteringPaymentOrder;
import com.sphp.patient.registration.mapper.RegisteringAppointmentMapper;
import com.sphp.patient.registration.mapper.RegisteringDataMapper;
import com.sphp.patient.registration.mapper.RegisteringPaymentOrderMapper;
import com.sphp.patient.registration.mapper.RegisteringSlotLockRecord;
import com.sphp.patient.registration.service.RegisteringService;
import com.sphp.patient.registration.support.RegisteringSlotLockService;
import com.sphp.patient.registration.vo.RegisteringAppointmentCreateVO;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;

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
    private final RegistrationProperties registrationProperties;

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

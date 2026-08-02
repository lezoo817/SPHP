package com.sphp.patient.registration.event;
import com.sphp.patient.registration.mapper.RegisteringAppointmentRecord;
import com.sphp.patient.registration.mapper.RegisteringDataMapper;
import com.sphp.patient.registration.support.RegisteringSlotLockService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
/** 挂号支付超时消费者。 */
@Component @RequiredArgsConstructor
public class RegisteringAppointmentTimeoutConsumer {
    private final RegisteringDataMapper dataMapper; private final RegisteringSlotLockService slotLockService;
    /** 条件取消仍未支付订单并释放号源，重复消息不产生重复补偿。 */
    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(queues = "cend.appointment.timeout.queue")
    public void registeringHandleTimeout(RegisteringAppointmentLockedEvent event) { RegisteringAppointmentRecord r = dataMapper.selectRegisteringAppointment(event.appointmentId()); if (r == null) return; OffsetDateTime now=OffsetDateTime.now(); if(dataMapper.registeringCancelUnpaidAppointment(r.id(),now)==1){dataMapper.registeringClosePendingPayment(r.id(),now);if(dataMapper.registeringReleaseLockedSnapshot(r.snapshotId(),now)==1)slotLockService.registeringUnlock(r.slotId());} }
}

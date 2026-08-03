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
    // 挂号数据访问接口
    private final RegisteringDataMapper dataMapper;
    // 挂号锁服务
    private final RegisteringSlotLockService slotLockService;

    /** 条件取消仍未支付订单并释放号源，重复消息不产生重复补偿。 */
    @Transactional(rollbackFor = Exception.class)
    @RabbitListener(queues = "cend.appointment.timeout.queue")
    public void registeringHandleTimeout(RegisteringAppointmentLockedEvent event) {

        RegisteringAppointmentRecord r = dataMapper.selectRegisteringAppointment(event.appointmentId());
        // 订单不存在
        if (r == null) return;

        OffsetDateTime now=OffsetDateTime.now();
        // 取消未支付订单
        if(dataMapper.registeringCancelUnpaidAppointment(r.id(),now)==1){
            dataMapper.registeringClosePendingPayment(r.id(),now);
            // 释放锁定的号源
            if(dataMapper.registeringReleaseLockedSnapshot(r.snapshotId(),now)==1)
                slotLockService.registeringUnlock(r.slotId());
        }
    }
}

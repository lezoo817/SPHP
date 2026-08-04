package com.sphp.patient.registration.event;

import com.sphp.patient.notification.mq.producer.NotificationEventProducer;
import com.sphp.patient.registration.mapper.RegisteringAppointmentRecord;
import com.sphp.patient.registration.mapper.RegisteringDataMapper;
import com.sphp.patient.registration.support.RegisteringSlotLockService;
import com.sphp.patient.registration.support.RegisteringWaitlistPromotionService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 挂号支付超时消费者测试。
 */
class RegisteringAppointmentTimeoutConsumerTest {

    /**
     * 验证超时订单只有真实释放号源后才晋级候补。
     */
    @Test
    void registeringHandleTimeoutPromotesWaitlistAfterSnapshotRelease() {
        RegisteringDataMapper dataMapper = mock(RegisteringDataMapper.class);
        RegisteringWaitlistPromotionService promotionService = mock(RegisteringWaitlistPromotionService.class);
        when(dataMapper.selectRegisteringAppointment(7001L)).thenReturn(record());
        when(dataMapper.registeringCancelUnpaidAppointment(eq(7001L), any())).thenReturn(1);
        when(dataMapper.registeringReleaseLockedSnapshot(eq(9001L), any())).thenReturn(1);
        RegisteringAppointmentTimeoutConsumer consumer = new RegisteringAppointmentTimeoutConsumer(dataMapper,
                mock(RegisteringSlotLockService.class), promotionService, mock(NotificationEventProducer.class));

        consumer.registeringHandleTimeout(RegisteringAppointmentLockedEvent.registeringOf(7001L, 10001L));

        verify(promotionService).registeringPromoteAfterSlotReleased(501L);
    }

    /**
     * 创建待支付挂号订单记录。
     *
     * @return 挂号订单投影
     */
    private RegisteringAppointmentRecord record() {
        return new RegisteringAppointmentRecord(7001L, 20001L, 9001L, 501L, 401L, "张医生", "呼吸内科",
                "门诊楼三层", LocalDate.now().plusDays(1), LocalTime.of(9, 0), LocalTime.of(9, 30),
                "UNPAID", 5000, OffsetDateTime.now().plusMinutes(15), 8001L, "PENDING");
    }
}

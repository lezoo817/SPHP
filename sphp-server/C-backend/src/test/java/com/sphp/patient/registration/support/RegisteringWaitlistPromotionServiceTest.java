package com.sphp.patient.registration.support;

import com.sphp.patient.common.enums.NotificationTypeEnum;
import com.sphp.patient.notification.mq.producer.NotificationEventProducer;
import com.sphp.patient.registration.config.RegistrationProperties;
import com.sphp.patient.registration.mapper.RegisteringDataMapper;
import com.sphp.patient.registration.mapper.RegisteringWaitlistCandidateRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 挂号候补晋级与过期处理服务测试。
 */
class RegisteringWaitlistPromotionServiceTest {

    /**
     * 验证号源释放后仅通知排队最靠前的候补人。
     */
    @Test
    void registeringPromoteAfterSlotReleasedNotifiesFirstWaitingCandidate() {
        RegisteringDataMapper dataMapper = mock(RegisteringDataMapper.class);
        NotificationEventProducer notificationProducer = mock(NotificationEventProducer.class);
        when(dataMapper.registeringLockWaitlistPromotionSlot(eq(501L), any())).thenReturn(501L);
        when(dataMapper.countRegisteringRebookableSnapshots(501L)).thenReturn(1L);
        when(dataMapper.countRegisteringNotifiedWaitlists(501L)).thenReturn(0L);
        when(dataMapper.registeringLockNextWaitingWaitlist(501L)).thenReturn(candidate());
        when(dataMapper.registeringNotifyWaitlist(eq(6001L), any())).thenReturn(1);

        service(dataMapper, notificationProducer).registeringPromoteAfterSlotReleased(501L);

        verify(dataMapper).registeringNotifyWaitlist(eq(6001L), any());
        verify(notificationProducer).publishNotification(eq("APPOINTMENT_WAITLIST_NOTIFIED"), eq(6001L),
                eq(10001L), eq(20001L), eq(NotificationTypeEnum.APPOINTMENT),
                eq("候补号源可预约"), any());
    }

    /**
     * 验证没有多余可预约号源时不重复通知候补。
     */
    @Test
    void registeringPromoteAfterSlotReleasedSkipsWhenNotificationQuotaIsFull() {
        RegisteringDataMapper dataMapper = mock(RegisteringDataMapper.class);
        when(dataMapper.registeringLockWaitlistPromotionSlot(eq(501L), any())).thenReturn(501L);
        when(dataMapper.countRegisteringRebookableSnapshots(501L)).thenReturn(1L);
        when(dataMapper.countRegisteringNotifiedWaitlists(501L)).thenReturn(1L);

        service(dataMapper, mock(NotificationEventProducer.class)).registeringPromoteAfterSlotReleased(501L);

        verify(dataMapper, never()).registeringLockNextWaitingWaitlist(any());
    }

    /**
     * 验证已通知候补过期后，在仍有余量时晋级下一位候补。
     */
    @Test
    void registeringExpireDueWaitlistsPromotesNextCandidate() {
        RegisteringDataMapper dataMapper = mock(RegisteringDataMapper.class);
        NotificationEventProducer notificationProducer = mock(NotificationEventProducer.class);
        when(dataMapper.selectRegisteringExpiredNotifiedWaitlists(any())).thenReturn(List.of(candidate()));
        when(dataMapper.registeringExpireNotifiedWaitlist(eq(6001L), any(), any())).thenReturn(1);
        when(dataMapper.registeringLockWaitlistPromotionSlot(eq(501L), any())).thenReturn(501L);
        when(dataMapper.countRegisteringRebookableSnapshots(501L)).thenReturn(1L);
        when(dataMapper.countRegisteringNotifiedWaitlists(501L)).thenReturn(0L);
        when(dataMapper.registeringLockNextWaitingWaitlist(501L)).thenReturn(candidate(6002L, 10002L, 2));
        when(dataMapper.registeringNotifyWaitlist(eq(6002L), any())).thenReturn(1);

        service(dataMapper, notificationProducer).registeringExpireDueWaitlists();

        verify(dataMapper).registeringExpireNotifiedWaitlist(eq(6001L), any(), any());
        verify(dataMapper).registeringNotifyWaitlist(eq(6002L), any());
        verify(dataMapper).registeringExpireStartedWaitlists(any());
    }

    /**
     * 创建候补晋级服务测试实例。
     *
     * @param dataMapper 挂号数据访问接口
     * @param notificationProducer 通知生产器
     * @return 候补晋级服务
     */
    private RegisteringWaitlistPromotionService service(RegisteringDataMapper dataMapper,
                                                         NotificationEventProducer notificationProducer) {
        RegistrationProperties properties = new RegistrationProperties();
        properties.setWaitlistNotifyTimeout(900);
        return new RegisteringWaitlistPromotionService(dataMapper, properties, notificationProducer);
    }

    /**
     * 创建第一位候补记录。
     *
     * @return 候补记录投影
     */
    private RegisteringWaitlistCandidateRecord candidate() {
        return candidate(6001L, 10001L, 1);
    }

    /**
     * 创建指定排队号的候补记录。
     *
     * @param id 候补记录 ID
     * @param userId 登记账号 ID
     * @param queueNo 排队号
     * @return 候补记录投影
     */
    private RegisteringWaitlistCandidateRecord candidate(Long id, Long userId, int queueNo) {
        return new RegisteringWaitlistCandidateRecord(id, userId, 20001L, 501L, queueNo);
    }
}

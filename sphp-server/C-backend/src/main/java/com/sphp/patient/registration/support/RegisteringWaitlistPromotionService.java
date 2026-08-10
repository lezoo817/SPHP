package com.sphp.patient.registration.support;

import com.sphp.patient.common.enums.NotificationTypeEnum;
import com.sphp.patient.registration.config.RegistrationProperties;
import com.sphp.patient.registration.mapper.RegisteringDataMapper;
import com.sphp.patient.registration.mapper.RegisteringWaitlistCandidateRecord;
import com.sphp.patient.notification.mq.producer.NotificationEventProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static com.sphp.patient.common.enums.NotificationTypeEnum.APPOINTMENT;

/**
 * C端挂号候补通知与过期处理服务。
 */
@Service
@RequiredArgsConstructor
public class RegisteringWaitlistPromotionService {
    // 候补及号源数据访问接口
    private final RegisteringDataMapper dataMapper;
    // 候补通知时限配置
    private final RegistrationProperties registrationProperties;
    // 事务后站内通知生产器
    private final NotificationEventProducer notificationEventProducer;

    /**
     * 在一个号源快照真实释放后尝试通知下一位候补。
     *
     * @param slotId 已释放快照所属的时段 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void registeringPromoteAfterSlotReleased(Long slotId) {
        OffsetDateTime now = OffsetDateTime.now();
        // 通过时段行锁串行化同一时段的并发释放和候补晋级。
        if (dataMapper.registeringLockWaitlistPromotionSlot(slotId, now) == null) {
            return;
        }
        // 尝试通知下一位候补
        registeringNotifyNextWaitlist(slotId, now);
    }

    /**
     * 扫描通知超时及已结束时段的候补，并在有余量时继续通知下一位。
     */
    @Transactional(rollbackFor = Exception.class)
    public void registeringExpireDueWaitlists() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime deadline = now.minusSeconds(Math.max(registrationProperties.getWaitlistNotifyTimeout(), 1));
        // 已结束时段的候补没有预约价值，必须先结束以避免扫描过程发出无效通知。
        dataMapper.registeringExpireStartedWaitlists(now);
        List<RegisteringWaitlistCandidateRecord> expiredWaitlists =
                dataMapper.selectRegisteringExpiredNotifiedWaitlists(deadline);
        for (RegisteringWaitlistCandidateRecord waitlist : expiredWaitlists) {
            // 条件更新防止扫描任务与用户下单并发时重复处理同一候补。
            if (dataMapper.registeringExpireNotifiedWaitlist(waitlist.id(), deadline, now) == 1) {
                registeringPromoteAfterSlotReleased(waitlist.slotId());
            }
        }
    }

    /**
     * 当前用户成功锁定候补时段后，将其已通知候补标记为已履约。
     *
     * @param userId 当前 C 端用户 ID
     * @param patientId 当前订单所属就诊人 ID
     * @param slotId 当前订单所属时段 ID
     * @param now 当前时间
     */
    public void registeringFulfillNotifiedWaitlist(Long userId, Long patientId, Long slotId, OffsetDateTime now) {
        // 不影响普通挂号；仅命中同账号、同就诊人、同时段的已通知候补。
        dataMapper.registeringFulfillNotifiedWaitlist(userId, patientId, slotId, now);
    }

    /**
     * 在可预约余量多于已通知候补时，通知排队最靠前的一位候补。
     *
     * @param slotId 时段 ID
     * @param now 当前时间
     */
    private void registeringNotifyNextWaitlist(Long slotId, OffsetDateTime now) {
        // 计算可预约快照数量与已通知候补数量
        long rebookableCount = dataMapper.countRegisteringRebookableSnapshots(slotId);
        // 计算已通知候补数量
        long notifiedCount = dataMapper.countRegisteringNotifiedWaitlists(slotId);
        // 通知数量不超过可预约快照，避免向多个候补发出无法兑现的提醒。
        if (rebookableCount <= notifiedCount) {
            return;
        }
        //锁定当前时段排队最靠前的待通知候补
        RegisteringWaitlistCandidateRecord waitlist = dataMapper.registeringLockNextWaitingWaitlist(slotId);
        if (waitlist == null || dataMapper.registeringNotifyWaitlist(waitlist.id(), now) != 1) {
            return;
        }
        // 站内通知经事务后事件投递，避免数据库回滚后误告知候补人。
        notificationEventProducer.publishNotification(
                "APPOINTMENT_WAITLIST_NOTIFIED",
                waitlist.id(),
                waitlist.userId(),
                waitlist.patientId(),
                APPOINTMENT,
                "候补号源可预约",
                "已有可用号源，请在当前时段结束前完成预约。号源不保留，建议尽快操作。"
        );
    }
}

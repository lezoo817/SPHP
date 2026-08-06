package com.sphp.patient.registration.event;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 挂号锁定后用于支付超时检查的领域事件。 */
public record RegisteringAppointmentLockedEvent(String eventId, Long appointmentId, Long userId,
                                                OffsetDateTime occurredAt, OffsetDateTime expireAt) implements Serializable {

    /**
     * 创建带实际支付截止时间的锁号事件。
     *
     * @param appointmentId 挂号订单 ID
     * @param userId 付款 C 端用户 ID
     * @param expireAt 支付截止时间
     * @return 挂号锁定事件
     */
    public static RegisteringAppointmentLockedEvent registeringOf(Long appointmentId, Long userId,
                                                                   OffsetDateTime expireAt) {
        return new RegisteringAppointmentLockedEvent(
                UUID.randomUUID().toString(), // 事件 ID
                appointmentId,  // 挂号订单 ID
                userId, // 用户 ID
                OffsetDateTime.now(),
                expireAt
        );
    }

    /**
     * 创建使用队列默认支付超时的兼容锁号事件。
     *
     * @param appointmentId 挂号订单 ID
     * @param userId 付款 C 端用户 ID
     * @return 不指定单消息截止时间的挂号锁定事件
     */
    public static RegisteringAppointmentLockedEvent registeringOf(Long appointmentId, Long userId) {
        return registeringOf(appointmentId, userId, null);
    }
}

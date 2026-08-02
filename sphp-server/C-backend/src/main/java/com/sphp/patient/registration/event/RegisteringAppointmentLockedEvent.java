package com.sphp.patient.registration.event;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;
/** 挂号锁定后用于支付超时检查的领域事件。 */
public record RegisteringAppointmentLockedEvent(String eventId, Long appointmentId, Long userId, OffsetDateTime occurredAt) implements Serializable {
    /** 创建新的挂号锁定事件。 */
    public static RegisteringAppointmentLockedEvent registeringOf(Long appointmentId, Long userId) { return new RegisteringAppointmentLockedEvent(UUID.randomUUID().toString(), appointmentId, userId, OffsetDateTime.now()); }
}

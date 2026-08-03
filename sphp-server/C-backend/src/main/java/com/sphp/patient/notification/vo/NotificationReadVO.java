package com.sphp.patient.notification.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * C端站内通知已读响应对象。
 */
@Getter
@Builder
public class NotificationReadVO {

    /** 通知 ID */
    private final Long id;
    /** 固定为 true 的已读标记 */
    private final boolean read;
    /** 首次已读时间 */
    private final OffsetDateTime readAt;
}

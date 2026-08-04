package com.sphp.patient.notification.mapper;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * C端站内通知查询投影。
 */
@Getter
@Setter
public class NotificationRecord {

    /** 通知 ID */
    private Long id;

    /** 接收用户 ID，仅用于归属校验 */
    private Long userId;

    /** 关联就诊人 ID */
    private Long patientId;

    /** 就诊人名称快照 */
    private String patientName;

    /** 通知类型 */
    private String type;

    /** 通知标题 */
    private String title;

    /** 通知正文 */
    private String content;

    /** 首次已读时间 */
    private OffsetDateTime readAt;

    /** 创建时间 */
    private OffsetDateTime createdAt;
}

package com.sphp.patient.notification.mapper;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/** 到期用药或随访提醒的通知投影。 */
@Getter
@Setter
public class NotificationReminderRecord {
    /** 计划业务 ID */
    private Long businessId;
    /** 通知接收用户 ID */
    private Long userId;
    /** 关联就诊人 ID */
    private Long patientId;
    /** 就诊人名称快照 */
    private String patientName;
    /** 计划提醒时间 */
    private OffsetDateTime dueAt;
}

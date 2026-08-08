package com.sphp.patient.notification.mapper;

import lombok.Getter;
import lombok.Setter;

/**
 * 在线问诊回复通知数据库投影。
 */
@Getter
@Setter
public class OnlineConsultationNotificationRecord {

    /** 通知接收的 C 端用户 ID。 */
    private Long userId;

    /** 患者 ID。 */
    private Long patientId;

    /** 患者姓名快照。 */
    private String patientName;

    /** 医生回复正文。 */
    private String content;
}

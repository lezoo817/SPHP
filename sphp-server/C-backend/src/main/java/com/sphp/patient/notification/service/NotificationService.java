package com.sphp.patient.notification.service;

import com.sphp.patient.notification.vo.NotificationPageVO;
import com.sphp.patient.notification.vo.NotificationReadVO;

/**
 * C端站内通知服务。
 */
public interface NotificationService {

    /**
     * 分页查询当前账号通知。
     *
     * @param patientId 可选就诊人 ID
     * @param read 可选已读状态
     * @param type 可选通知类型
     * @param pageNo 页号
     * @param pageSize 页大小
     * @return 通知分页数据
     */
    NotificationPageVO listNotifications(Long patientId, Boolean read, String type, Integer pageNo, Integer pageSize);

    /**
     * 标记当前账号的一条通知已读。
     *
     * @param notificationId 通知 ID
     * @return 已读结果
     */
    NotificationReadVO markNotificationRead(Long notificationId);
}

package com.sphp.patient.notification.service;

import com.sphp.patient.notification.vo.NotificationPageVO;

/**
 * C端站内通知服务。
 */
public interface NotificationService {

    /**
     * 分页查询当前账号通知。
     *
     * @param patientId 可选就诊人 ID
     * @param read 可选已读状态
     * @param pageNo 页号
     * @param pageSize 页大小
     * @return 通知分页数据
     */
    NotificationPageVO listNotifications(Long patientId, Boolean read, Integer pageNo, Integer pageSize);
}

package com.sphp.patient.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.patient.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * C端站内通知数据访问接口。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    /**
     * 判断就诊人是否存在且未软删除。
     *
     * @param patientId 就诊人 ID
     * @return 存在时返回 true
     */
    boolean existsActivePatient(@Param("patientId") Long patientId);

    /**
     * 判断当前账号是否拥有有效就诊人关系。
     *
     * @param userId C端用户 ID
     * @param patientId 就诊人 ID
     * @return 存在有效关系时返回 true
     */
    boolean hasActivePatientRelation(@Param("userId") Long userId, @Param("patientId") Long patientId);

    /**
     * 查询未软删除就诊人的名称快照。
     *
     * @param patientId 就诊人 ID
     * @return 就诊人名称，不存在时返回 null
     */
    String selectActivePatientName(@Param("patientId") Long patientId);

    /**
     * 查询尚未生成通知的到期用药计划及有效接收账号。
     *
     * @param now 当前时间
     * @return 到期用药提醒投影
     */
    List<NotificationReminderRecord> selectDueMedicationReminders(@Param("now") OffsetDateTime now);

    /**
     * 查询尚未生成通知的到期随访计划及有效接收账号。
     *
     * @param now 当前时间
     * @return 到期随访提醒投影
     */
    List<NotificationReminderRecord> selectDueFollowUpReminders(@Param("now") OffsetDateTime now);

    /**
     * 分页查询当前账号的未删除通知。
     *
     * @param userId C端用户 ID
     * @param patientId 可选就诊人 ID
     * @param read 可选已读状态
     * @param limit 分页大小
     * @param offset 分页偏移量
     * @return 通知投影列表
     */
    List<NotificationRecord> selectNotifications(@Param("userId") Long userId,
                                                 @Param("patientId") Long patientId,
                                                 @Param("read") Boolean read,
                                                 @Param("limit") int limit,
                                                 @Param("offset") long offset);

    /**
     * 统计当前账号满足条件的通知数量。
     *
     * @param userId C端用户 ID
     * @param patientId 可选就诊人 ID
     * @param read 可选已读状态
     * @return 通知数量
     */
    long countNotifications(@Param("userId") Long userId, @Param("patientId") Long patientId,
                            @Param("read") Boolean read);

    /**
     * 按通知 ID 查询通知归属与已读状态。
     *
     * @param notificationId 通知 ID
     * @return 通知投影，不存在或已删除时返回 null
     */
    NotificationRecord selectNotification(@Param("notificationId") Long notificationId);

    /**
     * 条件标记当前账号的一条未读通知为已读。
     *
     * @param notificationId 通知 ID
     * @param userId C端用户 ID
     * @param readAt 首次已读时间
     * @return 实际更新行数
     */
    int markNotificationRead(@Param("notificationId") Long notificationId, @Param("userId") Long userId,
                             @Param("readAt") OffsetDateTime readAt);

    /**
     * 幂等插入一条 RabbitMQ 驱动的站内通知。
     *
     * @param notification 待写入通知
     * @return 实际插入行数，重复事件时为 0
     */
    int insertNotificationIfAbsent(Notification notification);

}

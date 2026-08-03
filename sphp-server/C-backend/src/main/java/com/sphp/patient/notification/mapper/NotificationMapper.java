package com.sphp.patient.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.patient.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

}

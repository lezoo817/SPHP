package com.sphp.admin.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.admin.schedule.entity.Schedule;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 排班表 Mapper。
 */
public interface ScheduleMapper extends BaseMapper<Schedule> {

    /**
     * 统计排班下状态为 PAID 且排班日期 >= 今天的有效挂号订单数。
     *
     * <p>取消发布前置校验：经 appointment.slot_snapshot_id → slot_snapshot.slot_id → slot.schedule_id
     * 关联判定，避免已挂号患者被动爽约（存在则禁止取消发布，返回 A0443）。
     *
     * @param scheduleId 排班 ID
     * @return 未来有效 PAID 订单数
     */
    @Select("SELECT COUNT(*) FROM appointment a " +
            "JOIN slot_snapshot ss ON a.slot_snapshot_id = ss.id AND ss.deleted_at IS NULL " +
            "JOIN slot s ON ss.slot_id = s.id AND s.deleted_at IS NULL " +
            "JOIN schedule sch ON s.schedule_id = sch.id AND sch.deleted_at IS NULL " +
            "WHERE sch.id = #{scheduleId} " +
            "  AND a.status = 'PAID' AND a.deleted_at IS NULL " +
            "  AND sch.schedule_date >= CURRENT_DATE")
    long countPaidFutureAppointments(@Param("scheduleId") Long scheduleId);
}

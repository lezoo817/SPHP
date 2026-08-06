package com.sphp.admin.schedule.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 过期 PAID 自动 NO_SHOW 标记 Mapper。
 */
@Mapper
public interface AppointmentNoShowMapper {

    /**
     * 批量将"slot 已结束超过宽限期且无 COMPLETED/NO_SHOW/IN_PROGRESS/DRAFT 就诊记录"的
     * PAID 预约自动标记为 NO_SHOW，避免过期 PAID 字面量长期阻塞排班取消发布与前端号源统计。
     *
     * <p>与 C 端 {@code effectiveAppointmentStatus} 同源：仅当 consult_record 为 NULL
     * 或 status=PENDING（视为未真正开始就诊）时才标记；医生已开始 DRAFT/IN_PROGRESS
     * 或已 COMPLETED/NO_SHOW 的记录保持不动，由医生主动完结。
     *
     * @param graceMinutes slot 结束后的宽限期（分钟），仅当 now - end_time >= graceMinutes 才标记
     * @return 受影响行数
     */
    @Update("UPDATE appointment " +
            "SET status = 'NO_SHOW', updated_at = now() " +
            "WHERE status = 'PAID' AND deleted_at IS NULL " +
            "  AND slot_snapshot_id IN ( " +
            "    SELECT ss.id FROM slot_snapshot ss " +
            "    JOIN slot s ON s.id = ss.slot_id " +
            "    WHERE (s.schedule_date + s.end_time) AT TIME ZONE 'Asia/Shanghai' " +
            "          <= (CURRENT_TIMESTAMP - (INTERVAL '1 minute' * #{graceMinutes})) " +
            "  ) " +
            "  AND NOT EXISTS ( " +
            "    SELECT 1 FROM consult_record cr " +
            "    WHERE cr.appointment_id = appointment.id " +
            "      AND cr.deleted_at IS NULL " +
            "      AND cr.status <> 'PENDING' " +
            "  )")
    int markOverduePaidAsNoShow(@Param("graceMinutes") int graceMinutes);
}

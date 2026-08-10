package com.sphp.admin.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.admin.schedule.entity.Schedule;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 排班表 Mapper。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
public interface ScheduleMapper extends BaseMapper<Schedule> {

    /**
     * 统计排班下"有效状态 = PAID"的挂号订单数（取消发布前置校验）。
     *
     * <p>有效状态判定复用 C 端 {@code effectiveAppointmentStatus} 同款 CASE：
     * <ul>
     *     <li>{@code a.status != 'PAID'} → 取 a.status（非阻塞）</li>
     *     <li>{@code cr.status = 'COMPLETED'} → COMPLETED（非阻塞）</li>
     *     <li>{@code cr.status = 'NO_SHOW'} → NO_SHOW（非阻塞）</li>
     *     <li>slot 已结束且无 COMPLETED/NO_SHOW 就诊记录 → NO_SHOW（非阻塞）</li>
     *     <li>其余（PAID + slot 未到 / cr = DRAFT / cr = IN_PROGRESS）→ PAID（阻塞）</li>
     * </ul>
     * 避免过期 PAID 字面量长期阻塞 unpublish；consult_record(appointment_id) 唯一保证 1:1，无需 DISTINCT。
     *
     * @param scheduleId 排班 ID
     * @return 阻塞 unpublish 的 PAID 订单数
     */
    @Select("SELECT COUNT(*) FROM (" +
            "  SELECT " +
            "    CASE " +
            "      WHEN a.status <> 'PAID' THEN a.status " +
            "      WHEN cr.status = 'COMPLETED' THEN 'COMPLETED' " +
            "      WHEN cr.status = 'NO_SHOW' THEN 'NO_SHOW' " +
            "      WHEN (sch.schedule_date + s.end_time) AT TIME ZONE 'Asia/Shanghai' <= CURRENT_TIMESTAMP " +
            "           AND (cr.id IS NULL OR cr.status = 'PENDING') THEN 'NO_SHOW' " +
            "      ELSE a.status " +
            "    END AS effective_status " +
            "  FROM appointment a " +
            "  JOIN slot_snapshot ss ON a.slot_snapshot_id = ss.id AND ss.deleted_at IS NULL " +
            "  JOIN slot s ON ss.slot_id = s.id AND s.deleted_at IS NULL " +
            "  JOIN schedule sch ON s.schedule_id = sch.id AND sch.deleted_at IS NULL " +
            "  LEFT JOIN consult_record cr ON cr.appointment_id = a.id AND cr.deleted_at IS NULL " +
            "  WHERE sch.id = #{scheduleId} " +
            "    AND a.deleted_at IS NULL " +
            "    AND sch.schedule_date >= CURRENT_DATE" +
            ") sub " +
            "WHERE sub.effective_status = 'PAID'")
    long countPaidFutureAppointments(@Param("scheduleId") Long scheduleId);
}

package com.sphp.admin.doctor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 挂号订单表 Mapper（B端仅需更新状态）。
 *
 * <p>appointment 表由 C 端维护，B 端只在结束接诊时同步状态，
 * 以及过期未就诊时标记为 EXPIRED。
 * 不创建完整实体，直接通过 SQL 更新，避免与 C 端 {@code RegisteringAppointment} 耦合。
 */
@Mapper
public interface BAppointmentMapper {

    /**
     * 将挂号订单状态更新为 COMPLETED。
     *
     * <p>幂等操作：已 COMPLETED 的订单再次执行仅更新 updated_at。
     *
     * @param appointmentId 挂号订单 ID
     * @param now           当前时间（用于 updated_at）
     * @return 影响行数
     */
    @Update("UPDATE appointment SET status = 'COMPLETED', updated_at = #{now} WHERE id = #{appointmentId} AND deleted_at IS NULL")
    int completeStatus(@Param("appointmentId") Long appointmentId, @Param("now") OffsetDateTime now);

    /**
     * 批量过期未就诊的挂号订单。
     *
     * <p>将指定医生列表中，排班日期早于今天且 status = PAID 的挂号订单标记为 EXPIRED。
     * 与 {@code batchExpireOldPending} 配合使用，保持两端状态一致。
     *
     * @param doctorIds 医生 ID 列表
     * @param now       当前时间（用于 updated_at）
     * @return 影响行数
     */
    @Update("<script>" +
            "UPDATE appointment a SET status = 'EXPIRED', updated_at = #{now} " +
            "WHERE a.deleted_at IS NULL " +
            "  AND a.status = 'PAID' " +
            "  AND a.doctor_id IN " +
            "  <foreach item='did' collection='doctorIds' open='(' separator=',' close=')'>#{did}</foreach> " +
            "  AND NOT EXISTS (" +
            "    SELECT 1 FROM consult_record cr " +
            "    WHERE cr.appointment_id = a.id AND cr.deleted_at IS NULL AND cr.status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED')" +
            "  )" +
            "  AND EXISTS (" +
            "    SELECT 1 FROM slot_snapshot ss " +
            "    JOIN slot s ON ss.slot_id = s.id AND s.deleted_at IS NULL " +
            "    JOIN schedule sch ON s.schedule_id = sch.id AND sch.deleted_at IS NULL " +
            "    WHERE a.slot_snapshot_id = ss.id AND ss.deleted_at IS NULL " +
            "      AND sch.schedule_date &lt; CURRENT_DATE" +
            "  )" +
            "</script>")
    int batchExpireOldPaid(@Param("doctorIds") List<Long> doctorIds, @Param("now") OffsetDateTime now);
}
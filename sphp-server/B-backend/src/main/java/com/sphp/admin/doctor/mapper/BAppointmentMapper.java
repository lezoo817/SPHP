package com.sphp.admin.doctor.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.OffsetDateTime;

/**
 * 挂号订单表 Mapper（B端仅需更新状态）。
 *
 * <p>appointment 表由 C 端维护，B 端只在结束接诊时同步状态。
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
}
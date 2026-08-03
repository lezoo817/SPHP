package com.sphp.admin.statistics.mapper;

import com.sphp.admin.statistics.vo.DailyStatVO;
import com.sphp.admin.statistics.vo.DepartmentStatVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 统计报表 Mapper，使用原生 SQL 进行复杂聚合查询。
 */
public interface StatisticsMapper {

    /**
     * 统计指定医院在指定时间范围内的总挂号量。
     */
    @Select("SELECT COUNT(*) FROM appointment a "
            + "JOIN doctor d ON a.doctor_id = d.id "
            + "WHERE d.hospital_id = #{hospitalId} "
            + "AND a.created_at >= #{startDate}::date "
            + "AND a.created_at < #{endDate}::date + INTERVAL '1 day' "
            + "AND a.deleted_at IS NULL")
    long countAppointments(@Param("hospitalId") Long hospitalId,
                           @Param("startDate") String startDate,
                           @Param("endDate") String endDate);

    /**
     * 统计指定医院在指定时间范围内已完成的问诊数。
     */
    @Select("SELECT COUNT(*) FROM consult_record cr "
            + "JOIN doctor d ON cr.doctor_id = d.id "
            + "WHERE d.hospital_id = #{hospitalId} "
            + "AND cr.status = 'COMPLETED' "
            + "AND cr.created_at >= #{startDate}::date "
            + "AND cr.created_at < #{endDate}::date + INTERVAL '1 day' "
            + "AND cr.deleted_at IS NULL")
    long countCompletedConsults(@Param("hospitalId") Long hospitalId,
                                @Param("startDate") String startDate,
                                @Param("endDate") String endDate);

    /**
     * 统计指定医院在指定时间范围内已支付/完成的挂号收入总和（单位：分）。
     */
    @Select("SELECT COALESCE(SUM(a.amount_cent), 0) FROM appointment a "
            + "JOIN doctor d ON a.doctor_id = d.id "
            + "WHERE d.hospital_id = #{hospitalId} "
            + "AND a.status IN ('PAID', 'COMPLETED') "
            + "AND a.paid_at >= #{startDate}::date "
            + "AND a.paid_at < #{endDate}::date + INTERVAL '1 day' "
            + "AND a.deleted_at IS NULL")
    long sumRevenueCent(@Param("hospitalId") Long hospitalId,
                        @Param("startDate") String startDate,
                        @Param("endDate") String endDate);

    /**
     * 统计指定医院在指定时间范围内已提交的处方数（排除草稿）。
     */
    @Select("SELECT COUNT(*) FROM prescription p "
            + "JOIN doctor d ON p.doctor_id = d.id "
            + "WHERE d.hospital_id = #{hospitalId} "
            + "AND p.status != 'DRAFT' "
            + "AND p.created_at >= #{startDate}::date "
            + "AND p.created_at < #{endDate}::date + INTERVAL '1 day' "
            + "AND p.deleted_at IS NULL")
    long countPrescriptions(@Param("hospitalId") Long hospitalId,
                            @Param("startDate") String startDate,
                            @Param("endDate") String endDate);

    /**
     * 计算指定医院在指定时间范围内的平均等待时间（分钟）。
     *
     * <p>等待时间 = consult_record.started_at - appointment.paid_at。
     */
    @Select("SELECT COALESCE(AVG(EXTRACT(EPOCH FROM (cr.started_at - a.paid_at)) / 60), 0) "
            + "FROM consult_record cr "
            + "JOIN appointment a ON cr.appointment_id = a.id "
            + "JOIN doctor d ON cr.doctor_id = d.id "
            + "WHERE d.hospital_id = #{hospitalId} "
            + "AND cr.started_at IS NOT NULL "
            + "AND a.paid_at IS NOT NULL "
            + "AND cr.created_at >= #{startDate}::date "
            + "AND cr.created_at < #{endDate}::date + INTERVAL '1 day' "
            + "AND cr.deleted_at IS NULL")
    double avgWaitTimeMinutes(@Param("hospitalId") Long hospitalId,
                              @Param("startDate") String startDate,
                              @Param("endDate") String endDate);

    /**
     * 按科室统计（系分 §5.9.2）。
     */
    @Select("<script>"
            + "SELECT "
            + "  d.dept_id AS dept_id, "
            + "  dep.name AS dept_name, "
            + "  COUNT(DISTINCT a.id) AS appointment_count, "
            + "  COUNT(DISTINCT cr.id) AS consult_count, "
            + "  COUNT(DISTINCT p.id) AS prescription_count, "
            + "  COALESCE(sur.usage_rate, 0) AS slot_usage_rate "
            + "FROM doctor d "
            + "JOIN department dep ON dep.id = d.dept_id "
            + "LEFT JOIN appointment a ON a.doctor_id = d.id AND a.deleted_at IS NULL "
            + "  AND a.created_at &gt;= #{startDate}::date "
            + "  AND a.created_at &lt; #{endDate}::date + INTERVAL '1 day' "
            + "LEFT JOIN consult_record cr ON cr.doctor_id = d.id AND cr.deleted_at IS NULL "
            + "  AND cr.created_at &gt;= #{startDate}::date "
            + "  AND cr.created_at &lt; #{endDate}::date + INTERVAL '1 day' "
            + "LEFT JOIN prescription p ON p.doctor_id = d.id AND p.deleted_at IS NULL AND p.status != 'DRAFT' "
            + "  AND p.created_at &gt;= #{startDate}::date "
            + "  AND p.created_at &lt; #{endDate}::date + INTERVAL '1 day' "
            + "LEFT JOIN ( "
            + "  SELECT s.dept_id, "
            + "    SUM(sl.total_count - sl.remain_count)::float / NULLIF(SUM(sl.total_count), 0) AS usage_rate "
            + "  FROM schedule s "
            + "  JOIN slot sl ON sl.schedule_id = s.id AND sl.deleted_at IS NULL "
            + "  WHERE s.schedule_date &gt;= #{startDate}::date "
            + "    AND s.schedule_date &lt;= #{endDate}::date "
            + "    AND s.status = 'PUBLISHED' "
            + "    AND s.deleted_at IS NULL "
            + "  GROUP BY s.dept_id "
            + ") sur ON sur.dept_id = d.dept_id "
            + "WHERE d.hospital_id = #{hospitalId} "
            + "  AND d.deleted_at IS NULL "
            + "  AND dep.deleted_at IS NULL "
            + "<if test='deptId != null'> AND d.dept_id = #{deptId} </if>"
            + "GROUP BY d.dept_id, dep.name, sur.usage_rate "
            + "ORDER BY appointment_count DESC"
            + "</script>")
    List<DepartmentStatVO> selectDepartmentStats(@Param("hospitalId") Long hospitalId,
                                                  @Param("startDate") String startDate,
                                                  @Param("endDate") String endDate,
                                                  @Param("deptId") Long deptId);

    /**
     * 按日期统计（系分 §5.9.3）。
     */
    @Select("SELECT "
            + "  a.created_at::date AS date, "
            + "  COUNT(DISTINCT a.id) AS appointment_count, "
            + "  COUNT(DISTINCT cr.id) AS consult_count, "
            + "  COUNT(DISTINCT p.id) AS prescription_count, "
            + "  COALESCE(SUM(a.amount_cent) FILTER (WHERE a.status IN ('PAID', 'COMPLETED')), 0) AS revenue_cent "
            + "FROM appointment a "
            + "JOIN doctor d ON a.doctor_id = d.id "
            + "LEFT JOIN consult_record cr ON cr.appointment_id = a.id AND cr.deleted_at IS NULL "
            + "LEFT JOIN prescription p ON p.consult_id = cr.id AND p.deleted_at IS NULL AND p.status != 'DRAFT' "
            + "WHERE d.hospital_id = #{hospitalId} "
            + "  AND a.created_at >= #{startDate}::date "
            + "  AND a.created_at < #{endDate}::date + INTERVAL '1 day' "
            + "  AND a.deleted_at IS NULL "
            + "GROUP BY a.created_at::date "
            + "ORDER BY a.created_at::date")
    List<DailyStatVO> selectDailyStats(@Param("hospitalId") Long hospitalId,
                                       @Param("startDate") String startDate,
                                       @Param("endDate") String endDate);
}
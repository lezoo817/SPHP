package com.sphp.admin.doctor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sphp.admin.doctor.entity.ConsultRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 问诊记录表 Mapper。
 */
public interface ConsultRecordMapper extends BaseMapper<ConsultRecord> {

    /**
     * 分页查询待接诊列表（含排队号、患者信息、挂号时间）。
     *
     * <p>排队号根据同医生同排班日期的挂号时间排序生成。
     * 数据权限在 Service 层通过 doctorIds/deptId 参数控制。
     * doctorIds 为空时返回空数据。
     */
    @Select("<script>" +
            "SELECT cr.id AS consultId, cr.patient_id AS patientId, " +
            "       p.name AS patientName, p.gender AS patientGender, " +
            "       p.date_of_birth AS patientDateOfBirth, " +
            "       cr.ai_summary AS aiSummary, " +
            "       a.created_at AS appointmentTime, cr.status AS status, " +
            "       s.start_time AS slotStartTime, s.end_time AS slotEndTime, " +
            "       ROW_NUMBER() OVER (PARTITION BY cr.doctor_id, sch.schedule_date ORDER BY a.created_at) AS queueNumber " +
            "FROM consult_record cr " +
            "JOIN appointment a ON cr.appointment_id = a.id AND a.deleted_at IS NULL " +
            "JOIN slot_snapshot ss ON a.slot_snapshot_id = ss.id AND ss.deleted_at IS NULL " +
            "JOIN slot s ON ss.slot_id = s.id AND s.deleted_at IS NULL " +
            "JOIN schedule sch ON s.schedule_id = sch.id AND sch.deleted_at IS NULL " +
            "JOIN patient p ON cr.patient_id = p.id AND p.deleted_at IS NULL " +
            "WHERE cr.deleted_at IS NULL " +
            "  AND cr.status = #{status} " +
            "  AND cr.doctor_id IN " +
            "  <foreach item='did' collection='doctorIds' open='(' separator=',' close=')'>#{did}</foreach> " +
            "<if test='deptId != null'> AND sch.dept_id = #{deptId} </if>" +
            "  AND sch.schedule_date = CURRENT_DATE " +
            "ORDER BY a.created_at ASC" +
            "</script>")
    IPage<QueueRow> selectQueuePage(Page<QueueRow> page,
                                    @Param("status") String status,
                                    @Param("doctorIds") List<Long> doctorIds,
                                    @Param("deptId") Long deptId);

    /**
     * 查询指定医生当日所有 PENDING 问诊记录（用于统计科室待接诊队列）。
     */
    @Select("SELECT cr.id FROM consult_record cr " +
            "JOIN appointment a ON cr.appointment_id = a.id AND a.deleted_at IS NULL " +
            "WHERE cr.deleted_at IS NULL " +
            "  AND cr.status = 'PENDING' " +
            "  AND cr.doctor_id = #{doctorId}")
    List<Long> selectPendingByDoctor(@Param("doctorId") Long doctorId);

    /**
     * 查询当前医生是否有 IN_PROGRESS 的问诊记录。
     */
    @Select("SELECT COUNT(*) FROM consult_record " +
            "WHERE deleted_at IS NULL " +
            "  AND status = 'IN_PROGRESS' " +
            "  AND doctor_id = #{doctorId}")
    long countInProgressByDoctor(@Param("doctorId") Long doctorId);

    /**
     * 查询号源时段起止时间与排班日期（用于接诊时段校验）。
     */
    @Select("SELECT s.start_time, s.end_time, sch.schedule_date " +
            "FROM consult_record cr " +
            "JOIN appointment a ON cr.appointment_id = a.id AND a.deleted_at IS NULL " +
            "JOIN slot_snapshot ss ON a.slot_snapshot_id = ss.id AND ss.deleted_at IS NULL " +
            "JOIN slot s ON ss.slot_id = s.id AND s.deleted_at IS NULL " +
            "JOIN schedule sch ON s.schedule_id = sch.id AND sch.deleted_at IS NULL " +
            "WHERE cr.id = #{consultId} AND cr.deleted_at IS NULL")
    SlotTimeInfo selectSlotTimeByConsultId(@Param("consultId") Long consultId);

    /**
     * 批量创建缺失的 consult_record（幂等）。
     *
     * <p>查找今日已支付（PAID）但尚无对应问诊记录的挂号订单，
     * 自动插入 consult_record（status = PENDING），幂等操作。
     *
     * <p>用于解决 C端挂号成功后未创建 consult_record 的遗漏场景，
     * 在 B端查询队列前自动补全，对双方无侵入。
     */
    @Insert("<script>" +
            "INSERT INTO consult_record (appointment_id, doctor_id, patient_id, status, created_at, updated_at) " +
            "SELECT a.id, a.doctor_id, a.patient_id, 'PENDING', now(), now() " +
            "FROM appointment a " +
            "JOIN slot_snapshot ss ON a.slot_snapshot_id = ss.id AND ss.deleted_at IS NULL " +
            "JOIN slot s ON ss.slot_id = s.id AND s.deleted_at IS NULL " +
            "JOIN schedule sch ON s.schedule_id = sch.id AND sch.deleted_at IS NULL " +
            "WHERE a.status = 'PAID' " +
            "  AND a.deleted_at IS NULL " +
            "  AND a.doctor_id IN " +
            "  <foreach item='did' collection='doctorIds' open='(' separator=',' close=')'>#{did}</foreach> " +
            "  AND sch.schedule_date = CURRENT_DATE " +
            "  AND NOT EXISTS (SELECT 1 FROM consult_record cr WHERE cr.appointment_id = a.id AND cr.deleted_at IS NULL)" +
            "</script>")
    int batchCreateIfMissing(@Param("doctorIds") List<Long> doctorIds);

    /**
     * 批量过期已过期的 PENDING 问诊记录。
     *
     * <p>将排班日期早于今天的 PENDING 问诊记录标记为 NO_SHOW，
     * 同步更新对应的 appointment 状态为 EXPIRED。
     * 在查询队列前自动执行，确保队列只显示有效待接诊患者。
     *
     * @param doctorIds 医生 ID 列表
     * @return 影响行数
     */
    @Update("<script>" +
            "UPDATE consult_record cr SET status = 'NO_SHOW', updated_at = now() " +
            "WHERE cr.deleted_at IS NULL " +
            "  AND cr.status = 'PENDING' " +
            "  AND cr.doctor_id IN " +
            "  <foreach item='did' collection='doctorIds' open='(' separator=',' close=')'>#{did}</foreach> " +
            "  AND EXISTS (" +
            "    SELECT 1 FROM appointment a " +
            "    JOIN slot_snapshot ss ON a.slot_snapshot_id = ss.id AND ss.deleted_at IS NULL " +
            "    JOIN slot s ON ss.slot_id = s.id AND s.deleted_at IS NULL " +
            "    JOIN schedule sch ON s.schedule_id = sch.id AND sch.deleted_at IS NULL " +
            "    WHERE cr.appointment_id = a.id " +
            "      AND sch.schedule_date &lt; CURRENT_DATE" +
            "  )" +
            "</script>")
    int batchExpireOldPending(@Param("doctorIds") List<Long> doctorIds);
}
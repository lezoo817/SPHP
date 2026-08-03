package com.sphp.admin.doctor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sphp.admin.doctor.entity.ConsultRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
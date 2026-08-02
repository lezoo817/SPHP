package com.sphp.admin.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sphp.admin.schedule.dto.LockedSlotRow;
import com.sphp.admin.schedule.entity.SlotSnapshot;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * 号源快照表 Mapper。
 */
public interface SlotSnapshotMapper extends BaseMapper<SlotSnapshot> {

    /**
     * 锁定号源看板分页查询（status=LOCKED 的快照，按日期+科室+数据权限过滤）。
     *
     * <p>schedule 表无 hospital_id，医院范围经 doctor.hospital_id 关联过滤；
     * deptId 为页面可选条件；scopeDeptId/scopeDoctorId 为数据权限强制条件（null 不生效）。
     *
     * @param page          分页参数
     * @param date          排班日期（必填）
     * @param deptId        页面科室过滤（可空）
     * @param hospitalId    数据权限：所属医院
     * @param scopeDeptId   数据权限：科室（DEPT_HEAD 强制本科室，可空）
     * @param scopeDoctorId 数据权限：医生（DOCTOR 强制本人，可空）
     * @return 锁定号源分页结果
     */
    @Select("<script>" +
            "SELECT ss.id AS slotId, s.schedule_id AS scheduleId, " +
            "       d.name AS doctorName, p.name AS patientName, " +
            "       ss.locked_at AS lockedAt, ss.status AS status " +
            "FROM slot_snapshot ss " +
            "JOIN slot s ON ss.slot_id = s.id AND s.deleted_at IS NULL " +
            "JOIN schedule sch ON s.schedule_id = sch.id AND sch.deleted_at IS NULL " +
            "JOIN doctor d ON sch.doctor_id = d.id AND d.deleted_at IS NULL " +
            "LEFT JOIN patient p ON ss.patient_id = p.id AND p.deleted_at IS NULL " +
            "WHERE ss.status = 'LOCKED' AND ss.deleted_at IS NULL " +
            "  AND sch.schedule_date = #{date} " +
            "  AND d.hospital_id = #{hospitalId} " +
            "<if test='deptId != null'> AND sch.dept_id = #{deptId} </if>" +
            "<if test='scopeDeptId != null'> AND sch.dept_id = #{scopeDeptId} </if>" +
            "<if test='scopeDoctorId != null'> AND sch.doctor_id = #{scopeDoctorId} </if>" +
            "ORDER BY ss.locked_at DESC" +
            "</script>")
    IPage<LockedSlotRow> selectLockedPage(Page<LockedSlotRow> page,
                                          @Param("date") LocalDate date,
                                          @Param("deptId") Long deptId,
                                          @Param("hospitalId") Long hospitalId,
                                          @Param("scopeDeptId") Long scopeDeptId,
                                          @Param("scopeDoctorId") Long scopeDoctorId);
}

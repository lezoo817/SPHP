package com.sphp.admin.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sphp.admin.schedule.dto.LockedSlotRow;
import com.sphp.admin.schedule.entity.SlotSnapshot;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

/**
 * 号源快照表 Mapper。
 *
 * @author lezoo17
 * @since 2026-08-10
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

    /**
     * 为指定时段批量生成 AVAILABLE 号源快照（号源池）。
     *
     * <p>号源池以 slot_snapshot 的 AVAILABLE 记录为准（系分 §4.2.3），C 端可约数与
     * 锁号均基于 AVAILABLE 快照计数，故发布排班时必须同步生成快照，否则号源池为空、
     * 患者无法预约。单条 SQL 借 generate_series 一次插入 count 行。
     *
     * @param slotId 时段 ID
     * @param count  生成数量（须等于时段 total_count）
     * @return 插入行数
     */
    @Insert("INSERT INTO slot_snapshot (slot_id, status, created_at, updated_at) " +
            "SELECT #{slotId}, 'AVAILABLE', now(), now() FROM generate_series(1, #{count})")
    int generateAvailableSnapshots(@Param("slotId") Long slotId, @Param("count") int count);

    /**
     * 清空某排班下全部 AVAILABLE 号源快照（软删）。
     *
     * <p>取消发布时调用：排班取消后号源池随之清空，避免残留可约数据造成误读；
     * LOCKED/SOLD 等历史快照不受影响。
     *
     * @param scheduleId 排班 ID
     * @return 受影响行数
     */
    @Update("UPDATE slot_snapshot SET deleted_at = now(), updated_at = now() " +
            "WHERE slot_id IN (SELECT id FROM slot WHERE schedule_id = #{scheduleId} AND deleted_at IS NULL) " +
            "  AND status = 'AVAILABLE' AND deleted_at IS NULL")
    int clearAvailableSnapshotsBySchedule(@Param("scheduleId") Long scheduleId);
}

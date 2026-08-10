package com.sphp.admin.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sphp.admin.schedule.dto.ScheduleSlotStat;
import com.sphp.admin.schedule.entity.Slot;
import com.sphp.admin.schedule.vo.SourcePoolVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 号源时段表 Mapper。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
public interface SlotMapper extends BaseMapper<Slot> {

    /**
     * 批量聚合多个排班的号源统计（remain/sold/locked），避免逐排班查询的 N+1。
     *
     * @param scheduleIds 排班 ID 集合（调用方需保证非空）
     * @return 各排班的聚合统计
     */
    @Select("<script>" +
            "SELECT s.schedule_id AS scheduleId, " +
            "       COALESCE(SUM(CASE WHEN snap.total > 0 THEN snap.avail ELSE s.remain_count END), 0) AS remainTotal, " +
            "       COALESCE(SUM(snap.sold), 0) AS soldTotal, " +
            "       COALESCE(SUM(snap.locked), 0) AS lockedTotal " +
            "FROM slot s " +
            // 剩余口径：已发布时段（存在快照）取可约快照数（AVAILABLE + RELEASED），
            // 与 C 端 `countRegisteringAvailableSnapshots` 一致：RELEASED 来自 C 端
            // 取消订单路径（registeringReleaseLockedSnapshot），号源已归还可约池，
            // B 端"剩余"必须与 C 端"可约"同步递增；EXPIRED 仍不计（已不可约）。
            // 草稿时段（无快照）回退 slot.remain_count，保证列表发布门禁仍依赖已配置号源之和
            "LEFT JOIN ( " +
            "  SELECT slot_id, " +
            "         COUNT(*) FILTER (WHERE status IN ('AVAILABLE', 'RELEASED')) AS avail, " +
            "         COUNT(*) FILTER (WHERE status = 'SOLD') AS sold, " +
            "         COUNT(*) FILTER (WHERE status = 'LOCKED') AS locked, " +
            "         COUNT(*) AS total " +
            "  FROM slot_snapshot " +
            "  WHERE deleted_at IS NULL " +
            "  GROUP BY slot_id " +
            ") snap ON snap.slot_id = s.id " +
            "WHERE s.deleted_at IS NULL AND s.schedule_id IN " +
            "<foreach collection='scheduleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY s.schedule_id" +
            "</script>")
    List<ScheduleSlotStat> aggregateByScheduleIds(@Param("scheduleIds") Collection<Long> scheduleIds);

    /**
     * 号源池分页查询：按已发布排班明细返回，每行 = 医生某天某班次，含科室/医生/号源统计。
     *
     * <p>仅统计 PUBLISHED 排班；schedule 表无 hospital_id，医院范围经 doctor.hospital_id 关联过滤。
     * 科室（诊室）名称取 {@code schedule.dept_id → department.name}。剩余口径与
     * {@link #aggregateByScheduleIds} 一致：已发布时段（存在快照）取 AVAILABLE + RELEASED 快照数。
     * 数据权限参数语义：
     * <ul>
     *     <li>{@code deptId} / {@code doctorId}：ADMIN 传入的用户筛选条件</li>
     *     <li>{@code scopeDeptId} / {@code scopeDoctorId}：DEPT_HEAD / DOCTOR 数据权限强制条件</li>
     * </ul>
     *
     * @param page          分页参数
     * @param startDate     开始日期（含）
     * @param endDate       结束日期（含）
     * @param hospitalId    当前用户所属医院（必填）
     * @param deptId        科室筛选（仅 ADMIN，可为 null）
     * @param doctorId      医生筛选（仅 ADMIN，可为 null）
     * @param scopeDeptId   数据权限科室（DEPT_HEAD，可为 null）
     * @param scopeDoctorId 数据权限医生（DOCTOR，可为 null）
     * @return 按排班明细分页（日期倒序）
     */
    @Select("<script>" +
            "SELECT sch.id AS scheduleId, " +
            "       sch.schedule_date AS scheduleDate, sch.shift AS shift, " +
            "       dp.name AS deptName, d.name AS doctorName, " +
            "       sch.total_slots AS totalSlots, " +
            // 沿用排班列表 EXPIRED 口径，避免前后端判别不一致；SQL 中 < 必须转义为 &lt;，否则 MyBatis <script> 解析为 XML 时报错
            "       CASE WHEN sch.schedule_date &lt; CURRENT_DATE THEN true ELSE false END AS expired, " +
            "       COALESCE(stat.remainTotal, 0) AS remainSlots, " +
            "       COALESCE(stat.soldTotal, 0) AS soldSlots, " +
            "       COALESCE(stat.lockedTotal, 0) AS lockedSlots " +
            "FROM schedule sch " +
            "JOIN doctor d ON sch.doctor_id = d.id AND d.deleted_at IS NULL " +
            "LEFT JOIN department dp ON sch.dept_id = dp.id AND dp.deleted_at IS NULL " +
            // 先按排班预聚合号源统计，避免 slot 与 schedule 1:N 连接放大 sch.total_slots
            "LEFT JOIN ( " +
            "  SELECT s.schedule_id AS scheduleId, " +
            "         COALESCE(SUM(CASE WHEN snap.total > 0 THEN snap.avail ELSE s.remain_count END), 0) AS remainTotal, " +
            "         COALESCE(SUM(snap.sold), 0) AS soldTotal, " +
            "         COALESCE(SUM(snap.locked), 0) AS lockedTotal " +
            "  FROM slot s " +
            "  LEFT JOIN ( " +
            "    SELECT slot_id, " +
            "           COUNT(*) FILTER (WHERE status IN ('AVAILABLE', 'RELEASED')) AS avail, " +
            "           COUNT(*) FILTER (WHERE status = 'SOLD') AS sold, " +
            "           COUNT(*) FILTER (WHERE status = 'LOCKED') AS locked, " +
            "           COUNT(*) AS total " +
            "    FROM slot_snapshot " +
            "    WHERE deleted_at IS NULL " +
            "    GROUP BY slot_id " +
            "  ) snap ON snap.slot_id = s.id " +
            "  WHERE s.deleted_at IS NULL " +
            "  GROUP BY s.schedule_id " +
            ") stat ON stat.scheduleId = sch.id " +
            "WHERE sch.deleted_at IS NULL " +
            "  AND sch.status = 'PUBLISHED' " +
            "  AND d.hospital_id = #{hospitalId} " +
            "  AND sch.schedule_date BETWEEN #{startDate} AND #{endDate} " +
            "<if test='deptId != null'> AND sch.dept_id = #{deptId} </if>" +
            "<if test='doctorId != null'> AND sch.doctor_id = #{doctorId} </if>" +
            "<if test='scopeDeptId != null'> AND sch.dept_id = #{scopeDeptId} </if>" +
            "<if test='scopeDoctorId != null'> AND sch.doctor_id = #{scopeDoctorId} </if>" +
            "ORDER BY CASE WHEN sch.schedule_date = CURRENT_DATE THEN 0 ELSE 1 END ASC, " +
            "         sch.schedule_date DESC, " +
            "         CASE sch.shift WHEN 'MORNING' THEN 1 ELSE 2 END, " +
            "         sch.id" +
            "</script>")
    IPage<SourcePoolVO> selectSourcePoolPage(Page<?> page,
                                             @Param("startDate") LocalDate startDate,
                                             @Param("endDate") LocalDate endDate,
                                             @Param("hospitalId") Long hospitalId,
                                             @Param("deptId") Long deptId,
                                             @Param("doctorId") Long doctorId,
                                             @Param("scopeDeptId") Long scopeDeptId,
                                             @Param("scopeDoctorId") Long scopeDoctorId);
}

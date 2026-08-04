package com.sphp.admin.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.admin.schedule.dto.ScheduleSlotStat;
import com.sphp.admin.schedule.entity.Slot;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 号源时段表 Mapper。
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
            // 快照先按 slot 预聚合，避免 slot 与 slot_snapshot 1:N 连接导致 SUM(remain_count) 按快照行数放大；
            // 剩余口径：已发布时段（存在快照）取 AVAILABLE 快照数（C端预约只扣快照不扣 remain_count），
            // 草稿时段（无快照）回退 slot.remain_count，保证列表发布门禁仍依赖已配置号源之和
            "LEFT JOIN ( " +
            "  SELECT slot_id, " +
            "         COUNT(*) FILTER (WHERE status = 'AVAILABLE') AS avail, " +
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
}

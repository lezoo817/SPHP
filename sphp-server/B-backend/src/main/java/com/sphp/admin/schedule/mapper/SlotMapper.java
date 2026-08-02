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
            "       COALESCE(SUM(s.remain_count), 0) AS remainTotal, " +
            "       COALESCE(SUM(CASE WHEN ss.status = 'SOLD' THEN 1 ELSE 0 END), 0) AS soldTotal, " +
            "       COALESCE(SUM(CASE WHEN ss.status = 'LOCKED' THEN 1 ELSE 0 END), 0) AS lockedTotal " +
            "FROM slot s " +
            "LEFT JOIN slot_snapshot ss ON ss.slot_id = s.id AND ss.deleted_at IS NULL " +
            "WHERE s.deleted_at IS NULL AND s.schedule_id IN " +
            "<foreach collection='scheduleIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "GROUP BY s.schedule_id" +
            "</script>")
    List<ScheduleSlotStat> aggregateByScheduleIds(@Param("scheduleIds") Collection<Long> scheduleIds);
}

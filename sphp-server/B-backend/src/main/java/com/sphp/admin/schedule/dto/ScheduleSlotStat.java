package com.sphp.admin.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 排班号源聚合统计（排班列表的 booked/remain/locked 计数来源）。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@Schema(description = "排班号源聚合统计")
public class ScheduleSlotStat {

    /** 排班 ID */
    private Long scheduleId;

    /** 剩余可约号源数（AVAILABLE + RELEASED 快照数，与 C 端可约口径一致；草稿时段回退 slot.remain_count） */
    private Long remainTotal;

    /** 已售号源数（SOLD 快照数） */
    private Long soldTotal;

    /** 已锁定号源数（LOCKED 快照数） */
    private Long lockedTotal;
}

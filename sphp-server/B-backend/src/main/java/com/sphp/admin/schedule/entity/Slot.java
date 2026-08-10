package com.sphp.admin.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * 号源时段表实体（对应表 slot）。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@TableName("slot")
public class Slot {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属排班 ID */
    private Long scheduleId;

    /** 时段开始时间 */
    private LocalTime startTime;

    /** 时段结束时间 */
    private LocalTime endTime;

    /** 该时段号源数 */
    private Integer totalCount;

    /** 剩余可约号源数 */
    private Integer remainCount;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    /** 软删除时间（null 表示有效） */
    private OffsetDateTime deletedAt;
}

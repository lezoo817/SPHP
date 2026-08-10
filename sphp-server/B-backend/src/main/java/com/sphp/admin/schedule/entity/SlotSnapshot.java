package com.sphp.admin.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 号源快照表实体（对应表 slot_snapshot），每个号源独立记录。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@TableName("slot_snapshot")
public class SlotSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属时段 ID */
    private Long slotId;

    /** 就诊人 ID（未占用为空） */
    private Long patientId;

    /** 状态：AVAILABLE / LOCKED / SOLD / EXPIRED / RELEASED */
    private String status;

    /** 锁定时间 */
    private OffsetDateTime lockedAt;

    /** 售出时间 */
    private OffsetDateTime soldAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    /** 软删除时间（null 表示有效） */
    private OffsetDateTime deletedAt;
}

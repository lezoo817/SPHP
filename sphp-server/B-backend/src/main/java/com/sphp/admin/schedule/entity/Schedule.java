package com.sphp.admin.schedule.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 排班表实体（对应表 schedule）。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@TableName("schedule")
public class Schedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 医生 ID */
    private Long doctorId;

    /** 冗余科室 ID（创建时取自 doctor.dept_id），支撑按日期+科室联合查询 */
    private Long deptId;

    /** 排班日期 */
    private LocalDate scheduleDate;

    /** 班次：MORNING / AFTERNOON */
    private String shift;

    /** 号源总数（1~99） */
    private Integer totalSlots;

    /** 状态：DRAFT / PUBLISHED / CANCELLED */
    private String status;

    /** 发布时间 */
    private OffsetDateTime publishedAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    /** 软删除时间（null 表示有效） */
    private OffsetDateTime deletedAt;
}

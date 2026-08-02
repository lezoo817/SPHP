package com.sphp.admin.schedule.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 锁定号源看板查询结果行（关联 slot_snapshot → slot → schedule → doctor/patient）。
 */
@Data
public class LockedSlotRow {

    /** 号源快照 ID（slot_snapshot.id） */
    private Long slotId;

    /** 排班 ID */
    private Long scheduleId;

    /** 医生姓名 */
    private String doctorName;

    /** 就诊人姓名（原始值，脱敏在 Service 层处理） */
    private String patientName;

    /** 锁定时间 */
    private OffsetDateTime lockedAt;

    /** 状态：LOCKED */
    private String status;
}

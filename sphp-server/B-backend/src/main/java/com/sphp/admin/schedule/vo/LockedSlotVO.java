package com.sphp.admin.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 锁定号源看板项响应（系分 §5.4.7）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "锁定号源看板项")
public class LockedSlotVO {

    @Schema(description = "号源快照ID（slot_snapshot.id）")
    private Long slotId;

    @Schema(description = "排班ID")
    private Long scheduleId;

    @Schema(description = "医生姓名")
    private String doctorName;

    @Schema(description = "就诊人姓名（脱敏）")
    private String patientName;

    @Schema(description = "锁定时间")
    private OffsetDateTime lockedAt;

    @Schema(description = "状态：LOCKED")
    private String status;

    @Schema(description = "锁定过期时间（lockedAt + 15分钟）")
    private OffsetDateTime expireAt;
}

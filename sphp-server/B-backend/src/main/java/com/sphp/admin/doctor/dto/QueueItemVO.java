package com.sphp.admin.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 待接诊列表项 VO。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "待接诊列表项")
public class QueueItemVO {

    @Schema(description = "问诊记录 ID")
    private Long consultId;

    @Schema(description = "患者 ID")
    private Long patientId;

    @Schema(description = "患者姓名")
    private String patientName;

    @Schema(description = "患者性别：MALE / FEMALE / UNKNOWN")
    private String patientGender;

    @Schema(description = "患者年龄")
    private Integer patientAge;

    @Schema(description = "AI预问诊摘要（chiefComplaint / allergies 等）")
    private Map<String, Object> aiSummary;

    @Schema(description = "排队号")
    private Integer queueNumber;

    @Schema(description = "挂号时间")
    private OffsetDateTime appointmentTime;

    @Schema(description = "问诊状态：PENDING / IN_PROGRESS")
    private String status;

    @Schema(description = "号源时段开始时间（HH:mm）")
    private String slotStartTime;

    @Schema(description = "号源时段结束时间（HH:mm）")
    private String slotEndTime;
}
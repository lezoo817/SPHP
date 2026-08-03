package com.sphp.admin.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 接诊历史项 VO。
 *
 * <p>当前医生已完成的历史接诊记录列表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "接诊历史项")
public class ConsultHistoryVO {

    @Schema(description = "问诊记录 ID")
    private Long consultId;

    @Schema(description = "患者 ID")
    private Long patientId;

    @Schema(description = "患者姓名")
    private String patientName;

    @Schema(description = "患者性别：MALE / FEMALE / UNKNOWN")
    private String patientGender;

    @Schema(description = "患者出生日期")
    private LocalDate patientDateOfBirth;

    @Schema(description = "主诉")
    private String chiefComplaint;

    @Schema(description = "病历摘要（前100字）")
    private String noteSummary;

    @Schema(description = "问诊状态：COMPLETED / NO_SHOW")
    private String status;

    @Schema(description = "开始接诊时间")
    private OffsetDateTime startedAt;

    @Schema(description = "结束问诊时间")
    private OffsetDateTime endedAt;

    @Schema(description = "创建时间（挂号时间）")
    private OffsetDateTime createdAt;
}
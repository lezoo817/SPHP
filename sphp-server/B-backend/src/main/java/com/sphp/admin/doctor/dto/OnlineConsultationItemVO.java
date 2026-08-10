package com.sphp.admin.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * B 端在线问诊列表项。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "在线问诊列表项")
public class OnlineConsultationItemVO {

    @Schema(description = "问诊记录 ID")
    private Long consultId;

    @Schema(description = "患者 ID")
    private Long patientId;

    @Schema(description = "患者姓名")
    private String patientName;

    @Schema(description = "患者性别")
    private String patientGender;

    @Schema(description = "问诊状态")
    private String status;

    @Schema(description = "AI 预问诊摘要")
    private Map<String, Object> aiSummary;

    @Schema(description = "主诉")
    private String chiefComplaint;

    @Schema(description = "预问诊提交时间")
    private OffsetDateTime submittedAt;

    @Schema(description = "医生回复时间")
    private OffsetDateTime doctorRepliedAt;

    @Schema(description = "是否允许开始回复")
    private boolean canStart;

    @Schema(description = "是否允许提交回复")
    private boolean canReply;
}

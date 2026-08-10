package com.sphp.admin.doctor.dto;

import com.sphp.admin.prescription.dto.PrescriptionListVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * B 端在线问诊详情。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "在线问诊详情")
public class OnlineConsultationDetailVO {

    @Schema(description = "问诊记录 ID")
    private Long consultId;

    @Schema(description = "挂号订单 ID，在线问诊固定为空")
    private Long appointmentId;

    @Schema(description = "问诊状态")
    private String status;

    @Schema(description = "主诉")
    private String chiefComplaint;

    @Schema(description = "现病史")
    private String historyOfPresentIllness;

    @Schema(description = "预问诊提交时间")
    private OffsetDateTime submittedAt;

    @Schema(description = "医生回复时间")
    private OffsetDateTime doctorRepliedAt;

    @Schema(description = "患者聚合详情")
    private PatientDetailVO patientDetail;

    @Schema(description = "问诊消息")
    private List<MessageVO> messages;

    @Schema(description = "关联处方")
    private List<PrescriptionListVO> prescriptions;

    @Schema(description = "是否允许开始回复")
    private boolean canStart;

    @Schema(description = "是否允许提交回复")
    private boolean canReply;
}

package com.sphp.admin.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 接诊历史详情 VO。
 *
 * <p>查看历史接诊记录时，展示该次问诊的病历全文和关联处方。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "接诊历史详情")
public class ConsultHistoryDetailVO {

    @Schema(description = "问诊记录 ID")
    private Long consultId;

    @Schema(description = "患者 ID")
    private Long patientId;

    @Schema(description = "问诊状态：COMPLETED / NO_SHOW")
    private String status;

    @Schema(description = "主诉")
    private String chiefComplaint;

    @Schema(description = "病历全文")
    private String doctorNote;

    @Schema(description = "接诊医生姓名")
    private String doctorName;

    @Schema(description = "开始接诊时间")
    private OffsetDateTime startedAt;

    @Schema(description = "结束问诊时间")
    private OffsetDateTime endedAt;

    @Schema(description = "创建时间")
    private OffsetDateTime createdAt;

    @Schema(description = "关联处方列表")
    private List<PrescriptionBrief> prescriptions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "处方简要信息")
    public static class PrescriptionBrief {
        @Schema(description = "处方 ID")
        private Long id;
        @Schema(description = "处方状态：SUBMITTED / APPROVED / REJECTED / CANCELLED")
        private String status;
        @Schema(description = "药品明细条数")
        private Integer itemCount;
        @Schema(description = "开具时间")
        private OffsetDateTime issuedAt;
    }
}

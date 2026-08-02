package com.sphp.patient.health.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.util.List;

/** 录入检查报告请求。 */
@Getter
@Setter
public class ProposalReportCreateRequest {
    /** 就诊人 ID */
    @Positive
    private Long patientId;

    /** 检查报告名称 */
    @NotBlank
    @Size(max=256)
    private String reportName;

    /** 检查报告日期 */
    @NotNull
    private LocalDate reportDate;

    /** 检查报告指标列表 */
    @NotEmpty
    @Valid
    private List<Indicator> indicators;

    /** 报告指标请求项。 */
    @Getter
    @Setter
    public static class Indicator {
        /** 检查报告指标名称 */
        @NotBlank
        @Size(max=128)
        private String name;

        /** 检查报告指标值 */
        @NotBlank
        @Size(max=128)
        private String value;

        /** 检查报告指标单位 */
        @Size(max=64)
        private String unit;

        /** 检查报告指标参考范围 */
        @Size(max=128)
        private String referenceRange;

    }
}

package com.sphp.patient.health.vo;
import lombok.*;

/** 报告录入结果。 */
@Getter
@Builder
public class ProposalReportCreateVO {
    // 报告 ID
    private final Long reportId;

    // 报告状态
    private final String status;
}

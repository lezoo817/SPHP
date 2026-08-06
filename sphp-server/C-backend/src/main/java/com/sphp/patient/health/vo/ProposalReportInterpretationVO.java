package com.sphp.patient.health.vo;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * 医生病历报告解读响应。
 */
@Getter
@Builder
public class ProposalReportInterpretationVO {
    // 报告 ID，即问诊记录 ID
    private final Long reportId;

    // 解读正文
    private final String content;

    // 免责声明
    private final String disclaimer;

    // 解读生成时间
    private final OffsetDateTime generatedAt;
}

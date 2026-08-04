package com.sphp.patient.health.mapper;

import java.time.OffsetDateTime;

/**
 * 医生病历解读查询投影。
 *
 * @param content 解读正文
 * @param disclaimer 解读免责声明
 * @param generatedAt 解读生成时间
 */
public record ConsultationReportInterpretationRecord(
        String content,
        String disclaimer,
        OffsetDateTime generatedAt
) {
}

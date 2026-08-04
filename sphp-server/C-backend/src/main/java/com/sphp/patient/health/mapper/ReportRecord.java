package com.sphp.patient.health.mapper;
import java.time.LocalDate;

/**
 * 检查报告投影。
 * @param id 检查报告 ID
 * @param patientId 患者 ID
 * @param reportName 检查报告名称
 * @param reportDate 检查报告日期
 * @param interpretationStatus 检查报告解读状态
 * @param interpretation 检查报告解读
 */
public record ReportRecord(
        Long id,
        Long patientId,
        String reportName,
        LocalDate reportDate,
        String interpretationStatus,
        String interpretation
){}

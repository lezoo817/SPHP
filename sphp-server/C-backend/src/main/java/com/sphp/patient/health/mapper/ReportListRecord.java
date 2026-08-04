package com.sphp.patient.health.mapper;
import java.time.LocalDate;

/**
 * 检查报告列表查询记录。
 * @param id 检查报告 ID
 * @param reportName 检查报告名称
 * @param reportDate 检查报告日期
 * @param indicatorCount 检查报告指标数量
 */
public record ReportListRecord(
        Long id,
        String reportName,
        LocalDate reportDate,
        long indicatorCount
){}

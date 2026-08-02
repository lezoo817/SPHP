package com.sphp.patient.health.mapper; import java.time.LocalDate; /** 报告列表投影。 */ public record ReportListRecord(Long id,String reportName,LocalDate reportDate,long indicatorCount){}

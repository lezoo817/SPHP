package com.sphp.patient.triage.mapper;

/**
 * 导诊规则命中的科室投影。
 *
 * @param departmentId 科室 ID
 * @param departmentName 科室名称
 * @param reason 推荐原因
 * @param urgency 规则紧急程度
 */
public record TriageDepartmentRuleRecord(Long departmentId, String departmentName, String reason, String urgency) {
}

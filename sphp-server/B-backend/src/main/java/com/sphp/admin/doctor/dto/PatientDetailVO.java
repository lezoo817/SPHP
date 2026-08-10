package com.sphp.admin.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 患者详情 VO。
 *
 * <p>包含基本信息、过敏史、既往史、AI摘要、近期处方、历史就诊记录。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "患者详情")
public class PatientDetailVO {

    @Schema(description = "问诊记录 ID")
    private Long consultId;

    @Schema(description = "病历记录（结构化 JSON 或旧版纯文本），用于接诊中回显已保存病历")
    private String doctorNote;

    @Schema(description = "患者基本信息")
    private PatientInfo patient;

    @Schema(description = "过敏史列表")
    private List<AllergyInfo> allergies;

    @Schema(description = "既往史列表")
    private List<MedicalHistoryInfo> medicalHistories;

    @Schema(description = "AI 预问诊摘要")
    private Map<String, Object> aiSummary;

    @Schema(description = "近期处方列表")
    private List<RecentPrescriptionInfo> recentPrescriptions;

    @Schema(description = "历史就诊记录")
    private List<HistoryRecordInfo> historyRecords;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "患者基本信息")
    public static class PatientInfo {
        @Schema(description = "患者 ID")
        private Long id;
        @Schema(description = "姓名")
        private String name;
        @Schema(description = "性别：MALE / FEMALE / UNKNOWN")
        private String gender;
        @Schema(description = "出生日期")
        private LocalDate dateOfBirth;
        @Schema(description = "手机号（脱敏后）")
        private String phone;
        @Schema(description = "紧急联系人手机号（脱敏后）")
        private String emergencyContact;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "过敏史")
    public static class AllergyInfo {
        @Schema(description = "过敏史记录 ID")
        private Long id;
        @Schema(description = "过敏原")
        private String allergen;
        @Schema(description = "反应描述")
        private String reaction;
        @Schema(description = "严重程度：MILD / MODERATE / SEVERE")
        private String severity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "既往史")
    public static class MedicalHistoryInfo {
        @Schema(description = "既往史记录 ID")
        private Long id;
        @Schema(description = "病史内容")
        private String content;
        @Schema(description = "发生日期")
        private LocalDate occurredAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "近期处方")
    public static class RecentPrescriptionInfo {
        @Schema(description = "处方 ID")
        private Long id;
        @Schema(description = "处方状态：SUBMITTED / APPROVED / REJECTED / CANCELLED")
        private String status;
        @Schema(description = "开具时间")
        private OffsetDateTime issuedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "历史就诊记录")
    public static class HistoryRecordInfo {
        @Schema(description = "就诊日期（yyyy-MM-dd）")
        private String date;
        @Schema(description = "记录类型")
        private String type;
        @Schema(description = "就诊医生姓名")
        private String doctorName;
        @Schema(description = "病历摘要")
        private String summary;
        @Schema(description = "问诊状态：IN_PROGRESS / COMPLETED / NO_SHOW")
        private String status;
    }
}

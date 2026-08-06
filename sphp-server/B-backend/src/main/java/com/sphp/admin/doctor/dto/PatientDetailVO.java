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
 * 患者详情 VO（系分 §5.5.2）。
 *
 * <p>包含基本信息、过敏史、既往史、AI摘要、近期处方、历史就诊记录。
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
        private Long id;
        private String name;
        private String gender;
        private LocalDate dateOfBirth;
        private String phone;
        private String emergencyContact;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "过敏史")
    public static class AllergyInfo {
        private Long id;
        private String allergen;
        private String reaction;
        private String severity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "既往史")
    public static class MedicalHistoryInfo {
        private Long id;
        private String content;
        private LocalDate occurredAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "近期处方")
    public static class RecentPrescriptionInfo {
        private Long id;
        private String status;
        private OffsetDateTime issuedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "历史就诊记录")
    public static class HistoryRecordInfo {
        private String date;
        private String type;
        private String summary;
        private String status;
    }
}
package com.sphp.patient.health.mapper;
import java.time.OffsetDateTime;

/**
 * 用药计划投影。
 * @param id 用药计划 ID
 * @param patientId 就诊人 ID
 * @param drugName 药物名称
 * @param dosage 用药剂量
 * @param frequency 用药频次
 * @param nextReminderAt 下次提醒时间
 * @param reminderEnabled 是否已开启提醒
 * @param reminderTimesJson 每日提醒时刻 JSON 数组
 * @param status 用药计划状态
 */
public record MedicationRecord(
        // 用药计划 ID
        Long id,

        // 就诊人 ID
        Long patientId,

        // 药物名称
        String drugName,

        // 用药剂量
        String dosage,

        // 用药频次
        String frequency,

        // 下次提醒时间
        OffsetDateTime nextReminderAt,

        // 是否已开启提醒
        boolean reminderEnabled,

        // 每日提醒时刻 JSON 数组
        String reminderTimesJson,

        // 用药计划状态
        String status
){}

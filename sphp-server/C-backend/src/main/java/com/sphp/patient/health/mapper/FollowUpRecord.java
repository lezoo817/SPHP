package com.sphp.patient.health.mapper;
import java.time.OffsetDateTime;

/**
 * 随访计划查询记录。
 * @param id
 * @param patientId 就诊人 ID
 * @param type 随访计划类型
 * @param dueAt 随访计划截止日期
 * @param content 随访计划内容
 * @param status 随访计划状态
 * @param remindAt 随访计划提醒时间
 */
public record FollowUpRecord(
        // 随访计划 ID
        Long id,

        // 就诊人 ID
        Long patientId,

        // 随访计划类型
        String type,

        // 随访计划内容
        OffsetDateTime dueAt,

        // 随访计划内容
        String content,

        // 随访计划状态
        String status,

        // 随访计划提醒时间
        OffsetDateTime remindAt

        ){

        }

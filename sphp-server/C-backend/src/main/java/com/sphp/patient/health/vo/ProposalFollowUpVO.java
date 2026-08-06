package com.sphp.patient.health.vo;
import lombok.*;
import java.time.OffsetDateTime;

/** 随访计划响应。 */
@Getter
@Builder
public class ProposalFollowUpVO {
    // 随访计划 ID
    private final Long id;

    // 随访计划类型
    private final String type;

    // 随访计划截止时间
    private final OffsetDateTime dueAt;

    // 随访计划内容
    private final String content;

    // 随访计划状态
    private final String status;

    // 随访计划提醒时间
    private final OffsetDateTime remindAt;

}

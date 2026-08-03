package com.sphp.patient.health.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 更新既往史响应对象。
 */
@Getter
@Builder
public class MedicalHistoryUpdateVO {

    /** 既往史 ID */
    private final Long id;

    /** 既往史内容 */
    private final String content;

    /** 病史发生或记录日期 */
    private final LocalDate occurredAt;

    /** 更新时间 */
    private final OffsetDateTime updatedAt;
}

package com.sphp.patient.health.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 健康档案中的既往史列表项。
 */
@Getter
@Builder
public class MedicalHistoryItemVO {

    /** 既往史 ID */
    private final Long id;

    /** 既往史内容 */
    private final String content;

    /** 病史发生或记录日期 */
    private final LocalDate occurredAt;

}

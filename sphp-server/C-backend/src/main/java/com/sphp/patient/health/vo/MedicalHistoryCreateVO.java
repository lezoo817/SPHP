package com.sphp.patient.health.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 新增既往史响应对象。
 */
@Getter
@Builder
public class MedicalHistoryCreateVO {

    /** 既往史 ID */
    private final Long id;

    /** 既往史内容 */
    private final String content;

    /** 病史发生或记录日期 */
    private final LocalDate occurredAt;

}

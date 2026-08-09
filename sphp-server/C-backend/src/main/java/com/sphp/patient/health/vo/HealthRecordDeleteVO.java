package com.sphp.patient.health.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 健康档案记录软删除结果。
 */
@Getter
@Builder
public class HealthRecordDeleteVO {

    /** 已删除的健康档案记录 ID。 */
    private final Long id;

    /** 软删除时间。 */
    private final OffsetDateTime deletedAt;
}

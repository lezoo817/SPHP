package com.sphp.patient.health.vo;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 健康档案响应对象。
 */
@Getter
@Builder
public class HealthRecordVO {

    /** 最小患者资料 */
    private final HealthProfileVO profile;

    /** 过敏史列表 */
    private final List<AllergyItemVO> allergies;

    /** 既往史列表 */
    private final List<MedicalHistoryItemVO> medicalHistories;

    /** 健康档案摘要 */
    private final String summary;

}

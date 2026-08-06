package com.sphp.patient.consultation.vo;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 已批准处方详情响应。
 */
@Getter
@Builder
public class ConsultationPrescriptionDetailVO {

    /** 处方 ID */
    private final Long id;

    /** 处方状态，固定为 APPROVED */
    private final String status;

    /** 开方医生姓名，兼容既有接口字段 */
    private final String doctorName;

    /** 开方医生展示信息 */
    private final Doctor doctor;

    /** 药品明细 */
    private final List<Item> items;

    /**
     * 开方医生展示信息。
     */
    @Getter
    @Builder
    public static class Doctor {

        /** 医生 ID */
        private final Long id;

        /** 医生姓名 */
        private final String name;

        /** 医生职称 */
        private final String title;
    }

    /**
     * 处方药品明细展示项。
     */
    @Getter
    @Builder
    public static class Item {

        /** 药品 ID */
        private final Long drugId;

        /** 药品名称 */
        private final String drugName;

        /** 药品规格 */
        private final String specification;

        /** 单次用量 */
        private final String dosage;

        /** 用药频次 */
        private final String frequency;

        /** 用药方式 */
        private final String usage;

        /** 用药天数 */
        private final Short durationDays;
    }
}

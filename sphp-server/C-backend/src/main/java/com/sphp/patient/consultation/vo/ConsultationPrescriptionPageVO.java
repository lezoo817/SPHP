package com.sphp.patient.consultation.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 已批准处方分页响应。
 */
@Getter
@Builder
public class ConsultationPrescriptionPageVO {

    /** 当前页码 */
    private final int pageNo;
    /** 当前页大小 */
    private final int pageSize;
    /** 已批准处方总数 */
    private final long total;
    /** 当前页处方记录 */
    private final List<Item> records;

    /**
     * 已批准处方列表展示项。
     */
    @Getter
    @Builder
    public static class Item {

        /** 处方 ID */
        private final Long id;
        /** 关联问诊 ID */
        private final Long consultationId;
        /** 开方医生姓名 */
        private final String doctorName;
        /** 处方状态，固定为 APPROVED */
        private final String status;
        /** 开方时间 */
        private final OffsetDateTime issuedAt;
    }
}

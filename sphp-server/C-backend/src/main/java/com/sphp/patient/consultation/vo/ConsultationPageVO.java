package com.sphp.patient.consultation.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 问诊记录分页响应。
 */
@Getter
@Builder
public class ConsultationPageVO {

    /** 当前页码 */
    private final int pageNo;
    /** 当前页大小 */
    private final int pageSize;
    /** 记录总数 */
    private final long total;
    /** 当前页问诊记录 */
    private final List<Item> records;

    /**
     * 问诊列表展示项。
     */
    @Getter
    @Builder
    public static class Item {

        /** 问诊记录 ID */
        private final Long id;
        /** 关联挂号订单 ID */
        private final Long appointmentId;
        /** 接诊医生姓名 */
        private final String doctorName;
        /** 问诊状态 */
        private final String status;
        /** 最近更新时间 */
        private final OffsetDateTime updatedAt;
    }
}

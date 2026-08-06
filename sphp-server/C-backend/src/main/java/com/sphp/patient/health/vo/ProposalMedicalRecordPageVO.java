package com.sphp.patient.health.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 医生病历分页响应。
 */
@Getter
@Builder
public class ProposalMedicalRecordPageVO {
    // 页码
    private final int pageNo;

    // 每页数量
    private final int pageSize;

    // 总记录数
    private final long total;

    // 病历摘要列表
    private final List<Item> records;

    /**
     * 病历摘要。
     */
    @Getter
    @Builder
    public static class Item {
        // 问诊记录 ID，即病历 ID
        private final Long id;

        // 就诊人 ID
        private final Long patientId;

        // 医生姓名
        private final String doctorName;

        // 科室名称
        private final String departmentName;

        // 问诊完成时间
        private final OffsetDateTime completedAt;

        // 病历最后保存时间
        private final OffsetDateTime updatedAt;
    }
}

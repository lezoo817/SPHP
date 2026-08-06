package com.sphp.patient.health.vo;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 医生病历报告分页响应。
 */
@Getter
@Builder
public class ProposalReportPageVO {
    // 页码
    private final int pageNo;

    // 大小
    private final int pageSize;

    // 总记录数
    private final long total;

    // 记录列表
    private final List<Item> records;

    // 记录项
    @Getter
    @Builder
    public static class Item {
        // 报告 ID，即问诊记录 ID
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

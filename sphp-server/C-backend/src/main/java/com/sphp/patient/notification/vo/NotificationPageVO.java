package com.sphp.patient.notification.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * C端站内通知分页响应对象。
 */
@Getter
@Builder
public class NotificationPageVO {

    /** 当前页号 */
    private final int pageNo;
    /** 当前页大小 */
    private final int pageSize;
    /** 匹配总数 */
    private final long total;
    /** 通知记录 */
    private final List<Item> records;

    /** 通知分页记录。 */
    @Getter
    @Builder
    public static class Item {
        /** 通知 ID */
        private final Long id;
        /** 通知类型 */
        private final String type;
        /** 关联就诊人 ID */
        private final Long patientId;
        /** 就诊人名称快照 */
        private final String patientName;
        /** 通知标题 */
        private final String title;
        /** 通知正文 */
        private final String content;
        /** 是否已读 */
        private final boolean read;
        /** 创建时间 */
        private final OffsetDateTime createdAt;
    }
}

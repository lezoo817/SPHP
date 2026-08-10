package com.sphp.patient.consultation.vo;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * C端问诊消息游标分页结果。
 */
@Getter
@Builder
public class ConsultationMessagePageVO {

    /** 按时间正序返回的消息 */
    private final List<ConsultationDetailVO.Message> messages;

    /** 当前方向仍存在更多消息时为 true */
    private final boolean hasMore;
}

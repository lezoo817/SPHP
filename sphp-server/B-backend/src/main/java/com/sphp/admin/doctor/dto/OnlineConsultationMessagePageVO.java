package com.sphp.admin.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 在线问诊消息游标分页结果。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Getter
@Builder
@Schema(description = "在线问诊消息游标分页结果")
public class OnlineConsultationMessagePageVO {

    /** 本次按时间正序返回的消息 */
    private final List<MessageVO> messages;

    /** 仍可向更早或更新方向继续查询时为 true */
    private final boolean hasMore;
}

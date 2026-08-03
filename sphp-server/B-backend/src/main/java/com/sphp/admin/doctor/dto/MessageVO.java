package com.sphp.admin.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 问诊消息 VO（系分 §5.5.6）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "问诊消息")
public class MessageVO {

    @Schema(description = "消息 ID")
    private Long messageId;

    @Schema(description = "发送方类型：PATIENT / DOCTOR / SYSTEM")
    private String senderType;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "发送时间")
    private OffsetDateTime createdAt;
}
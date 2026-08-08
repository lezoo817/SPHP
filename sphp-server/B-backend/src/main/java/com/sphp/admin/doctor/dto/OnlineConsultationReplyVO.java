package com.sphp.admin.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 在线问诊医生回复结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "在线问诊医生回复结果")
public class OnlineConsultationReplyVO {

    @Schema(description = "问诊记录 ID")
    private Long consultId;

    @Schema(description = "消息 ID")
    private Long messageId;

    @Schema(description = "问诊状态")
    private String status;

    @Schema(description = "回复时间")
    private OffsetDateTime repliedAt;
}

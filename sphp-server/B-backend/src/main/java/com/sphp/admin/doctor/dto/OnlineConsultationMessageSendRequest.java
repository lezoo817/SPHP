package com.sphp.admin.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 在线问诊医生文字消息请求。
 */
@Getter
@Setter
@Schema(description = "在线问诊医生文字消息请求")
public class OnlineConsultationMessageSendRequest {

    /** 客户端生成的消息幂等标识 */
    @NotBlank(message = "clientMessageId不能为空")
    @Size(max = 64, message = "clientMessageId不能超过64字符")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "clientMessageId格式不正确")
    @Schema(description = "客户端消息幂等标识", maxLength = 64)
    private String clientMessageId;

    /** 医生发送的文字内容 */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容不能超过2000字符")
    @Schema(description = "消息内容", maxLength = 2000)
    private String content;
}

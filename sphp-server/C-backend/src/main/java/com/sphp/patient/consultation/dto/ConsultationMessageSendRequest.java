package com.sphp.patient.consultation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 发送问诊文字消息请求参数。
 */
@Getter
@Setter
public class ConsultationMessageSendRequest {

    /** 客户端生成的消息幂等标识 */
    @NotBlank(message = "clientMessageId 不能为空")
    @Size(max = 64, message = "clientMessageId 不能超过64个字符")
    @Pattern(regexp = "[A-Za-z0-9_-]+", message = "clientMessageId 格式不正确")
    private String clientMessageId;

    /** 患者发送的文字内容 */
    @NotBlank(message = "问诊消息不能为空")
    @Size(max = 2000, message = "问诊消息不能超过2000个字符")
    private String content;
}

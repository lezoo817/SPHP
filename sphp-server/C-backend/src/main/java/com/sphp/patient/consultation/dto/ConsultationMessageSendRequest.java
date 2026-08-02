package com.sphp.patient.consultation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 发送问诊文字消息请求参数。
 */
@Getter
@Setter
public class ConsultationMessageSendRequest {

    /** 患者发送的文字内容 */
    @NotBlank(message = "问诊消息不能为空")
    @Size(max = 2000, message = "问诊消息不能超过2000个字符")
    private String content;
}

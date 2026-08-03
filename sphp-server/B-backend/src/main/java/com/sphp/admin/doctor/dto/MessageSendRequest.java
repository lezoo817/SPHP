package com.sphp.admin.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送问诊消息请求体（系分 §5.5.7）。
 */
@Data
@Schema(description = "发送问诊消息请求")
public class MessageSendRequest {

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "消息内容不能超过2000字符")
    @Schema(description = "消息内容", maxLength = 2000)
    private String content;
}
package com.sphp.admin.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 在线问诊医生回复请求。
 */
@Getter
@Setter
@Schema(description = "在线问诊医生回复请求")
public class OnlineConsultationReplyRequest {

    /** 医生回复内容。 */
    @NotBlank(message = "回复内容不能为空")
    @Size(max = 2000, message = "回复内容不能超过2000字符")
    @Schema(description = "医生回复内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;
}

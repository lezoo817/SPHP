package com.sphp.patient.consultation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 预问诊附件请求项。
 */
@Getter
@Setter
public class ConsultationAttachmentRequest {

    /** 附件展示名称 */
    @NotBlank(message = "附件名称不能为空")
    @Size(max = 255, message = "附件名称不能超过255个字符")
    private String name;

    /** 附件访问地址 */
    @NotBlank(message = "附件地址不能为空")
    @Size(max = 2048, message = "附件地址不能超过2048个字符")
    private String url;
}

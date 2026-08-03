package com.sphp.admin.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保存病历请求体（系分 §5.5.5）。
 */
@Data
@Schema(description = "保存病历请求")
public class NoteSaveRequest {

    @NotBlank(message = "病历内容不能为空")
    @Size(max = 10000, message = "病历内容不能超过10000字符")
    @Schema(description = "病历文本", maxLength = 10000)
    private String doctorNote;
}
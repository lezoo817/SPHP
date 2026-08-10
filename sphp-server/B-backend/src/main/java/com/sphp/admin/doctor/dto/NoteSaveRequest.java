package com.sphp.admin.doctor.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保存病历请求体。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@Schema(description = "保存病历请求")
public class NoteSaveRequest {

    @NotBlank(message = "病历内容不能为空")
    @Size(max = 65535, message = "病历内容不能超过65535字符")
    @Schema(description = "病历文本（纯文本，按 “主诉：xxx\\n现病史：yyy\\n查体：zzz\\n诊断：aaa\\n治疗方案：bbb” 格式拼接）", maxLength = 65535)
    private String doctorNote;
}
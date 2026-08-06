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
    @Size(max = 65535, message = "病历内容不能超过65535字符")
    @Schema(description = "病历文本（结构化 JSON，含主诉/现病史/查体/诊断/治疗方案）", maxLength = 65535)
    private String doctorNote;
}
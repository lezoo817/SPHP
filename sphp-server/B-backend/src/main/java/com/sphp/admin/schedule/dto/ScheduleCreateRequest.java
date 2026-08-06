package com.sphp.admin.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 创建排班请求。
 */
@Data
@Schema(description = "创建排班请求")
public class ScheduleCreateRequest {

    @NotNull(message = "医生ID不能为空")
    @Schema(description = "医生ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long doctorId;

    @NotBlank(message = "排班日期不能为空")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "排班日期格式应为 yyyy-MM-dd")
    @Schema(description = "排班日期 yyyy-MM-dd", example = "2026-08-15",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String scheduleDate;

    @NotBlank(message = "班次不能为空")
    @Pattern(regexp = "MORNING|AFTERNOON", message = "班次仅支持 MORNING / AFTERNOON")
    @Schema(description = "班次：MORNING / AFTERNOON", requiredMode = Schema.RequiredMode.REQUIRED)
    private String shift;

    @NotNull(message = "号源总数不能为空")
    @Min(value = 1, message = "号源总数需在 1~99 之间")
    @Max(value = 99, message = "号源总数需在 1~99 之间")
    @Schema(description = "号源总数（1~99）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer totalSlots;
}

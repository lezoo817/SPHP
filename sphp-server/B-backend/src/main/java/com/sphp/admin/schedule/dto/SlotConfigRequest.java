package com.sphp.admin.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

/**
 * 配置号源时段请求（系分 §5.4.4）。
 */
@Data
@Schema(description = "配置号源时段请求")
public class SlotConfigRequest {

    @NotEmpty(message = "时段配置不能为空")
    @Valid
    @Schema(description = "时段配置列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<SlotConfigItem> slotConfigs;

    /**
     * 单个时段配置项。
     */
    @Data
    @Schema(description = "时段配置项")
    public static class SlotConfigItem {

        @NotBlank(message = "开始时间不能为空")
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "开始时间格式应为 HH:mm")
        @Schema(description = "开始时间 HH:mm", example = "08:00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String startTime;

        @NotBlank(message = "结束时间不能为空")
        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "结束时间格式应为 HH:mm")
        @Schema(description = "结束时间 HH:mm", example = "08:30",
                requiredMode = Schema.RequiredMode.REQUIRED)
        private String endTime;

        @NotNull(message = "时段号源数不能为空")
        @Min(value = 1, message = "时段号源数至少为 1")
        @Schema(description = "该时段号源数", requiredMode = Schema.RequiredMode.REQUIRED)
        private Integer count;
    }
}

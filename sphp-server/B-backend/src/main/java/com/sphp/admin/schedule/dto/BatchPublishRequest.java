package com.sphp.admin.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量发布排班请求。
 *
 * @author lezoo17
 * @since 2026-08-07
 */
@Data
@Schema(description = "批量发布排班请求")
public class BatchPublishRequest {

    /** 单次批量上限，防止误操作拖垮发布链路（号源快照生成、Redis 缓存初始化） */
    public static final int MAX_IDS = 200;

    @NotEmpty(message = "排班ID列表不能为空")
    @Size(max = MAX_IDS, message = "单次最多发布 " + MAX_IDS + " 条")
    @Schema(description = "待发布的排班 ID 列表（仅 DRAFT 可成功发布，其他计入失败）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> scheduleIds;
}

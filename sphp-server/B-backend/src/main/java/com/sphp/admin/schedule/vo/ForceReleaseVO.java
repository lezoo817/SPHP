package com.sphp.admin.schedule.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 手动释放锁定号源响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "手动释放锁定号源响应")
public class ForceReleaseVO {

    @Schema(description = "号源快照ID")
    private Long slotId;

    @Schema(description = "状态：AVAILABLE（释放后回到可约池）")
    private String status;

    @Schema(description = "释放时间")
    private OffsetDateTime releasedAt;
}

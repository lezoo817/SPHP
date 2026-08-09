package com.sphp.admin.prescription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 处方模板列表项 VO。
 *
 * @author lezoo17
 * @since 2026-08-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "处方模板列表项")
public class TemplateListVO {

    @Schema(description = "模板 ID")
    private Long id;

    @Schema(description = "模板名称")
    private String name;

    @Schema(description = "关联科室 ID")
    private Long deptId;

    @Schema(description = "科室名称")
    private String deptName;

    @Schema(description = "更新人姓名")
    private String updatedByName;

    @Schema(description = "药品项数")
    private Integer itemCount;

    @Schema(description = "药品明细")
    private List<TemplateItemDTO> items;

    @Schema(description = "创建时间")
    private OffsetDateTime createdAt;

    @Schema(description = "更新时间")
    private OffsetDateTime updatedAt;
}
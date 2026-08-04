package com.sphp.admin.hospital.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 科室列表项响应（系分 §5.3.3）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "科室列表项")
public class DepartmentListVO {

    @Schema(description = "科室ID")
    private Long id;

    @Schema(description = "科室名称")
    private String name;

    @Schema(description = "所属医院ID")
    private Long hospitalId;

    @Schema(description = "科室负责人医生ID")
    private Long headDoctorId;

    @Schema(description = "科室负责人姓名")
    private String headDoctorName;

    @Schema(description = "科室位置（如：1号楼2层201室）")
    private String location;

    @Schema(description = "状态：ENABLED / DISABLED")
    private String status;
}

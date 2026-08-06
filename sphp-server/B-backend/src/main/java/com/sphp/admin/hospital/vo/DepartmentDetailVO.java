package com.sphp.admin.hospital.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 科室详情响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "科室详情")
public class DepartmentDetailVO {

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

    @Schema(description = "科室下医生数量")
    private long doctorCount;

    @Schema(description = "创建时间")
    private OffsetDateTime createdAt;
}

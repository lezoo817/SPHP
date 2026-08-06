package com.sphp.admin.hospital.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 医院信息响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "医院信息")
public class HospitalVO {

    @Schema(description = "医院ID")
    private Long id;

    @Schema(description = "医院名称")
    private String name;

    @Schema(description = "医院等级")
    private String level;

    @Schema(description = "医院简介")
    private String description;

    @Schema(description = "地址")
    private String address;

    @Schema(description = "联系方式")
    private String contact;

    @Schema(description = "状态：ENABLED / DISABLED")
    private String status;
}

package com.sphp.admin.hospital.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 编辑医院信息请求。
 *
 * <p>字段均可空，仅更新传入的非空值。
 */
@Data
@Schema(description = "编辑医院信息请求")
public class HospitalUpdateRequest {

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
}

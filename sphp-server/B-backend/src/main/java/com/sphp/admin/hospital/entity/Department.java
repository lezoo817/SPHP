package com.sphp.admin.hospital.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 科室表实体（对应表 department）。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Data
@TableName("department")
public class Department {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属医院 */
    private Long hospitalId;

    /** 科室名称 */
    private String name;

    /** 科室负责人医生 ID */
    private Long headDoctorId;

    /** 科室位置（如：1号楼2层201室） */
    private String location;

    /** 状态：ENABLED / DISABLED */
    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    /** 软删除时间（null 表示有效） */
    private OffsetDateTime deletedAt;
}

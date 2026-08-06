package com.sphp.admin.prescription.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.sphp.admin.prescription.dto.TemplateItemDTO;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 处方模板表实体（对应表 prescription_template）。
 */
@Data
@TableName(value = "prescription_template", autoResultMap = true)
public class PrescriptionTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属医院 */
    private Long hospitalId;

    /** 关联科室，NULL 表示全院通用 */
    private Long deptId;

    /** 模板名称 */
    private String name;

    /** 创建人 */
    private Long doctorId;

    /** 更新人（为空时与 doctorId 相同） */
    private Long updatedBy;

    /** 药品明细 JSON */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<TemplateItemDTO> items;

    /** 状态：ENABLED / DISABLED */
    private String status;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

    private OffsetDateTime deletedAt;
}
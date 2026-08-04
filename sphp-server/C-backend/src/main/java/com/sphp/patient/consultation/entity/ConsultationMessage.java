package com.sphp.patient.consultation.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 问诊文字消息实体。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("consultation_message")
public class ConsultationMessage {

    /** 主键 ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 关联问诊记录 ID */
    @TableField("consult_id")
    private Long consultationId;

    /** 消息发送方类型 */
    @TableField("sender_type")
    private String senderType;

    /** 文字消息内容 */
    @TableField("content")
    private String content;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    /** 软删除时间 */
    @TableLogic
    @TableField("deleted_at")
    private OffsetDateTime deletedAt;
}

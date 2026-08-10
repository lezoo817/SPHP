package com.sphp.admin.doctor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 问诊消息表实体（对应表 consultation_message）。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("consultation_message")
public class ConsultationMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 问诊记录 ID */
    private Long consultId;

    /** 发送方类型：PATIENT / DOCTOR / SYSTEM */
    private String senderType;

    /** 消息内容（最大 2000 字符） */
    private String content;

    /** 客户端消息幂等标识 */
    private String clientMessageId;

    /** 消息类型，首期固定为 TEXT */
    private String messageType;

    private OffsetDateTime createdAt;

    private OffsetDateTime deletedAt;
}

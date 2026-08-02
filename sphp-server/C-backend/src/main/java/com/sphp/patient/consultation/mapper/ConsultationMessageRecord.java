package com.sphp.patient.consultation.mapper;

import java.time.OffsetDateTime;

/**
 * 问诊文字消息投影。
 *
 * @param id 消息 ID
 * @param senderType 发送方类型
 * @param content 消息内容
 * @param createdAt 创建时间
 */
public record ConsultationMessageRecord(Long id, String senderType, String content, OffsetDateTime createdAt) {
}

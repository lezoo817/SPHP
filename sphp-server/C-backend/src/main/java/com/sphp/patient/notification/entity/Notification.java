package com.sphp.patient.notification.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sphp.shared.entity.BaseDeleteDO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * C端站内通知实体。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("notification")
public class Notification extends BaseDeleteDO {

    /** 接收通知的 C端用户 ID */
    @TableField("user_id")
    private Long userId;
    /** 可选关联就诊人 ID */
    @TableField("patient_id")
    private Long patientId;
    /** 就诊人名称快照 */
    @TableField("patient_name_snapshot")
    private String patientNameSnapshot;
    /** 通知类型 */
    @TableField("type")
    private String type;
    /** 通知标题 */
    @TableField("title")
    private String title;
    /** 通知正文 */
    @TableField("content")
    private String content;
    /** 脱敏业务扩展信息 JSON */
    @TableField("payload")
    private String payload;
    /** 消息队列事件 ID，历史通知可为空 */
    @TableField("event_id")
    private String eventId;
    /** 首次已读时间 */
    @TableField("read_at")
    private OffsetDateTime readAt;
}

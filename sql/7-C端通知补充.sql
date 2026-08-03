-- C端通知补充：本脚本在既有 C端补充脚本后执行，可重复运行。

-- RabbitMQ 事件唯一标识，用于同一用户的重复消息幂等消费；历史通知保持为空。
ALTER TABLE notification ADD COLUMN IF NOT EXISTS event_id varchar(64);

-- 同一事件只能向同一 C端用户创建一条通知，支持同一就诊人关联多个账号。
CREATE UNIQUE INDEX IF NOT EXISTS uk_notification_event_user
    ON notification(event_id, user_id)
    WHERE event_id IS NOT NULL;

-- 在线问诊实时文本聊天迁移。
-- 为既有消息补充客户端幂等标识和消息类型；历史数据默认视为 TEXT。
ALTER TABLE consultation_message
    ADD COLUMN IF NOT EXISTS client_message_id varchar(64);

ALTER TABLE consultation_message
    ADD COLUMN IF NOT EXISTS message_type varchar(20) NOT NULL DEFAULT 'TEXT';

-- 首期只允许文本消息，附件、撤回和已读回执将在后续独立迁移中支持。
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_consultation_message_type'
    ) THEN
        ALTER TABLE consultation_message
            ADD CONSTRAINT ck_consultation_message_type CHECK (message_type IN ('TEXT'));
    END IF;
END $$;

-- 同一问诊内客户端重试使用相同标识时只能落一条有效消息。
CREATE UNIQUE INDEX IF NOT EXISTS uq_consultation_message_client
    ON consultation_message (consult_id, client_message_id)
    WHERE client_message_id IS NOT NULL AND deleted_at IS NULL;

-- 断线重连按消息 ID 游标补拉，避免按时间戳产生排序歧义。
CREATE INDEX IF NOT EXISTS idx_consultation_message_consult_id
    ON consultation_message (consult_id, id)
    WHERE deleted_at IS NULL;

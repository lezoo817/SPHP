-- C端挂号候补通知补充：在既有候补表创建后运行，可重复执行。

-- 记录候补登记账号，确保后续可预约通知只发送给实际登记人。
ALTER TABLE appointment_waitlist
    ADD COLUMN IF NOT EXISTS user_id bigint REFERENCES c_user(id);

-- 记录候补通知与候补履约时间，用于15分钟通知窗口和状态审计。
ALTER TABLE appointment_waitlist
    ADD COLUMN IF NOT EXISTS notified_at timestamptz;
ALTER TABLE appointment_waitlist
    ADD COLUMN IF NOT EXISTS fulfilled_at timestamptz;

-- 旧记录没有登记账号，无法安全确定通知接收人，统一结束其活跃候补状态。
UPDATE appointment_waitlist
SET status = 'EXPIRED', updated_at = now()
WHERE user_id IS NULL
  AND status IN ('WAITING', 'NOTIFIED')
  AND deleted_at IS NULL;

-- 增加已履约状态，区分候补人成功预约与主动取消、超时过期。
ALTER TABLE appointment_waitlist DROP CONSTRAINT IF EXISTS ck_waitlist_status;
ALTER TABLE appointment_waitlist
    ADD CONSTRAINT ck_waitlist_status
        CHECK (status IN ('WAITING', 'NOTIFIED', 'FULFILLED', 'CANCELLED', 'EXPIRED'));

-- 活跃候补必须绑定登记账号，避免后续向错误账号发送可预约通知。
ALTER TABLE appointment_waitlist DROP CONSTRAINT IF EXISTS ck_waitlist_active_user;
ALTER TABLE appointment_waitlist
    ADD CONSTRAINT ck_waitlist_active_user
        CHECK (status NOT IN ('WAITING', 'NOTIFIED') OR user_id IS NOT NULL);

-- 候补通知超时扫描按状态和通知时间检索，避免全表扫描。
CREATE INDEX IF NOT EXISTS idx_waitlist_notified_deadline
    ON appointment_waitlist(status, notified_at)
    WHERE deleted_at IS NULL AND status = 'NOTIFIED';

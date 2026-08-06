-- ============================================================
-- C 端手动开启处方频次用药提醒补充脚本
-- 执行环境：PostgreSQL
-- 用途：保存用户提醒开关及按处方频次生成的每日提醒时刻快照
-- 特性：可重复执行；历史用药计划默认不自动开启提醒
-- ============================================================

-- 用户手动开启后才允许扫描用药计划，默认 false 保证历史计划不会自动收到提醒。
ALTER TABLE medication_plan
    ADD COLUMN IF NOT EXISTS reminder_enabled boolean NOT NULL DEFAULT false;

-- 保存根据处方频次生成的每日 HH:mm 时刻数组，避免映射规则升级影响已开启计划。
ALTER TABLE medication_plan
    ADD COLUMN IF NOT EXISTS reminder_times jsonb;

-- 仅索引实际参与到期扫描的计划，降低定时扫描查询成本。
CREATE INDEX IF NOT EXISTS idx_medication_plan_reminder_due
    ON medication_plan(next_remind_at)
    WHERE status = 'ACTIVE' AND reminder_enabled = true AND deleted_at IS NULL;

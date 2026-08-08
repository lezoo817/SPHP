-- ============================================================
-- 在线问诊回复与查询优化
-- 执行环境：PostgreSQL
-- 用途：支持无挂号在线问诊的一次性医生回复和 B 端状态列表查询
-- 特性：可重复执行，不影响既有挂号接诊数据
-- ============================================================

-- 记录医生最终回复时间；非空表示在线问诊已完成一次性回复。
ALTER TABLE consult_record
    ADD COLUMN IF NOT EXISTS doctor_replied_at timestamptz;

-- 支撑医生按状态和提交时间分页查询无挂号在线问诊。
CREATE INDEX IF NOT EXISTS idx_consult_record_online_doctor_status
    ON consult_record(doctor_id, status, pre_consultation_submitted_at DESC, id DESC)
    WHERE appointment_id IS NULL AND deleted_at IS NULL;


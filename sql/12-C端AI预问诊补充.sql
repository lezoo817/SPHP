-- ============================================================
-- C 端 AI 预问诊直接提交补充脚本
-- 执行环境：PostgreSQL
-- 执行时机：在基础建表及既有 C 端问诊补充脚本之后执行
-- 用途：支持无挂号订单的本人 AI 预问诊直接写入 consult_record
-- 特性：可重复执行，不删除历史挂号问诊和草稿数据
-- ============================================================

-- 预问诊可由 AI 对话直接提交，不再强制关联挂号订单；保留外键和唯一约束兼容历史数据。
ALTER TABLE consult_record
    ALTER COLUMN appointment_id DROP NOT NULL;

-- 支撑按本人患者、医生和活动状态检查是否存在待接诊或进行中的预问诊。
CREATE INDEX IF NOT EXISTS idx_consult_record_patient_doctor_status
    ON consult_record(patient_id, doctor_id, status)
    WHERE deleted_at IS NULL;

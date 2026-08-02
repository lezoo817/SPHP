-- C端问诊补充：支持预问诊草稿及提交时间，脚本可重复执行。

-- 记录预问诊首次提交为待接诊的时间；草稿阶段保持为空。
ALTER TABLE consult_record
    ADD COLUMN IF NOT EXISTS pre_consultation_submitted_at timestamptz;

-- 预问诊草稿需要使用 DRAFT 状态，保留现有问诊生命周期状态。
ALTER TABLE consult_record DROP CONSTRAINT IF EXISTS ck_consult_record_status;
ALTER TABLE consult_record
    ADD CONSTRAINT ck_consult_record_status
        CHECK (status IN ('DRAFT', 'PENDING', 'IN_PROGRESS', 'COMPLETED', 'NO_SHOW'));

-- 按患者分页查询问诊记录的访问路径索引。
CREATE INDEX IF NOT EXISTS idx_consult_record_patient_updated
    ON consult_record(patient_id, updated_at DESC, id DESC)
    WHERE deleted_at IS NULL;

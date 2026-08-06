-- ============================================================
-- 处方模板更新功能：补充 updated_by 字段
-- 来源：B端后端系分 §5.6.8
-- 说明：记录最后更新人，创建时与 doctor_id 一致，更新时记录当前操作人
-- ============================================================

-- 5.1 新增 updated_by 字段（可为空，创建时由代码填充）
ALTER TABLE prescription_template
    ADD COLUMN IF NOT EXISTS updated_by bigint REFERENCES doctor(id);

-- 5.2 存量数据：updated_by 取 doctor_id 值
UPDATE prescription_template SET updated_by = doctor_id WHERE updated_by IS NULL;

-- 5.3 补充索引
CREATE INDEX IF NOT EXISTS idx_template_updated_by
    ON prescription_template(updated_by) WHERE deleted_at IS NULL;
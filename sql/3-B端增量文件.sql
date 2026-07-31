-- ============================================================
-- 数据库增量修改脚本
-- 执行环境：PostgreSQL
-- 执行时机：在 1-初始创表.sql 和 2-C端创表补充.sql 执行之后
-- 目的：对齐 B端后端系分 V1.3 中的数据库设计
-- 特性：仅新增字段/索引/约束，不删表、不改已有字段类型
-- 可重复执行（均使用 IF NOT EXISTS / 条件判断）
-- ============================================================

-- ============================================================
-- 1. 排班表（schedule）补充 dept_id 字段及索引
-- 来源：B端后端系分 §4.2.3
-- ============================================================

-- 1.1 新增 dept_id 字段（冗余科室ID，支撑按日期+科室联合查询）
ALTER TABLE schedule ADD COLUMN IF NOT EXISTS dept_id bigint;

-- 1.2 为已有排班数据回填 dept_id（从 doctor 表关联获取）
-- 注意：若 schedule.doctor_id 对应的 doctor 记录不存在或 doctor.dept_id 为 NULL，
-- 此 UPDATE 不会覆盖已有值，仅填充空值。
UPDATE schedule
SET dept_id = doctor.dept_id
    FROM doctor
WHERE schedule.doctor_id = doctor.id
  AND schedule.dept_id IS NULL;

-- 1.3 新增外键约束（如果尚不存在）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_schedule_dept') THEN
ALTER TABLE schedule ADD CONSTRAINT fk_schedule_dept
    FOREIGN KEY (dept_id) REFERENCES department(id);
END IF;
END $$;

-- 1.4 新增联合索引，支撑锁定号源看板按日期+科室的高频筛选
CREATE INDEX IF NOT EXISTS idx_schedule_date_dept
    ON schedule(schedule_date, dept_id) WHERE deleted_at IS NULL;


-- ============================================================
-- 2. 医生表（doctor）补充 b_user_id 字段及索引
-- 来源：B端后端系分 §4.2.2
-- ============================================================

-- 2.1 新增 b_user_id 字段（关联 b_user.id，便于 doctor → b_user 快速联查）
ALTER TABLE doctor ADD COLUMN IF NOT EXISTS b_user_id bigint;

-- 2.2 为已有医生数据回填 b_user_id（从 b_user 表反向关联）
-- 注意：仅当 b_user.doctor_id = doctor.id 时关联
UPDATE doctor
SET b_user_id = b_user.id
    FROM b_user
WHERE doctor.id = b_user.doctor_id
  AND doctor.b_user_id IS NULL;

-- 2.3 新增外键约束（如果尚不存在）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_doctor_b_user') THEN
ALTER TABLE doctor ADD CONSTRAINT fk_doctor_b_user
    FOREIGN KEY (b_user_id) REFERENCES b_user(id);
END IF;
END $$;

-- 2.4 新增索引
CREATE INDEX IF NOT EXISTS idx_doctor_b_user
    ON doctor(b_user_id) WHERE b_user_id IS NOT NULL;


-- ============================================================
-- 3. 处方模板表（prescription_template）补充 dept_id 字段说明
-- 来源：B端后端系分 §4.2.5
-- 说明：该字段已在初始建表SQL中存在，此处仅补充索引（如未创建）
-- ============================================================

-- 3.1 确保 dept_id 索引存在（如果尚未创建）
CREATE INDEX IF NOT EXISTS idx_template_dept
    ON prescription_template(dept_id) WHERE deleted_at IS NULL;


-- ============================================================
-- 4. B端刷新令牌表（b_refresh_token）补充索引
-- 来源：B端后端系分 §4.2.6
-- 说明：表已在初始建表SQL中存在，补充索引
-- ============================================================

-- 4.1 新增索引
CREATE INDEX IF NOT EXISTS idx_b_refresh_token_user
    ON b_refresh_token(user_id) WHERE revoked_at IS NULL;


-- ============================================================
-- 5. 问诊记录表（consult_record）补充 ai_summary 字段说明
-- 来源：B端后端系分 §4.2.4
-- 说明：字段已存在，无需DDL变更。仅在本文档中说明：
--      ai_summary 仅包含 chiefComplaint（主诉）、symptoms（症状）、allergies（过敏史）
--      不包含 riskLevel（B端不参与AI风险评级）
-- 本脚本不对此字段做任何结构变更。
-- ============================================================


-- ============================================================
-- 6. 双向外键循环引用处理（b_user ↔ doctor）
-- 来源：总后端系分 §6.4.1、§6.4.3
-- 说明：初始建表SQL中 b_user 和 doctor 互有外键引用。
--       建表时因循环引用无法同时添加外键，需在建库完成后补充。
-- ============================================================

-- 6.1 确保 b_user.doctor_id 外键约束存在
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_b_user_doctor') THEN
ALTER TABLE b_user ADD CONSTRAINT fk_b_user_doctor
    FOREIGN KEY (doctor_id) REFERENCES doctor(id);
END IF;
END $$;

-- 6.2 确保 doctor.b_user_id 外键约束存在（§2.1 中已添加，此处检查）
-- 注：2.3 已添加 fk_doctor_b_user，无需重复


-- ============================================================
-- 7. 处方表（prescription）补充索引
-- 来源：B端后端系分 §4.2.5
-- ============================================================

-- 7.1 确保索引存在
CREATE INDEX IF NOT EXISTS idx_prescription_consult
    ON prescription(consult_id) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_prescription_patient
    ON prescription(patient_id) WHERE deleted_at IS NULL;


-- ============================================================
-- 8. 处方明细表（prescription_item）补充索引
-- 来源：B端后端系分 §4.2.5
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_prescription_item_prescription
    ON prescription_item(prescription_id);


-- ============================================================
-- 9. 药房药品库存表（pharmacy_drug_stock）补充索引
-- 来源：B端后端系分 §4.2.5
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_stock_pharmacy_drug
    ON pharmacy_drug_stock(pharmacy_id, drug_id);

CREATE INDEX IF NOT EXISTS idx_stock_alert
    ON pharmacy_drug_stock(available_count, safety_stock);


-- ============================================================
-- 10. 问诊记录表（consult_record）补充索引
-- 来源：B端后端系分 §4.2.4
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_consult_record_patient
    ON consult_record(patient_id) WHERE deleted_at IS NULL;


-- ============================================================
-- 11. 号源快照表（slot_snapshot）补充索引
-- 来源：B端后端系分 §4.2.3
-- 说明：初始建表SQL中已包含 idx_slot_snapshot_slot_status，此索引已存在，无需重复创建
-- ============================================================


-- ============================================================
-- 执行完成
-- 验证方式：执行以下查询确认变更是否生效
-- ============================================================
-- SELECT column_name FROM information_schema.columns WHERE table_name = 'schedule' AND column_name = 'dept_id';
-- SELECT column_name FROM information_schema.columns WHERE table_name = 'doctor' AND column_name = 'b_user_id';
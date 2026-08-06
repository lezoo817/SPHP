-- ============================================================
-- 接诊与问诊流程测试数据
-- 执行环境：PostgreSQL
-- 用途：测试 B端接诊台全流程
-- 说明：基于数据库现有数据编写（已有医院/科室/医生/用户/排班/号源）
--       仅插入测试数据，不改表结构
-- ============================================================

BEGIN;

-- ----------------------------
-- 1. 统一更新所有账号密码为 123456（BCrypt hash）
-- ----------------------------
UPDATE b_user
SET password_hash = '$2a$10$r.5ZDL/ASg2aIJ4QCapUNOn3j8Q95QrKVrPkviR6tOhpg3gtvdQom',
    updated_at = now()
WHERE status = 'ENABLED';

-- ----------------------------
-- 2. 插入测试患者
--    patient 表无数据，需插入一条
-- ----------------------------
INSERT INTO patient (id, name, gender, date_of_birth, emergency_contact, created_at, updated_at)
VALUES (1, '张三', 'MALE', '1990-01-01', '李四 13800000000', now(), now())
ON CONFLICT (id) DO NOTHING;

-- ----------------------------
-- 3. 插入 slot_snapshot（LOCKED 状态）
--    使用 slot_id=2（schedule_id=2，doctor_id=4 妇科圣手的号源时段）
-- ----------------------------
INSERT INTO slot_snapshot (id, slot_id, patient_id, status, locked_at, created_at, updated_at)
VALUES (1, 2, 1, 'LOCKED', now(), now(), now())
ON CONFLICT (id) DO NOTHING;

-- ----------------------------
-- 4. 插入 appointment（PAID 状态）
--    amount_cent=0 作为测试数据（实际为挂号费）
-- ----------------------------
INSERT INTO appointment (id, slot_snapshot_id, patient_id, doctor_id, status, amount_cent, paid_at, created_at, updated_at)
VALUES (1, 1, 1, 4, 'PAID', 0, now(), now(), now())
ON CONFLICT (id) DO NOTHING;

-- ----------------------------
-- 5. 插入 consult_record（PENDING 状态）
--    关联 doctor_id=4（妇科圣手/doctor_master）、patient_id=1（张三）
-- ----------------------------
INSERT INTO consult_record (id, appointment_id, doctor_id, patient_id, status, created_at, updated_at)
VALUES (1, 1, 4, 1, 'PENDING', now(), now())
ON CONFLICT (id) DO NOTHING;

-- ----------------------------
-- 6. 插入患者过敏史（用于测试处方风险拦截）
--    患者张三对青霉素过敏
-- ----------------------------
INSERT INTO patient_allergy (id, patient_id, allergen, reaction, severity, created_at, updated_at)
VALUES (1, 1, '青霉素', '皮疹、呼吸困难', 'MODERATE', now(), now())
ON CONFLICT (id) DO NOTHING;

-- ----------------------------
-- 7. 插入药品（用于测试处方提交）
--    阿莫西林（含青霉素成分，会触发过敏红线）
--    布洛芬（正常药品，可正常开具）
--    注意 uk_drug_hospital_approval 唯一约束
-- ----------------------------
INSERT INTO drug (id, hospital_id, name, specification, manufacturer, approval_number, unit, indication, contraindication, status, created_at, updated_at)
VALUES
    (1, 1, '阿莫西林胶囊', '0.25g*24粒', '某制药厂', '国药准字H12345678', '盒', '用于敏感菌引起的呼吸道感染', '青霉素过敏者禁用', 'ENABLED', now(), now()),
    (2, 1, '布洛芬片', '0.2g*100片', '某药厂', '国药准字H87654321', '瓶', '用于缓解轻至中度疼痛', NULL, 'ENABLED', now(), now())
ON CONFLICT (id) DO NOTHING;

-- ----------------------------
-- 8. 重置所有主键序列到当前最大值
-- ----------------------------
SELECT setval(pg_get_serial_sequence('patient', 'id'), COALESCE((SELECT MAX(id) FROM patient), 1));
SELECT setval(pg_get_serial_sequence('slot_snapshot', 'id'), COALESCE((SELECT MAX(id) FROM slot_snapshot), 1));
SELECT setval(pg_get_serial_sequence('appointment', 'id'), COALESCE((SELECT MAX(id) FROM appointment), 1));
SELECT setval(pg_get_serial_sequence('consult_record', 'id'), COALESCE((SELECT MAX(id) FROM consult_record), 1));
SELECT setval(pg_get_serial_sequence('patient_allergy', 'id'), COALESCE((SELECT MAX(id) FROM patient_allergy), 1));
SELECT setval(pg_get_serial_sequence('drug', 'id'), COALESCE((SELECT MAX(id) FROM drug), 1));

COMMIT;
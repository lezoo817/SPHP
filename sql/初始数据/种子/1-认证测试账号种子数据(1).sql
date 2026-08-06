BEGIN;

-- ----------------------------
-- 1. 医院
-- ----------------------------
INSERT INTO hospital (id, name, level, description, address, contact, status, created_at, updated_at)
VALUES (1, '测试医院', '三级甲等', '综合性教学医院，提供全科医疗服务', '北京市朝阳区建国路88号', '010-12345678', 'ENABLED', now(), now());

-- ----------------------------
-- 2. 科室
-- ----------------------------
INSERT INTO department (id, hospital_id, name, location, status, created_at, updated_at)
VALUES
    (1, 1, '内科', '心血管、呼吸、消化等内科学科', 'ENABLED', now(), now()),
    (2, 1, '外科', '普外、骨科、泌尿外等外科学科', 'ENABLED', now(), now());

-- ----------------------------
-- 3. 医生
-- ----------------------------
INSERT INTO doctor (id, hospital_id, dept_id, name, title, specialty, introduction, license_no, phone, registration_fee_cent, status, created_at, updated_at)
VALUES
    (1, 1, 1, '张医生', '主任医师', '擅长心血管疾病及高血压、冠心病介入治疗', '从事心内科临床工作20余年，发表SCI论文10余篇', 'LIC20240001', '13800001001', 1000, 'ENABLED', now(), now()),
    (2, 1, 2, '李医生', '副主任医师', '擅长骨科创伤、关节置换及脊柱微创手术', '骨科资深专家，曾赴德国进修', 'LIC20240002', '13800001002', 800, 'ENABLED', now(), now());

-- ----------------------------
-- 4. B端用户
-- ----------------------------
INSERT INTO b_user (id, account, password_hash, role, hospital_id, doctor_id, status, created_at, updated_at)
VALUES
    (1, 'admin', '$2a$10$r.5ZDL/ASg2aIJ4QCapUNOn3j8Q95QrKVrPkviR6tOhpg3gtvdQom', 'ADMIN', 1, NULL, 'ENABLED', now(), now()),
    (2, 'head_internal', '$2a$10$r.5ZDL/ASg2aIJ4QCapUNOn3j8Q95QrKVrPkviR6tOhpg3gtvdQom', 'DEPT_HEAD', 1, 1, 'ENABLED', now(), now()),
    (3, 'doctor_li', '$2a$10$r.5ZDL/ASg2aIJ4QCapUNOn3j8Q95QrKVrPkviR6tOhpg3gtvdQom', 'DOCTOR', 1, 2, 'ENABLED', now(), now());

-- ----------------------------
-- 5. 更新医生表中的 b_user_id
-- ----------------------------
UPDATE doctor SET b_user_id = 2 WHERE id = 1;
UPDATE doctor SET b_user_id = 3 WHERE id = 2;

-- ----------------------------
-- 6. 重置所有主键序列到当前最大值
-- ----------------------------
SELECT setval(pg_get_serial_sequence('hospital', 'id'), COALESCE((SELECT MAX(id) FROM hospital), 1));
SELECT setval(pg_get_serial_sequence('department', 'id'), COALESCE((SELECT MAX(id) FROM department), 1));
SELECT setval(pg_get_serial_sequence('doctor', 'id'), COALESCE((SELECT MAX(id) FROM doctor), 1));
SELECT setval(pg_get_serial_sequence('b_user', 'id'), COALESCE((SELECT MAX(id) FROM b_user), 1));

COMMIT;

-- ============================================================
-- 验证查询
-- ============================================================
-- 查看序列当前值（验证是否重置成功）
SELECT
    currval(pg_get_serial_sequence('hospital', 'id')) as hospital_seq,
    currval(pg_get_serial_sequence('department', 'id')) as dept_seq,
    currval(pg_get_serial_sequence('doctor', 'id')) as doctor_seq,
    currval(pg_get_serial_sequence('b_user', 'id')) as b_user_seq;
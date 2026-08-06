-- ============================================================
-- 17 个科室 + 每科室一名医生种子数据
-- 用途：支撑预问诊联调（LLM 科室推荐 keyword 能命中 query_departments）
-- 环境：PostgreSQL（sphp 库）
-- 说明：hospital_id=1（测试医院），id 用 100-116 避免与现有 1-5 冲突；
--       15 常见科室（id 109 为妇科，原"妇产科"更名）+ 男科/中医科（id 115/116）；
--       去掉了内科/外科（种子 id 1/2）；
--       ON CONFLICT DO NOTHING，重复执行安全
-- ============================================================

BEGIN;

-- ----------------------------
-- 1. 15 个常见科室
-- ----------------------------
INSERT INTO department (id, hospital_id, name, location, status, created_at, updated_at)
VALUES
    (100, 1, '全科',         '1号楼1层101室', 'ENABLED', now(), now()),
    (101, 1, '呼吸内科',     '1号楼2层201室', 'ENABLED', now(), now()),
    (102, 1, '消化内科',     '1号楼2层202室', 'ENABLED', now(), now()),
    (103, 1, '心血管内科',   '1号楼2层203室', 'ENABLED', now(), now()),
    (104, 1, '神经内科',     '1号楼2层204室', 'ENABLED', now(), now()),
    (105, 1, '内分泌科',     '1号楼2层205室', 'ENABLED', now(), now()),
    (106, 1, '普通外科',     '1号楼3层301室', 'ENABLED', now(), now()),
    (107, 1, '骨科',         '1号楼3层302室', 'ENABLED', now(), now()),
    (108, 1, '泌尿外科',     '1号楼3层303室', 'ENABLED', now(), now()),
    (109, 1, '妇产科',       '1号楼4层401室', 'ENABLED', now(), now()),
    (110, 1, '儿科',         '1号楼4层402室', 'ENABLED', now(), now()),
    (111, 1, '眼科',         '1号楼5层501室', 'ENABLED', now(), now()),
    (112, 1, '耳鼻喉科',     '1号楼5层502室', 'ENABLED', now(), now()),
    (113, 1, '口腔科',       '1号楼5层503室', 'ENABLED', now(), now()),
    (114, 1, '皮肤科',       '1号楼6层601室', 'ENABLED', now(), now())
ON CONFLICT (id) DO NOTHING;

-- ----------------------------
-- 1b. 妇产科更名为妇科（id=109）；新增男科/中医科（id 115/116）
-- ----------------------------
UPDATE department SET name = '妇科', updated_at = now() WHERE id = 109 AND name = '妇产科';
INSERT INTO department (id, hospital_id, name, location, status, created_at, updated_at)
VALUES
    (115, 1, '男科',   '1号楼6层602室', 'ENABLED', now(), now()),
    (116, 1, '中医科', '1号楼6层603室', 'ENABLED', now(), now())
ON CONFLICT (id) DO NOTHING;

-- ----------------------------
-- 2. 每科室一名医生（dept_id 对应科室 id）
-- ----------------------------
INSERT INTO doctor (id, hospital_id, dept_id, name, title, specialty, introduction, license_no, phone, registration_fee_cent, status, created_at, updated_at)
VALUES
    (100, 1, 100, '全医生', '主治医师',   '全科医学诊疗、常见病与多发病诊治',           '从事全科医疗工作10余年',                       'LIC20240100', '13800000100', 500,  'ENABLED', now(), now()),
    (101, 1, 101, '王医生', '主任医师',   '呼吸系统疾病、哮喘、慢性阻塞性肺病',         '呼吸内科专家，擅长疑难呼吸病诊治',             'LIC20240101', '13800000101', 1000, 'ENABLED', now(), now()),
    (102, 1, 102, '李医生', '副主任医师', '消化系统疾病、胃肠病、肝胆胰疾病',           '消化内科资深医师',                             'LIC20240102', '13800000102', 800,  'ENABLED', now(), now()),
    (103, 1, 103, '张医生', '主任医师',   '心血管疾病及高血压、冠心病介入治疗',         '从事心内科临床工作20余年，发表SCI论文10余篇',   'LIC20240103', '13800000103', 1000, 'ENABLED', now(), now()),
    (104, 1, 104, '刘医生', '副主任医师', '脑血管病、头痛、眩晕、癫痫、失眠',           '神经内科专家',                                 'LIC20240104', '13800000104', 800,  'ENABLED', now(), now()),
    (105, 1, 105, '陈医生', '主治医师',   '糖尿病、甲状腺疾病、肥胖与代谢病',           '内分泌科医师',                                 'LIC20240105', '13800000105', 500,  'ENABLED', now(), now()),
    (106, 1, 106, '赵医生', '主任医师',   '普外科疾病、阑尾炎、胆囊疾病、疝',           '普外科专家',                                   'LIC20240106', '13800000106', 1000, 'ENABLED', now(), now()),
    (107, 1, 107, '孙医生', '副主任医师', '骨科创伤、关节置换、脊柱疾病',               '骨科资深医师',                                 'LIC20240107', '13800000107', 800,  'ENABLED', now(), now()),
    (108, 1, 108, '周医生', '主治医师',   '泌尿系统结石、前列腺疾病、泌尿系肿瘤',       '泌尿外科医师',                                 'LIC20240108', '13800000108', 500,  'ENABLED', now(), now()),
    (109, 1, 109, '吴医生', '主任医师',   '妇科疾病、产科、孕产保健',                   '妇产科专家',                                   'LIC20240109', '13800000109', 1000, 'ENABLED', now(), now()),
    (110, 1, 110, '郑医生', '副主任医师', '儿科常见病、儿童保健、新生儿疾病',           '儿科资深医师',                                 'LIC20240110', '13800000110', 800,  'ENABLED', now(), now()),
    (111, 1, 111, '钱医生', '主治医师',   '眼科疾病、近视、白内障、青光眼',             '眼科医师',                                     'LIC20240111', '13800000111', 500,  'ENABLED', now(), now()),
    (112, 1, 112, '冯医生', '副主任医师', '耳鼻喉疾病、鼻炎、咽喉炎、中耳炎',           '耳鼻喉科医师',                                 'LIC20240112', '13800000112', 800,  'ENABLED', now(), now()),
    (113, 1, 113, '杨医生', '主治医师',   '口腔疾病、牙体牙髓、口腔修复、种植',         '口腔科医师',                                   'LIC20240113', '13800000113', 500,  'ENABLED', now(), now()),
    (114, 1, 114, '朱医生', '副主任医师', '皮肤病、湿疹、皮炎、银屑病',                 '皮肤科医师',                                   'LIC20240114', '13800000114', 800,  'ENABLED', now(), now()),
    (115, 1, 115, '何医生', '副主任医师', '男性不育、前列腺疾病、性功能障碍',           '男科专家',                                     'LIC20240115', '13800000115', 800,  'ENABLED', now(), now()),
    (116, 1, 116, '叶医生', '主治医师',   '中医内科、针灸、推拿、亚健康调理',           '中医科医师',                                   'LIC20240116', '13800000116', 500,  'ENABLED', now(), now())
ON CONFLICT (id) DO NOTHING;

-- ----------------------------
-- 3. 更新科室 head_doctor_id（科室 id 与医生 id 一一对应）
-- ----------------------------
UPDATE department
SET head_doctor_id = id, updated_at = now()
WHERE id BETWEEN 100 AND 116 AND head_doctor_id IS NULL;

-- ----------------------------
-- 4. 重置主键序列到当前最大值
-- ----------------------------
SELECT setval(pg_get_serial_sequence('department', 'id'), GREATEST((SELECT MAX(id) FROM department), 116));
SELECT setval(pg_get_serial_sequence('doctor', 'id'), GREATEST((SELECT MAX(id) FROM doctor), 116));

COMMIT;

-- ============================================================
-- 验证查询
-- ============================================================
SELECT d.id, d.name, d.location, doc.name AS doctor_name, doc.title
FROM department d
LEFT JOIN doctor doc ON doc.dept_id = d.id AND doc.deleted_at IS NULL
WHERE d.id BETWEEN 100 AND 116 AND d.deleted_at IS NULL
ORDER BY d.id;

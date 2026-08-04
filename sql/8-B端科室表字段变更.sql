-- ============================================================
-- 科室表 description 字段改为 location（位置字段）
-- 执行环境：PostgreSQL
-- 用途：将科室简介字段改为科室位置字段，方便患者导航
-- ============================================================

-- 1. 重命名字段：description → location
ALTER TABLE department RENAME COLUMN description TO location;

-- 2. 更新字段注释
COMMENT ON COLUMN department.location IS '科室位置（如：1号楼2层201室）';

-- 3. 插入初始位置数据
UPDATE department SET location = '1号楼2层201室'   WHERE id = 1;  -- 内科
UPDATE department SET location = '1号楼3层301室'   WHERE id = 2;  -- 外科
UPDATE department SET location = '2号楼1层101室'   WHERE id = 3;  -- 中医科
UPDATE department SET location = '2号楼2层201室'   WHERE id = 4;  -- 眼耳鼻喉科
UPDATE department SET location = '2号楼3层301室'   WHERE id = 5;  -- 妇科

-- 4. 重置序列
SELECT setval(pg_get_serial_sequence('department', 'id'), COALESCE((SELECT MAX(id) FROM department), 1));

-- 验证
SELECT id, name, location, status FROM department WHERE deleted_at IS NULL ORDER BY id;
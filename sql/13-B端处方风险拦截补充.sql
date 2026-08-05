-- ============================================================
-- prescription 表新增 risk_warnings 字段（风险规则快照）
-- 执行环境：PostgreSQL
-- 用途：记录处方提交那一刻命中的风险规则（重复用药 / 高危药品），
--       供待审核列表与处方详情展示"为何待审"，并作为追溯记录保留
-- ============================================================

-- 1. 新增 risk_warnings jsonb 字段（无风险处方不写入）
ALTER TABLE prescription ADD COLUMN risk_warnings jsonb;

-- 2. 字段注释
COMMENT ON COLUMN prescription.risk_warnings IS '提交时命中的风险规则快照（JSONB 数组，元素含 level/rule/message），供审核展示与追溯';

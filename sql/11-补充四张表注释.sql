-- ============================================================
-- 补充缺失表注释
-- 执行环境：PostgreSQL
-- 执行时机：任意时刻，可重复执行（COMMENT 重复执行不报错）
-- 说明：仅补表注释，字段不加；注释均 ≤ 8 字
-- ============================================================

-- C端导诊规则：按症状关键词推荐本院启用科室
COMMENT ON TABLE triage_rule IS '分诊规则表';

-- 处方解读结果：医生/生产链路写入，C端读取 READY 数据
COMMENT ON TABLE prescription_interpretation IS '处方解读表';

-- 医生病历解读结果：以问诊记录为报告主体
COMMENT ON TABLE consultation_report_interpretation IS '报告解读表';

-- C端用户收货地址簿
COMMENT ON TABLE c_user_delivery_address IS '收货地址表';

-- ============================================================
-- 验证：查看这四张表的注释
-- ============================================================
-- SELECT
--     c.relname AS table_name,
--     pg_catalog.obj_description(c.oid) AS table_comment
-- FROM pg_catalog.pg_class c
-- WHERE c.relkind = 'r'
--   AND c.relname IN ('triage_rule', 'prescription_interpretation',
--                     'consultation_report_interpretation', 'c_user_delivery_address')
-- ORDER BY c.relname;
-- ============================================================

-- ============================================================
-- 数据库表注释简化脚本
-- 执行环境：PostgreSQL
-- 执行时机：在已有注释脚本运行后执行，用于覆盖为简化版本
-- 目的：将所有表注释简化为"表名 + 表"格式
-- 特性：可重复执行（COMMENT 语句重复执行不会报错）
-- ============================================================

-- ============================================================
-- 1. 共享核心表（sphp-core）
-- ============================================================

COMMENT ON TABLE hospital IS '医院表';
COMMENT ON TABLE department IS '科室表';
COMMENT ON TABLE doctor IS '医生表';
COMMENT ON TABLE schedule IS '排班表';
COMMENT ON TABLE slot IS '号源时段表';
COMMENT ON TABLE slot_snapshot IS '号源快照表';
COMMENT ON TABLE c_user IS 'C端用户表';
COMMENT ON TABLE b_user IS 'B端用户表';
COMMENT ON TABLE b_refresh_token IS 'B端刷新令牌表';
COMMENT ON TABLE patient IS '患者表';
COMMENT ON TABLE patient_user_relation IS '患者用户关联表';
COMMENT ON TABLE patient_allergy IS '患者过敏史表';
COMMENT ON TABLE patient_medical_history IS '患者既往史表';
COMMENT ON TABLE appointment IS '挂号订单表';
COMMENT ON TABLE consult_record IS '问诊记录表';
COMMENT ON TABLE prescription IS '处方表';
COMMENT ON TABLE prescription_item IS '处方明细表';
COMMENT ON TABLE drug IS '药品目录表';
COMMENT ON TABLE pharmacy IS '药房表';
COMMENT ON TABLE pharmacy_drug_stock IS '药房药品库存表';
COMMENT ON TABLE prescription_template IS '处方模板表';

-- ============================================================
-- 2. C端独有表
-- ============================================================

COMMENT ON TABLE c_refresh_token IS 'C端刷新令牌表';
COMMENT ON TABLE triage_assessment IS '导诊评估表';
COMMENT ON TABLE appointment_waitlist IS '挂号候补表';
COMMENT ON TABLE payment_order IS '支付订单表';
COMMENT ON TABLE consultation_message IS '问诊消息表';
COMMENT ON TABLE drug_order IS '购药订单表';
COMMENT ON TABLE drug_order_item IS '购药订单明细表';
COMMENT ON TABLE drug_order_logistics_trace IS '购药订单物流轨迹表';
COMMENT ON TABLE patient_report IS '检查报告表';
COMMENT ON TABLE patient_report_indicator IS '检查报告指标明细表';
COMMENT ON TABLE medication_plan IS '用药计划表';
COMMENT ON TABLE follow_up_plan IS '随访计划表';
COMMENT ON TABLE notification IS '通知表';
COMMENT ON TABLE agent_tool_audit IS 'Agent工具调用审计表';

-- ============================================================
-- 3. 验证方式
-- ============================================================
-- 执行以下SQL可查看所有表的注释：
-- SELECT
--     c.relname AS table_name,
--     pg_catalog.obj_description(c.oid) AS table_comment
-- FROM pg_catalog.pg_class c
-- WHERE c.relkind = 'r'
--   AND c.relnamespace = (SELECT oid FROM pg_catalog.pg_namespace WHERE nspname = 'public')
-- ORDER BY c.relname;
-- ============================================================
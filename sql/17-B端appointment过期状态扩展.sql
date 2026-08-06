-- ============================================================
-- appointment 表状态枚举扩展：增加 EXPIRED
-- 来源：B端接诊台 batchExpireOldPaid（§5.5.1 队列清理）
-- 说明：原约束 ck_appointment_status 仅允许 ('UNPAID','PAID','COMPLETED','CANCELLED')，
--       B 端队列清理会把"已支付但未就诊且排班已过"的订单置为 EXPIRED，需扩展约束。
-- 执行时机：在 1-初始创表.sql 之后，任意位置执行均可
-- 可重复执行（先 DROP 再 ADD）
-- ============================================================

-- 1. 重建检查约束，状态枚举加入 EXPIRED
ALTER TABLE appointment DROP CONSTRAINT IF EXISTS ck_appointment_status;
ALTER TABLE appointment ADD CONSTRAINT ck_appointment_status
    CHECK (status IN ('UNPAID', 'PAID', 'COMPLETED', 'CANCELLED', 'EXPIRED'));

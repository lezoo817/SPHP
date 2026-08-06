-- ============================================================
-- appointment 表状态枚举扩展
-- 历史：
--   1) B 端接诊台 batchExpireOldPaid（§5.5.1 队列清理）
--      → 状态枚举追加 EXPIRED。
--   2) B 端 AppointmentNoShowScheduler（commit 55a4567）
--      → 状态枚举再追加 NO_SHOW：PAID 订单在 slot 结束超过宽限期、
--        且无 COMPLETED/IN_PROGRESS/DRAFT 就诊记录时，自动标记为 NO_SHOW，
--        避免过期 PAID 长期阻塞排班取消发布与 C 端号源统计。
-- 当前允许值：('UNPAID','PAID','COMPLETED','CANCELLED','EXPIRED','NO_SHOW')
-- 执行时机：在 1-初始创表.sql 之后，任意位置执行均可
-- 可重复执行（先 DROP 再 ADD）
-- ============================================================

-- 1. 重建检查约束，状态枚举加入 EXPIRED + NO_SHOW
ALTER TABLE appointment DROP CONSTRAINT IF EXISTS ck_appointment_status;
ALTER TABLE appointment ADD CONSTRAINT ck_appointment_status
    CHECK (status IN ('UNPAID', 'PAID', 'COMPLETED', 'CANCELLED', 'EXPIRED', 'NO_SHOW'));

-- C端购药订单配送时效快照：创建订单时固化地址与院内药房计算出的分钟数，避免后续地址或配置变更影响已支付订单的预计送达时间。
ALTER TABLE drug_order
    ADD COLUMN IF NOT EXISTS estimated_delivery_minutes integer;

-- 新订单必须保存正数分钟数；历史订单为空时在查询层兼容原 1 分钟物流演示时效。
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_drug_order_estimated_delivery_minutes'
    ) THEN
        ALTER TABLE drug_order
            ADD CONSTRAINT ck_drug_order_estimated_delivery_minutes
                CHECK (estimated_delivery_minutes IS NULL OR estimated_delivery_minutes > 0);
    END IF;
END $$;

COMMENT ON COLUMN drug_order.estimated_delivery_minutes IS '下单时基于收货地址和院内药房模拟计算的预计配送分钟数';

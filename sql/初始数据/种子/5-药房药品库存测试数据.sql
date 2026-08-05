-- ============================================================
-- 药房 & 药品库存测试数据
-- 说明：为郑州轻工业大学校医院(hospital_id=1)新建药房和药品库存
-- 覆盖表：drug / pharmacy / pharmacy_drug_stock
-- 可重复执行（幂等）
-- ============================================================

DO $$
DECLARE
    v_hospital_id CONSTANT bigint := 1;
    v_pharmacy_id  bigint;
    v_next_id      bigint;
BEGIN

    -- ============================================================
    -- 1. 插入药品（drug 表）
    --    已有 id=1~6 的 6 种基础药，新增 8 种，id 自增从 7 开始
    -- ============================================================

    IF NOT EXISTS (SELECT 1 FROM drug WHERE name = '乌鸡白凤丸' AND hospital_id = v_hospital_id AND deleted_at IS NULL) THEN
        INSERT INTO drug (hospital_id, name, specification, manufacturer, approval_number, unit, indication, contraindication, side_effect, status)
        VALUES (v_hospital_id, '乌鸡白凤丸', '6g*10袋', '北京同仁堂股份有限公司', '国药准字Z11020001',
                '盒', '补气养血，调经止带。用于气血两虚，月经不调。', '孕妇禁用，感冒发热者不宜服用。', '尚不明确。', 'ENABLED');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM drug WHERE name = '盐酸坦索罗辛缓释胶囊' AND hospital_id = v_hospital_id AND deleted_at IS NULL) THEN
        INSERT INTO drug (hospital_id, name, specification, manufacturer, approval_number, unit, indication, contraindication, side_effect, status)
        VALUES (v_hospital_id, '盐酸坦索罗辛缓释胶囊', '0.2mg*10粒', '安斯泰来制药（中国）有限公司', '国药准字H20000681',
                '盒', '用于前列腺增生症引起的排尿障碍。', '对本品成分过敏者禁用。', '可能出现头晕、体位性低血压等反应。', 'ENABLED');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM drug WHERE name = '金匮肾气丸' AND hospital_id = v_hospital_id AND deleted_at IS NULL) THEN
        INSERT INTO drug (hospital_id, name, specification, manufacturer, approval_number, unit, indication, contraindication, side_effect, status)
        VALUES (v_hospital_id, '金匮肾气丸', '360丸/瓶', '北京同仁堂科技发展股份有限公司', '国药准字Z11020002',
                '瓶', '温补肾阳，化气行水。用于肾虚水肿，腰膝酸软。', '孕妇忌服，阴虚内热者慎用。', '尚不明确。', 'ENABLED');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM drug WHERE name = '复方丹参滴丸' AND hospital_id = v_hospital_id AND deleted_at IS NULL) THEN
        INSERT INTO drug (hospital_id, name, specification, manufacturer, approval_number, unit, indication, contraindication, side_effect, status)
        VALUES (v_hospital_id, '复方丹参滴丸', '27mg*180丸', '天士力医药集团股份有限公司', '国药准字Z10950111',
                '盒', '活血化瘀，理气止痛。用于胸中憋闷，心绞痛。', '孕妇慎用，出血性疾病患者禁用。', '偶见胃肠道不适。', 'ENABLED');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM drug WHERE name = '板蓝根颗粒' AND hospital_id = v_hospital_id AND deleted_at IS NULL) THEN
        INSERT INTO drug (hospital_id, name, specification, manufacturer, approval_number, unit, indication, contraindication, side_effect, status)
        VALUES (v_hospital_id, '板蓝根颗粒', '10g*20袋', '广州白云山和记黄埔中药有限公司', '国药准字Z44023485',
                '盒', '清热解毒，凉血利咽。用于肺胃热盛所致的咽喉肿痛。', '糖尿病患者慎用。', '尚不明确。', 'ENABLED');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM drug WHERE name = '左氧氟沙星滴眼液' AND hospital_id = v_hospital_id AND deleted_at IS NULL) THEN
        INSERT INTO drug (hospital_id, name, specification, manufacturer, approval_number, unit, indication, contraindication, side_effect, status)
        VALUES (v_hospital_id, '左氧氟沙星滴眼液', '5ml:15mg', '参天制药（中国）有限公司', '国药准字H20200001',
                '支', '用于敏感菌引起的眼部感染，如结膜炎、角膜炎。', '对本品或喹诺酮类过敏者禁用。', '偶见眼部刺激感、发红。', 'ENABLED');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM drug WHERE name = '盐酸羟甲唑啉喷雾剂' AND hospital_id = v_hospital_id AND deleted_at IS NULL) THEN
        INSERT INTO drug (hospital_id, name, specification, manufacturer, approval_number, unit, indication, contraindication, side_effect, status)
        VALUES (v_hospital_id, '盐酸羟甲唑啉喷雾剂', '10ml:5mg', '深圳大佛药业股份有限公司', '国药准字H20000001',
                '瓶', '用于急慢性鼻炎、鼻窦炎引起的鼻塞症状。', '萎缩性鼻炎、对本品过敏者禁用。', '偶见鼻黏膜干燥、灼烧感。', 'ENABLED');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM drug WHERE name = '藿香正气水' AND hospital_id = v_hospital_id AND deleted_at IS NULL) THEN
        INSERT INTO drug (hospital_id, name, specification, manufacturer, approval_number, unit, indication, contraindication, side_effect, status)
        VALUES (v_hospital_id, '藿香正气水', '10ml*10支', '太极集团重庆涪陵制药厂有限公司', '国药准字Z50020409',
                '盒', '解表化湿，理气和中。用于外感风寒、内伤湿滞所致的感冒。', '对本品及酒精过敏者禁用。', '偶见皮疹、恶心等反应。', 'ENABLED');
    END IF;

    -- 修正 drug 表自增序列
    SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM drug;
    PERFORM setval(pg_get_serial_sequence('drug', 'id'), v_next_id, false);
    RAISE NOTICE 'drug 表数据插入完成，序列已修正为 %。', v_next_id;

    -- ============================================================
    -- 2. 插入药房（pharmacy 表）
    -- ============================================================

    IF NOT EXISTS (SELECT 1 FROM pharmacy WHERE hospital_id = v_hospital_id AND name = '一号药房' AND deleted_at IS NULL) THEN
        INSERT INTO pharmacy (hospital_id, name, address, phone, is_default, status)
        VALUES (v_hospital_id, '一号药房', '郑州轻工业大学校医院门诊楼一层', '0371-86601111', true, 'ENABLED');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pharmacy WHERE hospital_id = v_hospital_id AND name = '二号药房' AND deleted_at IS NULL) THEN
        INSERT INTO pharmacy (hospital_id, name, address, phone, is_default, status)
        VALUES (v_hospital_id, '二号药房', '郑州轻工业大学校医院住院楼一层', '0371-86602222', false, 'ENABLED');
    END IF;

    SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM pharmacy;
    PERFORM setval(pg_get_serial_sequence('pharmacy', 'id'), v_next_id, false);
    RAISE NOTICE 'pharmacy 表数据插入完成，序列已修正为 %。', v_next_id;

    -- ============================================================
    -- 3. 插入药房药品库存（pharmacy_drug_stock 表）
    --    通过名称关联 drug_id，避免硬编码
    -- ============================================================

    FOR v_pharmacy_id IN
        SELECT id FROM pharmacy
        WHERE hospital_id = v_hospital_id AND name IN ('一号药房', '二号药房') AND deleted_at IS NULL
        ORDER BY id
    LOOP
        -- 阿莫西林胶囊（18元）
        INSERT INTO pharmacy_drug_stock (pharmacy_id, drug_id, available_count, safety_stock, unit_price_cent)
        SELECT v_pharmacy_id, id, 200, 50, 1800 FROM drug WHERE name = '阿莫西林胶囊' AND hospital_id = v_hospital_id AND deleted_at IS NULL
        ON CONFLICT (pharmacy_id, drug_id) DO NOTHING;

        -- 布洛芬片（15元）
        INSERT INTO pharmacy_drug_stock (pharmacy_id, drug_id, available_count, safety_stock, unit_price_cent)
        SELECT v_pharmacy_id, id, 150, 30, 1500 FROM drug WHERE name = '布洛芬片' AND hospital_id = v_hospital_id AND deleted_at IS NULL
        ON CONFLICT (pharmacy_id, drug_id) DO NOTHING;

        -- 风寒感冒颗粒（12元）
        INSERT INTO pharmacy_drug_stock (pharmacy_id, drug_id, available_count, safety_stock, unit_price_cent)
        SELECT v_pharmacy_id, id, 100, 20, 1200 FROM drug WHERE name = '风寒感冒颗粒' AND hospital_id = v_hospital_id AND deleted_at IS NULL
        ON CONFLICT (pharmacy_id, drug_id) DO NOTHING;

        -- 氯雷他定颗粒（25元）
        INSERT INTO pharmacy_drug_stock (pharmacy_id, drug_id, available_count, safety_stock, unit_price_cent)
        SELECT v_pharmacy_id, id, 120, 25, 2500 FROM drug WHERE name = '氯雷他定颗粒' AND hospital_id = v_hospital_id AND deleted_at IS NULL
        ON CONFLICT (pharmacy_id, drug_id) DO NOTHING;

        -- 蒙脱石散（15元）
        INSERT INTO pharmacy_drug_stock (pharmacy_id, drug_id, available_count, safety_stock, unit_price_cent)
        SELECT v_pharmacy_id, id, 180, 30, 1500 FROM drug WHERE name = '蒙脱石散' AND hospital_id = v_hospital_id AND deleted_at IS NULL
        ON CONFLICT (pharmacy_id, drug_id) DO NOTHING;

        -- 碘伏（8元）
        INSERT INTO pharmacy_drug_stock (pharmacy_id, drug_id, available_count, safety_stock, unit_price_cent)
        SELECT v_pharmacy_id, id, 300, 50, 800 FROM drug WHERE name = '碘伏' AND hospital_id = v_hospital_id AND deleted_at IS NULL
        ON CONFLICT (pharmacy_id, drug_id) DO NOTHING;

        -- 乌鸡白凤丸（22元）→ 初始 6 盒，偏低状态
        INSERT INTO pharmacy_drug_stock (pharmacy_id, drug_id, available_count, safety_stock, unit_price_cent)
        SELECT v_pharmacy_id, id, 6, 15, 2200 FROM drug WHERE name = '乌鸡白凤丸' AND hospital_id = v_hospital_id AND deleted_at IS NULL
        ON CONFLICT (pharmacy_id, drug_id) DO NOTHING;

        -- 盐酸坦索罗辛缓释胶囊（18元）→ 初始 2 盒，告警状态
        INSERT INTO pharmacy_drug_stock (pharmacy_id, drug_id, available_count, safety_stock, unit_price_cent)
        SELECT v_pharmacy_id, id, 2, 20, 1800 FROM drug WHERE name = '盐酸坦索罗辛缓释胶囊' AND hospital_id = v_hospital_id AND deleted_at IS NULL
        ON CONFLICT (pharmacy_id, drug_id) DO NOTHING;

        -- 金匮肾气丸（20元）
        INSERT INTO pharmacy_drug_stock (pharmacy_id, drug_id, available_count, safety_stock, unit_price_cent)
        SELECT v_pharmacy_id, id, 90, 15, 2000 FROM drug WHERE name = '金匮肾气丸' AND hospital_id = v_hospital_id AND deleted_at IS NULL
        ON CONFLICT (pharmacy_id, drug_id) DO NOTHING;

        -- 复方丹参滴丸（26元）
        INSERT INTO pharmacy_drug_stock (pharmacy_id, drug_id, available_count, safety_stock, unit_price_cent)
        SELECT v_pharmacy_id, id, 120, 20, 2600 FROM drug WHERE name = '复方丹参滴丸' AND hospital_id = v_hospital_id AND deleted_at IS NULL
        ON CONFLICT (pharmacy_id, drug_id) DO NOTHING;

        -- 板蓝根颗粒（12元）
        INSERT INTO pharmacy_drug_stock (pharmacy_id, drug_id, available_count, safety_stock, unit_price_cent)
        SELECT v_pharmacy_id, id, 200, 30, 1200 FROM drug WHERE name = '板蓝根颗粒' AND hospital_id = v_hospital_id AND deleted_at IS NULL
        ON CONFLICT (pharmacy_id, drug_id) DO NOTHING;

        -- 左氧氟沙星滴眼液（16元）
        INSERT INTO pharmacy_drug_stock (pharmacy_id, drug_id, available_count, safety_stock, unit_price_cent)
        SELECT v_pharmacy_id, id, 150, 25, 1600 FROM drug WHERE name = '左氧氟沙星滴眼液' AND hospital_id = v_hospital_id AND deleted_at IS NULL
        ON CONFLICT (pharmacy_id, drug_id) DO NOTHING;

        -- 盐酸羟甲唑啉喷雾剂（12元）
        INSERT INTO pharmacy_drug_stock (pharmacy_id, drug_id, available_count, safety_stock, unit_price_cent)
        SELECT v_pharmacy_id, id, 100, 15, 1200 FROM drug WHERE name = '盐酸羟甲唑啉喷雾剂' AND hospital_id = v_hospital_id AND deleted_at IS NULL
        ON CONFLICT (pharmacy_id, drug_id) DO NOTHING;

        -- 藿香正气水（8元）
        INSERT INTO pharmacy_drug_stock (pharmacy_id, drug_id, available_count, safety_stock, unit_price_cent)
        SELECT v_pharmacy_id, id, 160, 25, 800 FROM drug WHERE name = '藿香正气水' AND hospital_id = v_hospital_id AND deleted_at IS NULL
        ON CONFLICT (pharmacy_id, drug_id) DO NOTHING;
    END LOOP;

    SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM pharmacy_drug_stock;
    PERFORM setval(pg_get_serial_sequence('pharmacy_drug_stock', 'id'), v_next_id, false);
    RAISE NOTICE 'pharmacy_drug_stock 表数据插入完成，序列已修正为 %。', v_next_id;

END $$;

-- ============================================================
-- 4. 数据校验
-- ============================================================
SELECT '=== 药品目录 ===' AS "统计";
SELECT id, name, specification, unit FROM drug WHERE deleted_at IS NULL ORDER BY id;

SELECT '=== 药房 ===' AS "统计";
SELECT id, name, address, is_default, status FROM pharmacy WHERE deleted_at IS NULL ORDER BY id;

SELECT '=== 药房药品库存 ===' AS "统计";
SELECT
    p.name AS 药房,
    d.name AS 药品,
    d.specification,
    pds.available_count AS 库存,
    pds.safety_stock AS 安全库存,
    pds.unit_price_cent / 100.0 || '元' AS 单价
FROM pharmacy p
JOIN pharmacy_drug_stock pds ON pds.pharmacy_id = p.id
JOIN drug d ON d.id = pds.drug_id
WHERE p.deleted_at IS NULL AND d.deleted_at IS NULL
ORDER BY p.id, d.id;

SELECT '=== 序列校验 ===' AS "统计";
SELECT 'drug_id_seq' AS 序列名, last_value, is_called FROM drug_id_seq
UNION ALL
SELECT 'pharmacy_id_seq', last_value, is_called FROM pharmacy_id_seq
UNION ALL
SELECT 'pharmacy_drug_stock_id_seq', last_value, is_called FROM pharmacy_drug_stock_id_seq;
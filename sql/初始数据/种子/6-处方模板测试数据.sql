-- ============================================================
-- 6-处方模板测试数据
-- 执行环境：PostgreSQL
-- 说明：为郑州轻工业大学校医院(hospital_id=1)新增5个常见病处方模板
--       同时补充阿司匹林肠溶片到药房库存
-- 覆盖表：drug / pharmacy_drug_stock / prescription_template
-- 可重复执行（幂等）
-- ============================================================

BEGIN;

DO $$
DECLARE
    v_hospital_id CONSTANT bigint := 1;
    v_dept_internal CONSTANT bigint := 1;  -- 内科
    v_dept_surgery CONSTANT bigint := 2;   -- 外科
    v_doctor_zhang CONSTANT bigint := 1;   -- 张医生（内科主任医师）
    v_doctor_li CONSTANT bigint := 2;      -- 李医生（外科副主任医师）
    v_drug_id bigint;
    v_pharmacy_id bigint;
    v_next_id bigint;
BEGIN

    -- ============================================================
    -- 1. 确保药品存在（drug 表）
    --    模板用到的药品若不存在则补充创建
    -- ============================================================

    -- 风寒感冒颗粒（模板①用）
    IF NOT EXISTS (SELECT 1 FROM drug WHERE name = '风寒感冒颗粒' AND hospital_id = v_hospital_id AND deleted_at IS NULL) THEN
        INSERT INTO drug (hospital_id, name, specification, manufacturer, approval_number, unit, indication, contraindication, side_effect, status)
        VALUES (v_hospital_id, '风寒感冒颗粒', '10g*20袋', '华润三九医药股份有限公司', '国药准字Z44023711',
                '盒', '解表发汗，疏风散寒。用于风寒感冒，发热头痛，恶寒，无汗，咳嗽。', '风热感冒者不适用。', '尚不明确。', 'ENABLED');
    END IF;

    -- 氯雷他定颗粒（模板②用）
    IF NOT EXISTS (SELECT 1 FROM drug WHERE name = '氯雷他定颗粒' AND hospital_id = v_hospital_id AND deleted_at IS NULL) THEN
        INSERT INTO drug (hospital_id, name, specification, manufacturer, approval_number, unit, indication, contraindication, side_effect, status)
        VALUES (v_hospital_id, '氯雷他定颗粒', '10mg*12袋', '西安杨森制药有限公司', '国药准字H20041022',
                '盒', '用于缓解过敏性鼻炎、荨麻疹等过敏症状。', '对本品成分过敏者禁用。', '少数患者可能出现困倦、口干。', 'ENABLED');
    END IF;

    -- 蒙脱石散（模板④用）
    IF NOT EXISTS (SELECT 1 FROM drug WHERE name = '蒙脱石散' AND hospital_id = v_hospital_id AND deleted_at IS NULL) THEN
        INSERT INTO drug (hospital_id, name, specification, manufacturer, approval_number, unit, indication, contraindication, side_effect, status)
        VALUES (v_hospital_id, '蒙脱石散', '3g*10袋', '博福-益普生（天津）制药有限公司', '国药准字H20000690',
                '盒', '用于成人及儿童急、慢性腹泻。', '对本品过敏者禁用。', '偶见便秘。', 'ENABLED');
    END IF;

    -- 修正 drug 表自增序列
    SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM drug;
    PERFORM setval(pg_get_serial_sequence('drug', 'id'), v_next_id, false);

    -- ============================================================
    -- 2. 补充阿司匹林肠溶片到药房库存
    --    阿司匹林肠溶片（id=4）已在药品表，但药房无库存
    --    为两家药房各补充 200 盒，安全库存 30，单价 6 元（600分）
    -- ============================================================

    FOR v_pharmacy_id IN
        SELECT id FROM pharmacy
        WHERE hospital_id = v_hospital_id AND name IN ('一号药房', '二号药房') AND deleted_at IS NULL
        ORDER BY id
    LOOP
        INSERT INTO pharmacy_drug_stock (pharmacy_id, drug_id, available_count, safety_stock, unit_price_cent)
        SELECT v_pharmacy_id, id, 200, 30, 600 FROM drug
        WHERE name = '阿司匹林肠溶片' AND hospital_id = v_hospital_id AND deleted_at IS NULL
        ON CONFLICT (pharmacy_id, drug_id) DO NOTHING;
    END LOOP;

    SELECT COALESCE(MAX(id), 0) + 1 INTO v_next_id FROM pharmacy_drug_stock;
    PERFORM setval(pg_get_serial_sequence('pharmacy_drug_stock', 'id'), v_next_id, false);

    -- ============================================================
    -- 3. 插入 5 个处方模板
    -- ============================================================

    -- ① 感冒发热（内科）
    IF NOT EXISTS (SELECT 1 FROM prescription_template WHERE hospital_id = v_hospital_id AND name = '感冒发热' AND deleted_at IS NULL) THEN
        INSERT INTO prescription_template (hospital_id, dept_id, name, doctor_id, items)
        VALUES (v_hospital_id, v_dept_internal, '感冒发热', v_doctor_zhang,
            jsonb_build_array(
                jsonb_build_object(
                    'drugId', (SELECT id FROM drug WHERE name = '风寒感冒颗粒' AND hospital_id = v_hospital_id AND deleted_at IS NULL LIMIT 1),
                    'drugName', '风寒感冒颗粒',
                    'dosage', '1袋',
                    'frequency', '每日3次',
                    'usageMethod', '口服',
                    'days', 3,
                    'quantity', 1,
                    'quantityUnit', '盒'
                ),
                jsonb_build_object(
                    'drugId', (SELECT id FROM drug WHERE name = '布洛芬片' AND hospital_id = v_hospital_id AND deleted_at IS NULL LIMIT 1),
                    'drugName', '布洛芬片',
                    'dosage', '1片',
                    'frequency', '每日2次',
                    'usageMethod', '口服',
                    'days', 3,
                    'quantity', 1,
                    'quantityUnit', '瓶'
                )
            )
        );
    END IF;

    -- ② 过敏性鼻炎（内科）
    IF NOT EXISTS (SELECT 1 FROM prescription_template WHERE hospital_id = v_hospital_id AND name = '过敏性鼻炎' AND deleted_at IS NULL) THEN
        INSERT INTO prescription_template (hospital_id, dept_id, name, doctor_id, items)
        VALUES (v_hospital_id, v_dept_internal, '过敏性鼻炎', v_doctor_zhang,
            jsonb_build_array(
                jsonb_build_object(
                    'drugId', (SELECT id FROM drug WHERE name = '氯雷他定颗粒' AND hospital_id = v_hospital_id AND deleted_at IS NULL LIMIT 1),
                    'drugName', '氯雷他定颗粒',
                    'dosage', '1袋',
                    'frequency', '每日1次',
                    'usageMethod', '口服',
                    'days', 7,
                    'quantity', 1,
                    'quantityUnit', '盒'
                ),
                jsonb_build_object(
                    'drugId', (SELECT id FROM drug WHERE name = '盐酸羟甲唑啉喷雾剂' AND hospital_id = v_hospital_id AND deleted_at IS NULL LIMIT 1),
                    'drugName', '盐酸羟甲唑啉喷雾剂',
                    'dosage', '1喷',
                    'frequency', '每日2次',
                    'usageMethod', '外用',
                    'days', 7,
                    'quantity', 1,
                    'quantityUnit', '瓶'
                )
            )
        );
    END IF;

    -- ③ 冠心病（内科）
    IF NOT EXISTS (SELECT 1 FROM prescription_template WHERE hospital_id = v_hospital_id AND name = '冠心病' AND deleted_at IS NULL) THEN
        INSERT INTO prescription_template (hospital_id, dept_id, name, doctor_id, items)
        VALUES (v_hospital_id, v_dept_internal, '冠心病', v_doctor_zhang,
            jsonb_build_array(
                jsonb_build_object(
                    'drugId', (SELECT id FROM drug WHERE name = '复方丹参滴丸' AND hospital_id = v_hospital_id AND deleted_at IS NULL LIMIT 1),
                    'drugName', '复方丹参滴丸',
                    'dosage', '10丸',
                    'frequency', '每日3次',
                    'usageMethod', '口服',
                    'days', 14,
                    'quantity', 1,
                    'quantityUnit', '盒'
                ),
                jsonb_build_object(
                    'drugId', (SELECT id FROM drug WHERE name = '阿司匹林肠溶片' AND hospital_id = v_hospital_id AND deleted_at IS NULL LIMIT 1),
                    'drugName', '阿司匹林肠溶片',
                    'dosage', '1片',
                    'frequency', '每日1次',
                    'usageMethod', '口服',
                    'days', 30,
                    'quantity', 1,
                    'quantityUnit', '盒'
                )
            )
        );
    END IF;

    -- ④ 急性腹泻（内科）
    IF NOT EXISTS (SELECT 1 FROM prescription_template WHERE hospital_id = v_hospital_id AND name = '急性腹泻' AND deleted_at IS NULL) THEN
        INSERT INTO prescription_template (hospital_id, dept_id, name, doctor_id, items)
        VALUES (v_hospital_id, v_dept_internal, '急性腹泻', v_doctor_zhang,
            jsonb_build_array(
                jsonb_build_object(
                    'drugId', (SELECT id FROM drug WHERE name = '蒙脱石散' AND hospital_id = v_hospital_id AND deleted_at IS NULL LIMIT 1),
                    'drugName', '蒙脱石散',
                    'dosage', '1袋',
                    'frequency', '每日3次',
                    'usageMethod', '口服',
                    'days', 3,
                    'quantity', 1,
                    'quantityUnit', '盒'
                )
            )
        );
    END IF;

    -- ⑤ 前列腺增生（外科）
    IF NOT EXISTS (SELECT 1 FROM prescription_template WHERE hospital_id = v_hospital_id AND name = '前列腺增生' AND deleted_at IS NULL) THEN
        INSERT INTO prescription_template (hospital_id, dept_id, name, doctor_id, items)
        VALUES (v_hospital_id, v_dept_surgery, '前列腺增生', v_doctor_li,
            jsonb_build_array(
                jsonb_build_object(
                    'drugId', (SELECT id FROM drug WHERE name = '盐酸坦索罗辛缓释胶囊' AND hospital_id = v_hospital_id AND deleted_at IS NULL LIMIT 1),
                    'drugName', '盐酸坦索罗辛缓释胶囊',
                    'dosage', '1粒',
                    'frequency', '每日1次',
                    'usageMethod', '口服',
                    'days', 30,
                    'quantity', 1,
                    'quantityUnit', '盒'
                )
            )
        );
    END IF;

    RAISE NOTICE '处方模板数据插入完成。';
END $$;

-- ============================================================
-- 4. 数据校验
-- ============================================================
SELECT '=== 新增药品 ===' AS "统计";
SELECT id, name, specification, unit FROM drug
WHERE hospital_id = 1 AND deleted_at IS NULL
  AND name IN ('风寒感冒颗粒', '氯雷他定颗粒', '蒙脱石散')
ORDER BY id;

SELECT '=== 阿司匹林肠溶片库存 ===' AS "统计";
SELECT p.name AS 药房, d.name AS 药品, pds.available_count AS 库存, pds.safety_stock AS 安全库存
FROM pharmacy p
JOIN pharmacy_drug_stock pds ON pds.pharmacy_id = p.id
JOIN drug d ON d.id = pds.drug_id
WHERE p.hospital_id = 1 AND p.deleted_at IS NULL AND d.name = '阿司匹林肠溶片' AND d.deleted_at IS NULL;

SELECT '=== 处方模板 ===' AS "统计";
SELECT
    pt.name AS 模板名称,
    d.name AS 科室,
    pt.items AS 药品明细
FROM prescription_template pt
LEFT JOIN department d ON d.id = pt.dept_id
WHERE pt.hospital_id = 1 AND pt.deleted_at IS NULL
ORDER BY pt.id;

COMMIT;
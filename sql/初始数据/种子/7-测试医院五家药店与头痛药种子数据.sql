-- ============================================================
-- 测试医院五家药店与头痛药演示数据
-- 执行环境：PostgreSQL
-- 执行前提：已执行测试医院、药品和药房基础种子数据
-- 说明：补齐五家药房，新增头痛药及其他演示药品，并为五家药房分配库存
--       头痛药仅在一号、二号、三号药房保持可售库存
-- 特性：可重复执行，不重复创建药房、药品或库存记录
-- ============================================================

BEGIN;

DO $$
DECLARE
    v_hospital_id CONSTANT bigint := 1;
    v_pharmacy_id bigint;
    v_drug_id bigint;
    v_pharmacy_name varchar(128);
    v_pharmacy_address varchar(500);
    v_drug_name varchar(200);
    v_drug_specification varchar(100);
    v_drug_manufacturer varchar(200);
    v_drug_approval_number varchar(50);
    v_drug_unit varchar(32);
    v_drug_indication text;
    v_drug_contraindication text;
    v_drug_side_effect text;
    v_available_count integer;
    v_safety_stock integer;
    v_unit_price_cent integer;
    v_is_headache_drug boolean;
    v_is_headache_pharmacy boolean;
    v_default_pharmacy_exists boolean;
    v_headache_drugs CONSTANT text[] := ARRAY[
        '布洛芬片',
        '对乙酰氨基酚片',
        '复方羊角颗粒',
        '天麻素片'
    ];
BEGIN
    -- 归属校验：避免把测试数据误写入其他医院。
    IF NOT EXISTS (
        SELECT 1
        FROM hospital
        WHERE id = v_hospital_id
          AND name = '测试医院'
          AND deleted_at IS NULL
    ) THEN
        RAISE EXCEPTION 'hospital_id=1 不存在有效的测试医院，已终止种子数据写入';
    END IF;

    -- 确保五家药房存在；保留已有一号、二号药房的地址和库存。
    FOR v_pharmacy_name, v_pharmacy_address IN
        SELECT pharmacy_name, pharmacy_address
        FROM (
            VALUES
                ('一号药房'::varchar(128), '测试医院门诊楼一层'::varchar(500)),
                ('二号药房'::varchar(128), '测试医院住院楼一层'::varchar(500)),
                ('三号药房'::varchar(128), '测试医院门诊楼二层'::varchar(500)),
                ('四号药房'::varchar(128), '测试医院急诊楼一层'::varchar(500)),
                ('五号药房'::varchar(128), '测试医院综合楼一层'::varchar(500))
        ) AS pharmacy_seed(pharmacy_name, pharmacy_address)
    LOOP
        SELECT id
        INTO v_pharmacy_id
        FROM pharmacy
        WHERE hospital_id = v_hospital_id
          AND name = v_pharmacy_name
          AND deleted_at IS NULL
        ORDER BY id
        LIMIT 1;

        IF v_pharmacy_id IS NULL THEN
            INSERT INTO pharmacy (hospital_id, name, address, phone, is_default, status)
            VALUES (
                v_hospital_id,
                v_pharmacy_name,
                v_pharmacy_address,
                '010-12345678',
                v_pharmacy_name = '一号药房',
                'ENABLED'
            );
        END IF;
    END LOOP;

    -- 仅在医院没有启用默认药房时设置一号药房为默认，避免违反唯一索引。
    SELECT EXISTS (
        SELECT 1
        FROM pharmacy
        WHERE hospital_id = v_hospital_id
          AND status = 'ENABLED'
          AND is_default
          AND deleted_at IS NULL
    ) INTO v_default_pharmacy_exists;

    IF NOT v_default_pharmacy_exists THEN
        UPDATE pharmacy
        SET is_default = true,
            updated_at = now()
        WHERE hospital_id = v_hospital_id
          AND name = '一号药房'
          AND status = 'ENABLED'
          AND deleted_at IS NULL;
    END IF;

    -- 新增三种头痛药和六种其他药品；批准文号作为稳定幂等标识。
    FOR v_drug_name, v_drug_specification, v_drug_manufacturer, v_drug_approval_number,
        v_drug_unit, v_drug_indication, v_drug_contraindication, v_drug_side_effect IN
        SELECT drug_name, drug_specification, drug_manufacturer, drug_approval_number,
               drug_unit, drug_indication, drug_contraindication, drug_side_effect
        FROM (
            VALUES
                ('对乙酰氨基酚片'::varchar(200), '0.5g*12片'::varchar(100), '上海信谊药厂有限公司'::varchar(200), '国药准字H20260801'::varchar(50), '盒'::varchar(32), '用于缓解轻至中度疼痛和发热。'::text, '严重肝功能不全者禁用。'::text, '过量可能损伤肝脏。'::text),
                ('复方羊角颗粒'::varchar(200), '8g*10袋'::varchar(100), '哈药集团中药二厂'::varchar(200), '国药准字Z20260802'::varchar(50), '盒'::varchar(32), '用于偏头痛、紧张性头痛等症状。'::text, '孕妇及对本品成分过敏者慎用。'::text, '偶见胃部不适。'::text),
                ('天麻素片'::varchar(200), '50mg*24片'::varchar(100), '昆明制药集团股份有限公司'::varchar(200), '国药准字H20260803'::varchar(50), '盒'::varchar(32), '用于头痛、头晕及神经衰弱的辅助治疗。'::text, '对本品成分过敏者禁用。'::text, '少数患者可能出现口干。'::text),
                ('维生素C片'::varchar(200), '100mg*100片'::varchar(100), '东北制药集团股份有限公司'::varchar(200), '国药准字H20260804'::varchar(50), '瓶'::varchar(32), '用于维生素C缺乏的预防和治疗。'::text, '草酸盐结石患者慎用。'::text, '长期大量服用可能引起胃肠不适。'::text),
                ('开塞露'::varchar(200), '20ml*2支'::varchar(100), '上海运佳黄浦制药有限公司'::varchar(200), '国药准字H20260805'::varchar(50), '盒'::varchar(32), '用于便秘患者的润滑性通便。'::text, '肠道出血或急腹症患者禁用。'::text, '偶见肛门刺激感。'::text),
                ('莫匹罗星软膏'::varchar(200), '2%*5g'::varchar(100), '中美天津史克制药有限公司'::varchar(200), '国药准字H20260806'::varchar(50), '支'::varchar(32), '用于革兰阳性球菌引起的皮肤感染。'::text, '对本品或聚乙二醇过敏者禁用。'::text, '局部可能出现烧灼感。'::text),
                ('氯己定含漱液'::varchar(200), '0.12%*200ml'::varchar(100), '江苏晨牌药业集团股份有限公司'::varchar(200), '国药准字H20260807'::varchar(50), '瓶'::varchar(32), '用于口腔消毒和牙龈炎辅助治疗。'::text, '对本品过敏者禁用，不可吞咽。'::text, '长期使用可能导致味觉异常。'::text),
                ('复方甘草片'::varchar(200), '100片'::varchar(100), '太极集团重庆桐君阁药厂有限公司'::varchar(200), '国药准字Z20260808'::varchar(50), '瓶'::varchar(32), '用于镇咳祛痰。'::text, '高血压、低钾血症患者慎用。'::text, '长期大量服用可能引起水肿。'::text),
                ('葡萄糖酸锌口服溶液'::varchar(200), '10ml*10支'::varchar(100), '哈药集团三精制药有限公司'::varchar(200), '国药准字H20260809'::varchar(50), '盒'::varchar(32), '用于锌缺乏引起的食欲不振等症状。'::text, '对本品成分过敏者禁用。'::text, '可能出现恶心、呕吐。'::text)
        ) AS drug_seed(drug_name, drug_specification, drug_manufacturer, drug_approval_number, drug_unit, drug_indication, drug_contraindication, drug_side_effect)
    LOOP
        IF NOT EXISTS (
            SELECT 1
            FROM drug
            WHERE hospital_id = v_hospital_id
              AND approval_number = v_drug_approval_number
              AND deleted_at IS NULL
        ) THEN
            INSERT INTO drug (hospital_id, name, specification, manufacturer, approval_number, unit, indication, contraindication, side_effect, status)
            VALUES (v_hospital_id, v_drug_name, v_drug_specification, v_drug_manufacturer, v_drug_approval_number, v_drug_unit, v_drug_indication, v_drug_contraindication, v_drug_side_effect, 'ENABLED');
        END IF;
    END LOOP;

    -- 为五家药房分配全部药品；头痛药仅保留前三家药房的可售库存。
    FOR v_pharmacy_id, v_pharmacy_name IN
        SELECT id, name
        FROM pharmacy
        WHERE hospital_id = v_hospital_id
          AND name IN ('一号药房', '二号药房', '三号药房', '四号药房', '五号药房')
          AND status = 'ENABLED'
          AND deleted_at IS NULL
        ORDER BY id
    LOOP
        v_is_headache_pharmacy := v_pharmacy_name IN ('一号药房', '二号药房', '三号药房');

        FOR v_drug_id, v_drug_name IN
            SELECT id, name
            FROM drug
            WHERE hospital_id = v_hospital_id
              AND status = 'ENABLED'
              AND deleted_at IS NULL
            ORDER BY id
        LOOP
            v_is_headache_drug := v_drug_name = ANY(v_headache_drugs);
            v_available_count := 30 + ((v_drug_id * 17 + v_pharmacy_id * 11) % 171);
            v_safety_stock := 10 + (v_drug_id % 21);
            v_unit_price_cent := 500 + ((v_drug_id * 37) % 4500);

            IF v_is_headache_drug AND NOT v_is_headache_pharmacy THEN
                -- 清零四号、五号药房的头痛药可售量，避免其出现在可购药房列表。
                UPDATE pharmacy_drug_stock
                SET available_count = 0,
                    safety_stock = 0,
                    updated_at = now()
                WHERE pharmacy_id = v_pharmacy_id
                  AND drug_id = v_drug_id;
                CONTINUE;
            END IF;

            IF v_is_headache_drug THEN
                -- 前三家药房的头痛药必须保持正库存；已有库存不被降低。
                INSERT INTO pharmacy_drug_stock (pharmacy_id, drug_id, available_count, locked_count, safety_stock, unit_price_cent)
                VALUES (v_pharmacy_id, v_drug_id, v_available_count, 0, v_safety_stock, v_unit_price_cent)
                ON CONFLICT (pharmacy_id, drug_id) DO UPDATE
                SET available_count = GREATEST(pharmacy_drug_stock.available_count, EXCLUDED.available_count),
                    safety_stock = GREATEST(pharmacy_drug_stock.safety_stock, EXCLUDED.safety_stock),
                    updated_at = now();
            ELSE
                -- 非头痛药在五家药房均补齐一条库存记录。
                INSERT INTO pharmacy_drug_stock (pharmacy_id, drug_id, available_count, locked_count, safety_stock, unit_price_cent)
                VALUES (v_pharmacy_id, v_drug_id, v_available_count, 0, v_safety_stock, v_unit_price_cent)
                ON CONFLICT (pharmacy_id, drug_id) DO NOTHING;
            END IF;
        END LOOP;
    END LOOP;
END $$;

COMMIT;

-- 校验五家药房和头痛药可售药房数量。
SELECT COUNT(*) AS pharmacy_count
FROM pharmacy
WHERE hospital_id = 1
  AND name IN ('一号药房', '二号药房', '三号药房', '四号药房', '五号药房')
  AND status = 'ENABLED'
  AND deleted_at IS NULL;

SELECT d.name AS drug_name,
       COUNT(*) FILTER (WHERE pds.available_count > 0) AS pharmacies_with_sellable_stock
FROM drug d
JOIN pharmacy_drug_stock pds ON pds.drug_id = d.id
JOIN pharmacy p ON p.id = pds.pharmacy_id
WHERE d.hospital_id = 1
  AND d.name = ANY (ARRAY['布洛芬片', '对乙酰氨基酚片', '复方羊角颗粒', '天麻素片'])
  AND p.name IN ('一号药房', '二号药房', '三号药房', '四号药房', '五号药房')
  AND d.deleted_at IS NULL
  AND p.deleted_at IS NULL
GROUP BY d.id, d.name
ORDER BY d.id;

-- 查看五家药房的药品库存分布。
SELECT p.name AS pharmacy_name,
       COUNT(*) FILTER (WHERE pds.available_count > 0) AS sellable_drug_count
FROM pharmacy p
LEFT JOIN pharmacy_drug_stock pds ON pds.pharmacy_id = p.id
WHERE p.hospital_id = 1
  AND p.name IN ('一号药房', '二号药房', '三号药房', '四号药房', '五号药房')
  AND p.deleted_at IS NULL
GROUP BY p.id, p.name
ORDER BY p.id;

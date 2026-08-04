-- ============================================================
-- 上海医院初始演示数据
-- 执行环境：PostgreSQL
-- 执行时机：在 1-初始创表.sql 至 9-C端收货地址与配送模拟补充.sql 执行完成后运行
-- 说明：创建一所上海医院、科室、医生、可挂号排班、药房、药品及库存；可重复执行
-- ============================================================

BEGIN;

DO $$
DECLARE
    v_hospital_id bigint;
    v_department_id bigint;
    v_head_doctor_id bigint;
    v_doctor_id bigint;
    v_doctor_dept_id bigint;
    v_schedule_id bigint;
    v_slot_id bigint;
    v_pharmacy_id bigint;
    v_drug_id bigint;
    v_department_name varchar(64);
    v_department_location varchar(500);
    v_doctor_name varchar(64);
    v_doctor_title varchar(64);
    v_doctor_specialty varchar(1000);
    v_doctor_introduction varchar(4000);
    v_doctor_license_no varchar(64);
    v_schedule_date date;
    v_shift varchar(10);
    v_first_start_time time;
    v_first_end_time time;
    v_second_end_time time;
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
BEGIN
    -- 按名称和地址定位种子医院，避免重复执行时新增医院。
    SELECT id
    INTO v_hospital_id
    FROM hospital
    WHERE name = '上海健康示范医院'
      AND address = '上海市浦东新区健康路88号'
      AND deleted_at IS NULL
    ORDER BY id
    LIMIT 1;

    IF v_hospital_id IS NULL THEN
        INSERT INTO hospital (name, level, description, address, contact, status)
        VALUES (
            '上海健康示范医院',
            '三级医院',
            '用于 C 端挂号、院内药房和库存流程演示的上海医院初始数据。',
            '上海市浦东新区健康路88号',
            '021-68888888',
            'ENABLED'
        )
        RETURNING id INTO v_hospital_id;
    END IF;

    -- 创建三个启用科室及其院内位置，科室负责人在医生创建完成后统一回填。
    FOR v_department_name, v_department_location IN
        SELECT department_name, department_location
        FROM (
            VALUES
                ('内科'::varchar(64), '门诊楼2层A区'::varchar(500)),
                ('外科'::varchar(64), '门诊楼3层B区'::varchar(500)),
                ('儿科'::varchar(64), '门诊楼1层C区'::varchar(500))
        ) AS department_seed(department_name, department_location)
    LOOP
        SELECT id
        INTO v_department_id
        FROM department
        WHERE hospital_id = v_hospital_id
          AND name = v_department_name
          AND deleted_at IS NULL
        ORDER BY id
        LIMIT 1;

        IF v_department_id IS NULL THEN
            INSERT INTO department (hospital_id, name, location, status)
            VALUES (v_hospital_id, v_department_name, v_department_location, 'ENABLED')
            RETURNING id INTO v_department_id;
        END IF;
    END LOOP;

    -- 为每个科室创建两名医生，执业证书编号作为种子医生的稳定业务标识。
    FOR v_department_name, v_doctor_name, v_doctor_title, v_doctor_specialty,
        v_doctor_introduction, v_doctor_license_no IN
        SELECT department_name, doctor_name, doctor_title, doctor_specialty,
               doctor_introduction, doctor_license_no
        FROM (
            VALUES
                ('内科'::varchar(64), '陈明远'::varchar(64), '主任医师'::varchar(64),
                 '高血压、糖尿病、冠心病及呼吸系统常见病诊疗。'::varchar(1000),
                 '从事内科临床工作二十余年，擅长慢性病规范化管理。'::varchar(4000),
                 'SHHP-IM-2026001'::varchar(64)),
                ('内科'::varchar(64), '李静怡'::varchar(64), '主治医师'::varchar(64),
                 '消化系统疾病、甲状腺疾病和健康体检异常评估。'::varchar(1000),
                 '注重结合生活方式指导开展内科常见病诊疗。'::varchar(4000),
                 'SHHP-IM-2026002'::varchar(64)),
                ('外科'::varchar(64), '王志强'::varchar(64), '主任医师'::varchar(64),
                 '胆囊疾病、疝气、阑尾炎及普外科常见疾病诊疗。'::varchar(1000),
                 '从事普外科临床与教学工作多年，具备丰富门诊诊疗经验。'::varchar(4000),
                 'SHHP-SU-2026001'::varchar(64)),
                ('外科'::varchar(64), '赵雨辰'::varchar(64), '主治医师'::varchar(64),
                 '体表肿物、甲状腺疾病和术后伤口管理。'::varchar(1000),
                 '擅长以规范随访提升普外科患者的连续诊疗体验。'::varchar(4000),
                 'SHHP-SU-2026002'::varchar(64)),
                ('儿科'::varchar(64), '孙晓蕾'::varchar(64), '主任医师'::varchar(64),
                 '儿童呼吸道感染、过敏性疾病和生长发育评估。'::varchar(1000),
                 '长期从事儿童常见病诊疗与儿童保健工作。'::varchar(4000),
                 'SHHP-PE-2026001'::varchar(64)),
                ('儿科'::varchar(64), '周安然'::varchar(64), '主治医师'::varchar(64),
                 '儿童发热、消化道疾病和疫苗接种咨询。'::varchar(1000),
                 '擅长与家长沟通儿童常见病家庭护理要点。'::varchar(4000),
                 'SHHP-PE-2026002'::varchar(64))
        ) AS doctor_seed(
            department_name,
            doctor_name,
            doctor_title,
            doctor_specialty,
            doctor_introduction,
            doctor_license_no
        )
    LOOP
        SELECT id
        INTO v_department_id
        FROM department
        WHERE hospital_id = v_hospital_id
          AND name = v_department_name
          AND deleted_at IS NULL
        ORDER BY id
        LIMIT 1;

        SELECT id
        INTO v_doctor_id
        FROM doctor
        WHERE hospital_id = v_hospital_id
          AND license_no = v_doctor_license_no
          AND deleted_at IS NULL
        ORDER BY id
        LIMIT 1;

        IF v_doctor_id IS NULL THEN
            INSERT INTO doctor (
                hospital_id,
                dept_id,
                name,
                title,
                specialty,
                introduction,
                license_no,
                registration_fee_cent,
                status
            )
            VALUES (
                v_hospital_id,
                v_department_id,
                v_doctor_name,
                v_doctor_title,
                v_doctor_specialty,
                v_doctor_introduction,
                v_doctor_license_no,
                3000,
                'ENABLED'
            )
            RETURNING id INTO v_doctor_id;
        END IF;
    END LOOP;

    -- 每科第一位种子医生担任负责人，确保负责人属于同一医院和科室。
    FOR v_department_name, v_doctor_license_no IN
        SELECT department_name, doctor_license_no
        FROM (
            VALUES
                ('内科'::varchar(64), 'SHHP-IM-2026001'::varchar(64)),
                ('外科'::varchar(64), 'SHHP-SU-2026001'::varchar(64)),
                ('儿科'::varchar(64), 'SHHP-PE-2026001'::varchar(64))
        ) AS department_head_seed(department_name, doctor_license_no)
    LOOP
        SELECT id
        INTO v_department_id
        FROM department
        WHERE hospital_id = v_hospital_id
          AND name = v_department_name
          AND deleted_at IS NULL
        ORDER BY id
        LIMIT 1;

        SELECT id
        INTO v_head_doctor_id
        FROM doctor
        WHERE hospital_id = v_hospital_id
          AND dept_id = v_department_id
          AND license_no = v_doctor_license_no
          AND deleted_at IS NULL
        ORDER BY id
        LIMIT 1;

        UPDATE department
        SET head_doctor_id = v_head_doctor_id,
            updated_at = now()
        WHERE id = v_department_id
          AND head_doctor_id IS DISTINCT FROM v_head_doctor_id;
    END LOOP;

    -- 仅为尚无有效排班的种子医生初始化一次随机排班，防止重复执行累积号源。
    FOR v_doctor_id, v_doctor_dept_id IN
        SELECT id, dept_id
        FROM doctor
        WHERE hospital_id = v_hospital_id
          AND license_no IN (
              'SHHP-IM-2026001', 'SHHP-IM-2026002',
              'SHHP-SU-2026001', 'SHHP-SU-2026002',
              'SHHP-PE-2026001', 'SHHP-PE-2026002'
          )
          AND deleted_at IS NULL
        ORDER BY id
    LOOP
        IF NOT EXISTS (
            SELECT 1
            FROM schedule
            WHERE doctor_id = v_doctor_id
              AND deleted_at IS NULL
        ) THEN
            -- 从明天至未来第七天随机抽取三个不同日期，保证每位医生均有可预约排班。
            FOR v_schedule_date IN
                SELECT current_date + day_offset
                FROM generate_series(1, 7) AS day_offset
                ORDER BY random()
                LIMIT 3
            LOOP
                -- 随机选择上午或下午班次，并限定时段不跨越对应班次。
                v_shift := CASE WHEN random() < 0.5 THEN 'MORNING' ELSE 'AFTERNOON' END;
                v_first_start_time := CASE
                    WHEN v_shift = 'MORNING' THEN (ARRAY['08:00'::time, '08:30'::time, '09:00'::time, '09:30'::time])[floor(random() * 4)::integer + 1]
                    ELSE (ARRAY['13:30'::time, '14:00'::time, '14:30'::time, '15:00'::time])[floor(random() * 4)::integer + 1]
                END;
                v_first_end_time := (v_first_start_time + interval '30 minutes')::time;
                v_second_end_time := (v_first_end_time + interval '30 minutes')::time;

                INSERT INTO schedule (
                    doctor_id,
                    dept_id,
                    schedule_date,
                    shift,
                    total_slots,
                    status,
                    published_at
                )
                VALUES (
                    v_doctor_id,
                    v_doctor_dept_id,
                    v_schedule_date,
                    v_shift,
                    10,
                    'PUBLISHED',
                    now()
                )
                RETURNING id INTO v_schedule_id;

                -- 每个排班生成两个连续时段，每个时段包含五个可约号源。
                INSERT INTO slot (schedule_id, start_time, end_time, total_count, remain_count)
                VALUES (v_schedule_id, v_first_start_time, v_first_end_time, 5, 5)
                RETURNING id INTO v_slot_id;

                INSERT INTO slot_snapshot (slot_id, status)
                SELECT v_slot_id, 'AVAILABLE'
                FROM generate_series(1, 5);

                INSERT INTO slot (schedule_id, start_time, end_time, total_count, remain_count)
                VALUES (v_schedule_id, v_first_end_time, v_second_end_time, 5, 5)
                RETURNING id INTO v_slot_id;

                INSERT INTO slot_snapshot (slot_id, status)
                SELECT v_slot_id, 'AVAILABLE'
                FROM generate_series(1, 5);
            END LOOP;
        END IF;
    END LOOP;

    -- 创建两家药房，默认药房仅设置为门诊药房以满足医院默认药房唯一约束。
    FOR v_pharmacy_name, v_pharmacy_address IN
        SELECT pharmacy_name, pharmacy_address
        FROM (
            VALUES
                ('上海健康示范医院门诊药房'::varchar(128), '上海市浦东新区健康路88号门诊一楼'::varchar(500)),
                ('上海健康示范医院住院药房'::varchar(128), '上海市浦东新区健康路88号住院部一楼'::varchar(500))
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
                '021-68888888',
                v_pharmacy_name = '上海健康示范医院门诊药房',
                'ENABLED'
            )
            RETURNING id INTO v_pharmacy_id;
        END IF;
    END LOOP;

    -- 创建三种启用药品，批准文号在同一医院内保持唯一。
    FOR v_drug_name, v_drug_specification, v_drug_manufacturer, v_drug_approval_number,
        v_drug_unit, v_drug_indication, v_drug_contraindication, v_drug_side_effect IN
        SELECT drug_name, drug_specification, drug_manufacturer, drug_approval_number,
               drug_unit, drug_indication, drug_contraindication, drug_side_effect
        FROM (
            VALUES
                ('布洛芬缓释胶囊'::varchar(200), '0.3g*20粒'::varchar(100), '上海现代制药股份有限公司'::varchar(200),
                 '国药准字H20260001'::varchar(50), '盒'::varchar(32), '用于缓解轻至中度疼痛及发热。'::text,
                 '对本品过敏、活动性消化道溃疡患者禁用。'::text, '可能出现胃肠道不适、头晕等反应。'::text),
                ('阿莫西林胶囊'::varchar(200), '0.25g*24粒'::varchar(100), '华北制药股份有限公司'::varchar(200),
                 '国药准字H20260002'::varchar(50), '盒'::varchar(32), '用于敏感菌引起的常见感染。'::text,
                 '青霉素过敏患者禁用。'::text, '可能出现皮疹、恶心、腹泻等反应。'::text),
                ('氯雷他定片'::varchar(200), '10mg*6片'::varchar(100), '拜耳医药保健有限公司'::varchar(200),
                 '国药准字H20260003'::varchar(50), '盒'::varchar(32), '用于缓解过敏性鼻炎和荨麻疹相关症状。'::text,
                 '对本品成分过敏者禁用。'::text, '少数患者可能出现困倦、口干等反应。'::text)
        ) AS drug_seed(
            drug_name,
            drug_specification,
            drug_manufacturer,
            drug_approval_number,
            drug_unit,
            drug_indication,
            drug_contraindication,
            drug_side_effect
        )
    LOOP
        SELECT id
        INTO v_drug_id
        FROM drug
        WHERE hospital_id = v_hospital_id
          AND approval_number = v_drug_approval_number
          AND deleted_at IS NULL
        ORDER BY id
        LIMIT 1;

        IF v_drug_id IS NULL THEN
            INSERT INTO drug (
                hospital_id,
                name,
                specification,
                manufacturer,
                approval_number,
                unit,
                indication,
                contraindication,
                side_effect,
                status
            )
            VALUES (
                v_hospital_id,
                v_drug_name,
                v_drug_specification,
                v_drug_manufacturer,
                v_drug_approval_number,
                v_drug_unit,
                v_drug_indication,
                v_drug_contraindication,
                v_drug_side_effect,
                'ENABLED'
            );
        END IF;
    END LOOP;

    -- 为两家药房写入全部三种药品的库存，已有库存保持原值避免覆盖实际库存。
    FOR v_pharmacy_id IN
        SELECT id
        FROM pharmacy
        WHERE hospital_id = v_hospital_id
          AND name IN ('上海健康示范医院门诊药房', '上海健康示范医院住院药房')
          AND deleted_at IS NULL
        ORDER BY id
    LOOP
        FOR v_drug_id, v_available_count, v_safety_stock, v_unit_price_cent IN
            SELECT
                d.id,
                stock_seed.available_count,
                stock_seed.safety_stock,
                stock_seed.unit_price_cent
            FROM drug d
            INNER JOIN (
                VALUES
                    ('国药准字H20260001'::varchar(50), 120, 20, 2800),
                    ('国药准字H20260002'::varchar(50), 100, 20, 1800),
                    ('国药准字H20260003'::varchar(50), 80, 15, 3500)
            ) AS stock_seed(approval_number, available_count, safety_stock, unit_price_cent)
                ON d.approval_number = stock_seed.approval_number
            WHERE d.hospital_id = v_hospital_id
              AND d.deleted_at IS NULL
            ORDER BY d.id
        LOOP
            INSERT INTO pharmacy_drug_stock (
                pharmacy_id,
                drug_id,
                available_count,
                locked_count,
                safety_stock,
                unit_price_cent
            )
            VALUES (
                v_pharmacy_id,
                v_drug_id,
                v_available_count,
                0,
                v_safety_stock,
                v_unit_price_cent
            )
            ON CONFLICT (pharmacy_id, drug_id) DO NOTHING;
        END LOOP;
    END LOOP;
END $$;

COMMIT;

-- ============================================================
-- 执行结果校验：所有查询均限定在本脚本创建的上海医院范围内。
-- ============================================================

-- 医院、科室、医生、药房、药品和库存数量应分别为 1、3、6、2、3、6。
SELECT
    h.name AS hospital_name,
    COUNT(DISTINCT d.id) FILTER (WHERE d.deleted_at IS NULL) AS department_count,
    COUNT(DISTINCT doc.id) FILTER (WHERE doc.deleted_at IS NULL) AS doctor_count,
    COUNT(DISTINCT p.id) FILTER (WHERE p.deleted_at IS NULL) AS pharmacy_count,
    COUNT(DISTINCT dr.id) FILTER (WHERE dr.deleted_at IS NULL) AS drug_count,
    COUNT(DISTINCT pds.id) AS stock_count
FROM hospital h
LEFT JOIN department d ON d.hospital_id = h.id
LEFT JOIN doctor doc ON doc.hospital_id = h.id
LEFT JOIN pharmacy p ON p.hospital_id = h.id
LEFT JOIN drug dr ON dr.hospital_id = h.id
LEFT JOIN pharmacy_drug_stock pds ON pds.pharmacy_id = p.id AND pds.drug_id = dr.id
WHERE h.name = '上海健康示范医院'
  AND h.address = '上海市浦东新区健康路88号'
  AND h.deleted_at IS NULL
GROUP BY h.id, h.name;

-- 每名种子医生应有 3 个已发布排班、6 个时段和 30 个可用号源快照。
SELECT
    doc.name AS doctor_name,
    doc.license_no,
    COUNT(DISTINCT s.id) FILTER (WHERE s.status = 'PUBLISHED' AND s.deleted_at IS NULL) AS published_schedule_count,
    COUNT(DISTINCT sl.id) FILTER (WHERE sl.deleted_at IS NULL) AS slot_count,
    COUNT(ss.id) FILTER (WHERE ss.status = 'AVAILABLE' AND ss.deleted_at IS NULL) AS available_snapshot_count
FROM doctor doc
LEFT JOIN schedule s ON s.doctor_id = doc.id
LEFT JOIN slot sl ON sl.schedule_id = s.id
LEFT JOIN slot_snapshot ss ON ss.slot_id = sl.id
WHERE doc.hospital_id = (
    SELECT id
    FROM hospital
    WHERE name = '上海健康示范医院'
      AND address = '上海市浦东新区健康路88号'
      AND deleted_at IS NULL
    ORDER BY id
    LIMIT 1
)
  AND doc.license_no LIKE 'SHHP-%'
  AND doc.deleted_at IS NULL
GROUP BY doc.id, doc.name, doc.license_no
ORDER BY doc.license_no;

-- ============================================================
-- test001 家庭病历报告演示数据
-- 执行环境：PostgreSQL
-- 执行时机：在基础建表、C 端问诊补充及医生病历报告解读补充脚本执行完成后运行
-- 说明：为 test001 本人及其 PARENT 关联家人各创建两条已完成问诊和 READY 病历解读；可重复执行
-- ============================================================

BEGIN;

DO $$
DECLARE
    v_user_id bigint;
    v_self_patient_id bigint;
    v_family_patient_id bigint;
    v_seed record;
    v_patient_id bigint;
    v_consult_id bigint;
    v_appointment_id bigint;
    v_prescription_id bigint;
    v_slot_snapshot_id bigint;
    v_slot_id bigint;
    v_doctor_id bigint;
    v_drug_id bigint;
    v_registration_fee_cent integer;
    v_visit_at timestamptz;
BEGIN
    -- 校验演示账号存在且处于启用状态，避免将病历报告写入无效账号。
    SELECT id
    INTO v_user_id
    FROM c_user
    WHERE account = 'test001'
      AND status = 'ENABLED'
      AND deleted_at IS NULL
    ORDER BY id
    LIMIT 1;

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION '未找到启用状态的 test001 演示账号，无法初始化家庭病历报告数据。';
    END IF;

    -- 定位 test001 本人就诊人，后续问诊与报告均通过 patient_id 保持数据归属。
    SELECT patient_id
    INTO v_self_patient_id
    FROM patient_user_relation
    WHERE user_id = v_user_id
      AND relationship = 'SELF'
      AND deleted_at IS NULL
    ORDER BY is_default DESC, id
    LIMIT 1;

    IF v_self_patient_id IS NULL THEN
        RAISE EXCEPTION 'test001 未关联本人就诊人，无法初始化本人病历报告数据。';
    END IF;

    -- 定位 test001 的父母就诊人，作为家庭成员报告的归属对象。
    SELECT patient_id
    INTO v_family_patient_id
    FROM patient_user_relation
    WHERE user_id = v_user_id
      AND relationship = 'PARENT'
      AND deleted_at IS NULL
    ORDER BY id
    LIMIT 1;

    IF v_family_patient_id IS NULL THEN
        RAISE EXCEPTION 'test001 未关联 PARENT 家庭成员，无法初始化家庭成员病历报告数据。';
    END IF;

    -- 每个固定主诉对应一条独立问诊和解读，主诉作为可重复执行的稳定演示标识。
    FOR v_seed IN
        SELECT *
        FROM (
            VALUES
                (
                    'SELF'::varchar(20),
                    '[演示] test001 春季过敏复诊'::varchar(1000),
                    '医生病历解读：结合本次问诊记录，症状与季节性过敏表现相符。建议避免已知诱因，按医嘱规范使用对症药物；若出现持续喘憋、面部肿胀或症状加重，请及时线下就医。'::text,
                    18::integer,
                    '氯雷他定片'::varchar(200),
                    '10mg/次'::varchar(50),
                    '每日一次'::varchar(50),
                    '口服'::varchar(50),
                    7::smallint,
                    2::smallint
                ),
                (
                    'SELF'::varchar(20),
                    '[演示] test001 头痛发热复诊'::varchar(1000),
                    '医生病历解读：本次问诊用于观察头痛、发热等不适的恢复情况。请保证休息和补充水分；若高热持续、头痛明显加重或伴随意识异常，请立即线下就医。'::text,
                    12::integer,
                    '布洛芬缓释胶囊'::varchar(200),
                    '0.3g/次'::varchar(50),
                    '必要时每日不超过两次'::varchar(50),
                    '口服'::varchar(50),
                    3::smallint,
                    1::smallint
                ),
                (
                    'PARENT'::varchar(20),
                    '[演示] test001 家人上呼吸道感染复诊'::varchar(1000),
                    '医生病历解读：呼吸道症状恢复期间请注意休息、补充水分并观察体温变化。若咳嗽加重、呼吸困难或发热反复，请及时线下复诊。'::text,
                    21::integer,
                    '阿莫西林胶囊'::varchar(200),
                    '0.25g/次'::varchar(50),
                    '每日三次'::varchar(50),
                    '口服'::varchar(50),
                    5::smallint,
                    2::smallint
                ),
                (
                    'PARENT'::varchar(20),
                    '[演示] test001 家人膝关节不适复诊'::varchar(1000),
                    '医生病历解读：膝关节不适期间建议适度休息，避免长时间负重、爬楼和剧烈运动，可进行温和的关节活动训练。疼痛加重、关节红肿发热或活动受限时，请及时至骨科线下评估。'::text,
                    9::integer,
                    '布洛芬缓释胶囊'::varchar(200),
                    '0.3g/次'::varchar(50),
                    '必要时每日不超过两次'::varchar(50),
                    '口服'::varchar(50),
                    3::smallint,
                    1::smallint
                )
        ) AS report_seed(
            patient_role,
            chief_complaint,
            interpretation_content,
            days_ago,
            drug_name,
            dosage,
            frequency,
            usage_method,
            medication_days,
            quantity
        )
    LOOP
        -- 根据家庭角色选择报告归属患者，防止本人和家人的问诊记录混用。
        v_patient_id := CASE v_seed.patient_role
            WHEN 'SELF' THEN v_self_patient_id
            WHEN 'PARENT' THEN v_family_patient_id
        END;
        v_visit_at := now() - make_interval(days => v_seed.days_ago);

        -- 优先复用本脚本已创建的问诊，避免重复执行时重复占用号源。
        SELECT id
        INTO v_consult_id
        FROM consult_record
        WHERE patient_id = v_patient_id
          AND chief_complaint = v_seed.chief_complaint
          AND deleted_at IS NULL
        ORDER BY id
        LIMIT 1;

        IF v_consult_id IS NULL THEN
            -- 锁定一条空闲号源快照，并获取其对应医生和挂号费用以构造完整预约链路。
            SELECT ss.id, ss.slot_id, d.id, d.registration_fee_cent
            INTO v_slot_snapshot_id, v_slot_id, v_doctor_id, v_registration_fee_cent
            FROM slot_snapshot ss
            JOIN slot s
              ON s.id = ss.slot_id
             AND s.deleted_at IS NULL
            JOIN schedule sch
              ON sch.id = s.schedule_id
             AND sch.deleted_at IS NULL
            JOIN doctor d
              ON d.id = sch.doctor_id
             AND d.deleted_at IS NULL
             AND d.status = 'ENABLED'
            WHERE ss.status = 'AVAILABLE'
              AND ss.patient_id IS NULL
              AND ss.deleted_at IS NULL
              AND s.remain_count > 0
            ORDER BY ss.id
            LIMIT 1
            FOR UPDATE OF ss, s SKIP LOCKED;

            IF v_slot_snapshot_id IS NULL THEN
                RAISE EXCEPTION '缺少可用号源快照，无法为 % 创建演示问诊。', v_seed.chief_complaint;
            END IF;

            -- 将号源快照标记为已售出，并同步减少号源余量，保证演示数据状态一致。
            UPDATE slot_snapshot
            SET patient_id = v_patient_id,
                status = 'SOLD',
                locked_at = v_visit_at - interval '10 minutes',
                sold_at = v_visit_at - interval '5 minutes',
                updated_at = now()
            WHERE id = v_slot_snapshot_id;

            UPDATE slot
            SET remain_count = remain_count - 1,
                updated_at = now()
            WHERE id = v_slot_id
              AND remain_count > 0;

            -- 创建已完成预约，为问诊记录满足外键和状态流转前置条件。
            INSERT INTO appointment (
                slot_snapshot_id,
                patient_id,
                doctor_id,
                status,
                amount_cent,
                paid_at,
                created_at,
                updated_at
            )
            VALUES (
                v_slot_snapshot_id,
                v_patient_id,
                v_doctor_id,
                'COMPLETED',
                v_registration_fee_cent,
                v_visit_at - interval '5 minutes',
                v_visit_at - interval '10 minutes',
                v_visit_at
            )
            RETURNING id INTO v_appointment_id;

            -- 创建已完成问诊，供病历报告解读以 consult_id 建立唯一关联。
            INSERT INTO consult_record (
                appointment_id,
                doctor_id,
                patient_id,
                status,
                chief_complaint,
                doctor_note,
                started_at,
                ended_at,
                created_at,
                updated_at
            )
            VALUES (
                v_appointment_id,
                v_doctor_id,
                v_patient_id,
                'COMPLETED',
                v_seed.chief_complaint,
                '用于 test001 家庭病历报告演示的已完成问诊记录。',
                v_visit_at,
                v_visit_at + interval '15 minutes',
                v_visit_at,
                v_visit_at + interval '15 minutes'
            )
            RETURNING id INTO v_consult_id;
        END IF;

        -- 按问诊反查医生，保证重复执行时处方仍与原问诊医生保持一致。
        SELECT doctor_id
        INTO v_doctor_id
        FROM consult_record
        WHERE id = v_consult_id
          AND deleted_at IS NULL;

        -- 仅选用上海健康示范医院启用药房中库存高于安全库存的药品，确保处方药品可由院内药房供应。
        SELECT d.id
        INTO v_drug_id
        FROM hospital h
        JOIN pharmacy p
          ON p.hospital_id = h.id
         AND p.deleted_at IS NULL
         AND p.status = 'ENABLED'
        JOIN pharmacy_drug_stock pds
          ON pds.pharmacy_id = p.id
         AND pds.available_count > pds.safety_stock
        JOIN drug d
          ON d.id = pds.drug_id
         AND d.deleted_at IS NULL
         AND d.status = 'ENABLED'
        WHERE h.name = '上海健康示范医院'
          AND h.deleted_at IS NULL
          AND d.name = v_seed.drug_name
        ORDER BY p.is_default DESC, p.id
        LIMIT 1;

        IF v_drug_id IS NULL THEN
            RAISE EXCEPTION '上海健康示范医院启用药房中缺少库存充足的药品 %，无法创建演示处方。', v_seed.drug_name;
        END IF;

        -- 每条演示问诊复用已有有效处方，避免重复执行时产生多张同源处方。
        SELECT id
        INTO v_prescription_id
        FROM prescription
        WHERE consult_id = v_consult_id
          AND deleted_at IS NULL
        ORDER BY id
        LIMIT 1;

        IF v_prescription_id IS NULL THEN
            -- 创建已审核通过的处方，使 C 端能够读取处方及药品明细。
            INSERT INTO prescription (
                consult_id,
                doctor_id,
                patient_id,
                status,
                issued_at,
                audited_at,
                created_at,
                updated_at
            )
            VALUES (
                v_consult_id,
                v_doctor_id,
                v_patient_id,
                'APPROVED',
                v_visit_at + interval '16 minutes',
                v_visit_at + interval '18 minutes',
                v_visit_at + interval '16 minutes',
                v_visit_at + interval '18 minutes'
            )
            RETURNING id INTO v_prescription_id;
        END IF;

        -- 按处方和药品双重判断明细是否已存在，保证初始化脚本可安全重复执行。
        IF NOT EXISTS (
            SELECT 1
            FROM prescription_item
            WHERE prescription_id = v_prescription_id
              AND drug_id = v_drug_id
        ) THEN
            INSERT INTO prescription_item (
                prescription_id,
                drug_id,
                dosage,
                frequency,
                usage_method,
                days,
                quantity,
                created_at
            )
            VALUES (
                v_prescription_id,
                v_drug_id,
                v_seed.dosage,
                v_seed.frequency,
                v_seed.usage_method,
                v_seed.medication_days,
                v_seed.quantity,
                v_visit_at + interval '16 minutes'
            );
        END IF;

        -- 按有效问诊唯一索引写入或刷新 READY 解读，确保重复执行结果稳定。
        INSERT INTO consultation_report_interpretation (
            consult_id,
            content,
            disclaimer,
            status,
            generated_at,
            created_at,
            updated_at
        )
        VALUES (
            v_consult_id,
            v_seed.interpretation_content,
            '本解读仅供健康管理和演示参考，不能替代执业医师的线下诊断与治疗建议。',
            'READY',
            v_visit_at + interval '20 minutes',
            v_visit_at + interval '20 minutes',
            v_visit_at + interval '20 minutes'
        )
        ON CONFLICT (consult_id) WHERE deleted_at IS NULL
        DO UPDATE SET
            content = EXCLUDED.content,
            disclaimer = EXCLUDED.disclaimer,
            status = EXCLUDED.status,
            generated_at = EXCLUDED.generated_at,
            updated_at = EXCLUDED.updated_at;
    END LOOP;
END $$;

COMMIT;

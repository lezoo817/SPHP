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
    v_slot_snapshot_id bigint;
    v_slot_id bigint;
    v_doctor_id bigint;
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
                    18::integer
                ),
                (
                    'SELF'::varchar(20),
                    '[演示] test001 体检指标随访'::varchar(1000),
                    '医生病历解读：本次为体检后随访，建议保持规律作息、均衡饮食和适量运动，并按医生建议复查相关指标。复查结果异常或出现不适时，应及时前往医院进一步评估。'::text,
                    12::integer
                ),
                (
                    'PARENT'::varchar(20),
                    '[演示] test001 家人血压管理随访'::varchar(1000),
                    '医生病历解读：建议家属协助记录晨起及睡前血压，规律服用既往医生开具的药物，减少高盐饮食。若连续多日血压明显升高或伴随头晕、胸闷等症状，请尽快线下复诊。'::text,
                    21::integer
                ),
                (
                    'PARENT'::varchar(20),
                    '[演示] test001 家人膝关节不适复诊'::varchar(1000),
                    '医生病历解读：膝关节不适期间建议适度休息，避免长时间负重、爬楼和剧烈运动，可进行温和的关节活动训练。疼痛加重、关节红肿发热或活动受限时，请及时至骨科线下评估。'::text,
                    9::integer
                )
        ) AS report_seed(patient_role, chief_complaint, interpretation_content, days_ago)
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

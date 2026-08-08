/**
 * query_patient_history 确认后的患者档案结构化展示。
 *
 * query_patient_history 是 B 端 L2 读工具：医生确认后，后端在 chat_confirm 响应的
 * action_result 中返回聚合档案（基本信息 / 就诊记录 / 历史处方 / 当前用药 / 随访计划）。
 * L2 结果不走 reply_node 的 LLM 总结（后端 PHI 保护，仅 ID 摘要回注下一轮），故由
 * 本组件在前端把 action_result 结构化渲染给医生。
 *
 * action_result 结构（每个子项是 Java Result 信封 {code, message, data}，需解一层）：
 *   { base_info, visits, prescriptions, medications }
 *   - base_info.data: PatientDetailInfo（患者基本信息 + 过敏史 + 既往史）
 *   - visits.data: PageResult<PatientVisitItem>（{list: 就诊记录[]}）
 *   - prescriptions.data: PageResult<PatientPrescriptionItem>（{list: 历史处方[]}）
 *   - medications.data: PatientMedicationResult（{medicationPlans, followUpPlans}）
 */
import type { ReactNode } from 'react';
import { Descriptions, Empty, List, Tag, Typography } from 'antd';

const { Text } = Typography;

/** 性别枚举到中文。 */
const GENDER_TEXT: Record<string, string> = {
  MALE: '男',
  FEMALE: '女',
  UNKNOWN: '未知',
};

/** 安全转字符串；空值统一显示 "-"。 */
function str(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-';
  return String(value);
}

/** 解 Java Result 信封取内层 data；非信封原样返回。 */
function unwrap(envelope: unknown): unknown {
  if (envelope && typeof envelope === 'object' && 'data' in envelope) {
    return (envelope as { data?: unknown }).data;
  }
  return envelope;
}

/** 取对象（非 null/数组）；不匹配返回 null。 */
function asObject(value: unknown): Record<string, unknown> | null {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  return null;
}

/** 取列表（兼容 PageResult.list 与裸数组）。 */
function asList(value: unknown): unknown[] {
  if (Array.isArray(value)) return value;
  const obj = asObject(value);
  if (obj && 'list' in obj) {
    const list = obj.list;
    return Array.isArray(list) ? list : [];
  }
  return [];
}

/** 档案区块标题。 */
function SectionTitle({ children }: { children: ReactNode }) {
  return (
    <div style={{ marginTop: 12, marginBottom: 4, color: '#1890ff', fontSize: 13, fontWeight: 600 }}>
      {children}
    </div>
  );
}

/**
 * 渲染 query_patient_history 的患者档案。
 * @param result confirm() 回写到卡片的 action_result（未知结构，防御性解析）
 */
export function AgentPatientHistoryResult({ result }: { result: unknown }) {
  const archive = asObject(result);
  if (!archive) {
    return <Empty description="档案数据为空" image={Empty.PRESENTED_IMAGE_SIMPLE} />;
  }

  // 逐项解信封（Java Result 信封 {code, message, data}，成功时 code=00000）
  const base = asObject(unwrap(archive.base_info)) as (API.PatientDetailInfo & Record<string, unknown>) | null;
  const visits = asList(unwrap(archive.visits)) as API.PatientVisitItem[];
  const prescriptions = asList(unwrap(archive.prescriptions)) as API.PatientPrescriptionItem[];
  const medications = asObject(unwrap(archive.medications));
  const medicationPlans = asList(medications?.medicationPlans) as API.MedicationPlanItem[];
  const followUps = asList(medications?.followUpPlans) as API.FollowUpPlanItem[];

  // base_info 内嵌过敏史 / 既往史
  const allergies = (base?.allergies ?? []) as API.AllergyInfo[];
  const medicalHistories = (base?.medicalHistories ?? []) as API.MedicalHistoryInfo[];

  // 无任何数据时给一个兜底提示
  const hasData = base || visits.length || prescriptions.length || medicationPlans.length;
  if (!hasData) {
    return <Empty description="未查询到档案记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />;
  }

  return (
    <div style={{ marginTop: 8 }}>
      {/* ========== 基本信息 ========== */}
      {base && (
        <>
          <SectionTitle>基本信息</SectionTitle>
          <Descriptions size="small" column={2} colon={false}>
            <Descriptions.Item label="姓名">{str(base.name)}</Descriptions.Item>
            <Descriptions.Item label="性别">
              {GENDER_TEXT[String(base.gender)] ?? str(base.gender)}
            </Descriptions.Item>
            <Descriptions.Item label="出生日期">{str(base.dateOfBirth)}</Descriptions.Item>
            <Descriptions.Item label="电话">{str(base.phone)}</Descriptions.Item>
            <Descriptions.Item label="紧急联系人">{str(base.emergencyContact)}</Descriptions.Item>
          </Descriptions>
        </>
      )}

      {/* ========== 过敏史 ========== */}
      {allergies.length > 0 && (
        <>
          <SectionTitle>过敏史</SectionTitle>
          <div>
            {allergies.map((a) => (
              <Tag key={a.id} color="red" style={{ marginBottom: 4 }}>
                {str(a.allergen)}
                {a.reaction ? `（${a.reaction}）` : ''}
                {a.severity ? ` [${a.severity}]` : ''}
              </Tag>
            ))}
          </div>
        </>
      )}

      {/* ========== 既往史 ========== */}
      {medicalHistories.length > 0 && (
        <>
          <SectionTitle>既往史</SectionTitle>
          <List
            size="small"
            dataSource={medicalHistories}
            renderItem={(h) => (
              <List.Item style={{ padding: '4px 0' }}>
                <div>
                  <Text>{str(h.content)}</Text>
                  {h.occurredAt && (
                    <Text type="secondary" style={{ fontSize: 12, marginLeft: 8 }}>
                      {h.occurredAt}
                    </Text>
                  )}
                </div>
              </List.Item>
            )}
          />
        </>
      )}

      {/* ========== 就诊记录 ========== */}
      <SectionTitle>历史就诊记录（{visits.length}）</SectionTitle>
      {visits.length === 0 ? (
        <Text type="secondary" style={{ fontSize: 12 }}>暂无就诊记录</Text>
      ) : (
        <List
          size="small"
          dataSource={visits}
          renderItem={(v) => (
            <List.Item style={{ padding: '4px 0' }}>
              <div style={{ width: '100%' }}>
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>{str(v.visitDate)}</Text>
                  <Tag style={{ marginLeft: 8 }}>{str(v.deptName)}</Tag>
                  <Text type="secondary" style={{ fontSize: 12, marginLeft: 4 }}>
                    {str(v.doctorName)}
                  </Text>
                </div>
                {v.summary && (
                  <Text type="secondary" style={{ fontSize: 12 }}>{v.summary}</Text>
                )}
              </div>
            </List.Item>
          )}
        />
      )}

      {/* ========== 历史处方 ========== */}
      <SectionTitle>历史处方（{prescriptions.length}）</SectionTitle>
      {prescriptions.length === 0 ? (
        <Text type="secondary" style={{ fontSize: 12 }}>暂无处方</Text>
      ) : (
        <List
          size="small"
          dataSource={prescriptions}
          renderItem={(p) => (
            <List.Item style={{ padding: '4px 0' }}>
              <div style={{ width: '100%' }}>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  处方 #{p.id} · {str(p.doctorName)} · {str(p.itemCount)} 项
                </Text>
                <Tag style={{ marginLeft: 8 }}>{str(p.status)}</Tag>
                {p.issuedAt && (
                  <Text type="secondary" style={{ fontSize: 12, marginLeft: 4 }}>
                    {p.issuedAt}
                  </Text>
                )}
              </div>
            </List.Item>
          )}
        />
      )}

      {/* ========== 当前用药 ========== */}
      <SectionTitle>当前用药清单（{medicationPlans.length}）</SectionTitle>
      {medicationPlans.length === 0 ? (
        <Text type="secondary" style={{ fontSize: 12 }}>暂无在用药品</Text>
      ) : (
        <List
          size="small"
          dataSource={medicationPlans}
          renderItem={(m) => (
            <List.Item style={{ padding: '4px 0' }}>
              <div style={{ width: '100%' }}>
                <Text>{str(m.drugName)}</Text>
                <Tag style={{ marginLeft: 8 }}>{str(m.status)}</Tag>
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {str(m.dosage)} · {str(m.frequency)} · {str(m.usageMethod)}
                  </Text>
                </div>
              </div>
            </List.Item>
          )}
        />
      )}

      {/* ========== 随访计划（可能为空，不渲染） ========== */}
      {followUps.length > 0 && (
        <>
          <SectionTitle>随访计划（{followUps.length}）</SectionTitle>
          <List
            size="small"
            dataSource={followUps}
            renderItem={(f) => (
              <List.Item style={{ padding: '4px 0' }}>
                <div style={{ width: '100%' }}>
                  <Text>{str(f.followUpType)} · {str(f.content)}</Text>
                  <Tag style={{ marginLeft: 8 }}>{str(f.status)}</Tag>
                  <div>
                    <Text type="secondary" style={{ fontSize: 12 }}>到期：{str(f.dueAt)}</Text>
                  </div>
                </div>
              </List.Item>
            )}
          />
        </>
      )}
    </div>
  );
}

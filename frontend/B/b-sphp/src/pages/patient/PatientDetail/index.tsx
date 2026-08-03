/**
 * 患者详情页
 * - 多 Tab 展示：基本信息、就诊记录、历史处方、当前用药与随访
 * - 从路由参数获取患者ID
 */
import { Tag, message, Tabs, Descriptions, Card, Row, Col, Space, Spin, Empty, Table } from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import { useParams, history } from '@umijs/max';
import { useState, useEffect, useRef, useCallback } from 'react';
import type { ActionType } from '@ant-design/pro-components';
import {
  getPatientInfo,
  getPatientVisits,
  getPatientPrescriptions,
  getPatientMedications,
} from '@/services/admin';

/** 性别映射 */
const genderMap: Record<string, string> = {
  MALE: '男',
  FEMALE: '女',
  UNKNOWN: '未知',
};

/** 性别标签颜色 */
const genderColor: Record<string, string> = {
  MALE: 'blue',
  FEMALE: 'magenta',
  UNKNOWN: 'default',
};

/** 处方状态映射 */
const prescriptionStatusMap: Record<string, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  SUBMITTED: { text: '待审核', color: 'processing' },
  APPROVED: { text: '已通过', color: 'success' },
  REJECTED: { text: '已驳回', color: 'error' },
  CANCELLED: { text: '已作废', color: 'warning' },
};

/** 就诊状态映射 */
const visitStatusMap: Record<string, { text: string; color: string }> = {
  PENDING: { text: '待接诊', color: 'processing' },
  IN_PROGRESS: { text: '接诊中', color: 'warning' },
  COMPLETED: { text: '已完成', color: 'success' },
  NO_SHOW: { text: '未到诊', color: 'error' },
};

/** 用药状态映射 */
const medicationStatusMap: Record<string, { text: string; color: string }> = {
  ACTIVE: { text: '进行中', color: 'success' },
  PAUSED: { text: '已暂停', color: 'warning' },
  COMPLETED: { text: '已完成', color: 'default' },
};

/** 随访状态映射 */
const followUpStatusMap: Record<string, { text: string; color: string }> = {
  PENDING_CONFIRM: { text: '待确认', color: 'processing' },
  CONFIRMED: { text: '已确认', color: 'warning' },
  COMPLETED: { text: '已完成', color: 'success' },
  CANCELLED: { text: '已取消', color: 'error' },
};

export default function PatientDetail() {
  const { id } = useParams<{ id: string }>();
  const patientId = Number(id);
  const [loading, setLoading] = useState(true);
  const [detail, setDetail] = useState<API.PatientDetailInfo | null>(null);
  const [activeTab, setActiveTab] = useState('info');
  const visitActionRef = useRef<ActionType>();
  const prescriptionActionRef = useRef<ActionType>();

  /** 加载患者详情 */
  const loadDetail = useCallback(async () => {
    if (!patientId) return;
    setLoading(true);
    try {
      const data = await getPatientInfo(patientId);
      setDetail(data);
    } catch (err: any) {
      message.error(err?.message || '查询患者详情失败');
    } finally {
      setLoading(false);
    }
  }, [patientId]);

  useEffect(() => {
    loadDetail();
  }, [loadDetail]);

  if (!patientId) {
    return <Empty description="缺少患者ID" />;
  }

  /** 就诊记录列 */
  const visitColumns: ProColumns<API.PatientVisitItem>[] = [
    { title: '就诊日期', dataIndex: 'visitDate', width: 120 },
    { title: '医生', dataIndex: 'doctorName', width: 120, ellipsis: true },
    { title: '科室', dataIndex: 'deptName', width: 120, ellipsis: true },
    {
      title: '诊断摘要', dataIndex: 'summary', ellipsis: true,
      render: (_, record) => record.summary || '-',
    },
    {
      title: '状态', dataIndex: 'status', width: 100,
      render: (_, record) => {
        const s = visitStatusMap[record.status] ?? { text: record.status, color: 'default' };
        return <Tag color={s.color}>{s.text}</Tag>;
      },
    },
  ];

  /** 历史处方列 */
  const prescriptionColumns: ProColumns<API.PatientPrescriptionItem>[] = [
    { title: '处方ID', dataIndex: 'id', width: 100 },
    { title: '医生', dataIndex: 'doctorName', width: 120, ellipsis: true },
    {
      title: '状态', dataIndex: 'status', width: 100,
      render: (_, record) => {
        const s = prescriptionStatusMap[record.status] ?? { text: record.status, color: 'default' };
        return <Tag color={s.color}>{s.text}</Tag>;
      },
    },
    { title: '药品数量', dataIndex: 'itemCount', width: 100 },
    { title: '签发时间', dataIndex: 'issuedAt', width: 160, render: (_, r) => r.issuedAt || '-' },
    { title: '创建时间', dataIndex: 'createdAt', width: 160 },
  ];

  const tabItems = [
    {
      key: 'info',
      label: '基本信息',
      children: (
        <Spin spinning={loading}>
          {detail ? (
            <>
              <Card title="个人信息" size="small" style={{ marginBottom: 16 }}>
                <Descriptions column={2} size="small">
                  <Descriptions.Item label="姓名">{detail.name}</Descriptions.Item>
                  <Descriptions.Item label="性别">
                    <Tag color={genderColor[detail.gender] ?? 'default'}>
                      {genderMap[detail.gender] ?? detail.gender}
                    </Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="出生日期">{detail.dateOfBirth || '-'}</Descriptions.Item>
                  <Descriptions.Item label="年龄">{detail.dateOfBirth ? calculateAge(detail.dateOfBirth) : '-'}</Descriptions.Item>
                  <Descriptions.Item label="手机号">{detail.phone || '-'}</Descriptions.Item>
                  <Descriptions.Item label="紧急联系人">{detail.emergencyContact || '-'}</Descriptions.Item>
                </Descriptions>
              </Card>

              <Card title="过敏史" size="small" style={{ marginBottom: 16 }}>
                {detail.allergies.length > 0 ? (
                  <Table
                    rowKey="id"
                    dataSource={detail.allergies}
                    columns={[
                      { title: '过敏原', dataIndex: 'allergen', width: 150 },
                      { title: '反应', dataIndex: 'reaction', ellipsis: true },
                      {
                        title: '严重程度', dataIndex: 'severity', width: 120,
                        render: (_, r) => {
                          const colorMap: Record<string, string> = { MILD: 'green', MODERATE: 'orange', SEVERE: 'red' };
                          return <Tag color={colorMap[r.severity] ?? 'default'}>{r.severity}</Tag>;
                        },
                      },
                    ]}
                    pagination={false}
                    size="small"
                  />
                ) : (
                  <Empty description="无过敏史记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                )}
              </Card>

              <Card title="既往史" size="small">
                {detail.medicalHistories.length > 0 ? (
                  <Table
                    rowKey="id"
                    dataSource={detail.medicalHistories}
                    columns={[
                      { title: '内容', dataIndex: 'content', ellipsis: true },
                      { title: '发生日期', dataIndex: 'occurredAt', width: 120 },
                    ]}
                    pagination={false}
                    size="small"
                  />
                ) : (
                  <Empty description="无既往史记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                )}
              </Card>
            </>
          ) : (
            !loading && <Empty description="未找到患者信息" />
          )}
        </Spin>
      ),
    },
    {
      key: 'visits',
      label: '就诊记录',
      children: (
        <ProTable<API.PatientVisitItem>
          actionRef={visitActionRef}
          rowKey="consultId"
          columns={visitColumns}
          request={async (params) => {
            const { current, pageSize } = params;
            try {
              const res = await getPatientVisits(patientId, { page: current, size: pageSize });
              return { data: res.list, total: res.total, success: true };
            } catch (err: any) {
              message.error(err?.message || '查询就诊记录失败');
              return { data: [], total: 0, success: true };
            }
          }}
          search={false}
          pagination={{ pageSize: 10 }}
          toolBarRender={false}
        />
      ),
    },
    {
      key: 'prescriptions',
      label: '历史处方',
      children: (
        <ProTable<API.PatientPrescriptionItem>
          actionRef={prescriptionActionRef}
          rowKey="id"
          columns={prescriptionColumns}
          request={async (params) => {
            const { current, pageSize } = params;
            try {
              const res = await getPatientPrescriptions(patientId, { page: current, size: pageSize });
              return { data: res.list, total: res.total, success: true };
            } catch (err: any) {
              message.error(err?.message || '查询历史处方失败');
              return { data: [], total: 0, success: true };
            }
          }}
          search={false}
          pagination={{ pageSize: 10 }}
          toolBarRender={false}
        />
      ),
    },
    {
      key: 'medications',
      label: '当前用药与随访',
      children: <MedicationsTab patientId={patientId} />,
    },
  ];

  return (
    <div>
      <Card
        size="small"
        style={{ marginBottom: 16 }}
        extra={
          <a onClick={() => history.push('/patient/list')}>返回列表</a>
        }
      >
        <Space>
          <strong>患者详情</strong>
          {detail && <span>— {detail.name}</span>}
        </Space>
      </Card>

      <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabItems} />
    </div>
  );
}

/** 当前用药与随访 Tab 子组件 */
function MedicationsTab({ patientId }: { patientId: number }) {
  const [loading, setLoading] = useState(true);
  const [data, setData] = useState<API.PatientMedicationResult | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    getPatientMedications(patientId)
      .then((res) => {
        if (!cancelled) setData(res);
      })
      .catch((err: any) => {
        if (!cancelled) message.error(err?.message || '查询用药信息失败');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [patientId]);

  if (loading) {
    return <Spin style={{ display: 'block', margin: '48px auto' }} />;
  }

  if (!data || (data.medicationPlans.length === 0 && data.followUpPlans.length === 0)) {
    return <Empty description="暂无用药与随访计划" />;
  }

  return (
    <Row gutter={[16, 16]}>
      <Col span={24}>
        <Card title="用药计划" size="small">
          {data.medicationPlans.length > 0 ? (
            <Row gutter={[12, 12]}>
              {data.medicationPlans.map((plan) => (
                <Col key={plan.id} xs={24} sm={12} lg={8}>
                  <Card size="small" variant="outlined">
                    <Descriptions column={1} size="small">
                      <Descriptions.Item label="药品">{plan.drugName}</Descriptions.Item>
                      <Descriptions.Item label="用量">{plan.dosage}</Descriptions.Item>
                      <Descriptions.Item label="频次">{plan.frequency}</Descriptions.Item>
                      <Descriptions.Item label="用法">{plan.usageMethod}</Descriptions.Item>
                      <Descriptions.Item label="状态">
                        <Tag color={medicationStatusMap[plan.status]?.color ?? 'default'}>
                          {medicationStatusMap[plan.status]?.text ?? plan.status}
                        </Tag>
                      </Descriptions.Item>
                    </Descriptions>
                  </Card>
                </Col>
              ))}
            </Row>
          ) : (
            <Empty description="暂无用药计划" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </Card>
      </Col>
      <Col span={24}>
        <Card title="随访计划" size="small">
          {data.followUpPlans.length > 0 ? (
            <Row gutter={[12, 12]}>
              {data.followUpPlans.map((plan) => (
                <Col key={plan.id} xs={24} sm={12} lg={8}>
                  <Card size="small" variant="outlined">
                    <Descriptions column={1} size="small">
                      <Descriptions.Item label="类型">{plan.followUpType || '-'}</Descriptions.Item>
                      <Descriptions.Item label="内容">{plan.content || '-'}</Descriptions.Item>
                      <Descriptions.Item label="到期时间">{plan.dueAt}</Descriptions.Item>
                      <Descriptions.Item label="状态">
                        <Tag color={followUpStatusMap[plan.status]?.color ?? 'default'}>
                          {followUpStatusMap[plan.status]?.text ?? plan.status}
                        </Tag>
                      </Descriptions.Item>
                    </Descriptions>
                  </Card>
                </Col>
              ))}
            </Row>
          ) : (
            <Empty description="暂无随访计划" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </Card>
      </Col>
    </Row>
  );
}

/** 根据出生日期计算年龄 */
function calculateAge(dateOfBirth: string): number {
  const birth = new Date(dateOfBirth);
  const today = new Date();
  let age = today.getFullYear() - birth.getFullYear();
  const m = today.getMonth() - birth.getMonth();
  if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) {
    age--;
  }
  return age;
}
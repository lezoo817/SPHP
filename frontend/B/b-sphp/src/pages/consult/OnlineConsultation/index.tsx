/**
 * B 端无挂号在线问诊工作台。
 *
 * 医生从待回复列表进入接诊状态，在问诊期间与患者实时双向文字沟通。
 */
import {
  Alert,
  Button,
  Descriptions,
  Divider,
  Empty,
  Form,
  Input,
  InputNumber,
  List,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  MedicineBoxOutlined,
  MessageOutlined,
  PlusOutlined,
  SendOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import {
  getOnlineConsultationDetail,
  getOnlineConsultations,
  getDoctorDrugs,
  getTemplates,
  endOnlineConsultation,
  sendOnlineConsultationMessage,
  startOnlineConsultation,
  submitPrescription,
} from '@/services/admin';
import { createConsultationSocket } from '@/services/consultationSocket';
import { QUERY_KEYS, STALE_TIME } from '@/constants/queryKeys';
import { POLL_INTERVAL_CONSULT } from '@/constants/timing';
import { getErrorMessage } from '@/utils/error';
import styles from './index.module.less';

const { Text, Title } = Typography;
const { TextArea } = Input;

type OnlineStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';

interface PrescriptionFormValues {
  items: Array<{
    drugId: number;
    dosage: string;
    frequency: string;
    usageMethod: string;
    days: number;
    quantity: number;
  }>;
}

interface AllergySummaryItem {
  allergen?: string;
  reaction?: string;
}

interface MedicalHistorySummaryItem {
  content?: string;
  name?: string;
  occurredAt?: string;
  date?: string;
}

/** 将后端性别枚举转换为 B 端展示文案。 */
function formatGender(gender?: string): string {
  if (gender === 'MALE' || gender === '男') return '男';
  if (gender === 'FEMALE' || gender === '女') return '女';
  return '未知';
}

/** 将预问诊中的日期统一格式化为 YYYY/MM/DD。 */
function formatHistoryDate(value?: string): string {
  if (!value) return '-';
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format('YYYY/MM/DD') : value;
}

/** 读取 AI 摘要中的数组字段，兼容接口返回空值或旧格式。 */
function readSummaryArray<T>(summary: Record<string, unknown> | undefined, key: string): T[] {
  const value = summary?.[key];
  return Array.isArray(value) ? (value as T[]) : [];
}

const STATUS_TEXT: Record<OnlineStatus, string> = {
  PENDING: '待回复',
  IN_PROGRESS: '回复中',
  COMPLETED: '已完成',
};

const STATUS_COLOR: Record<OnlineStatus, string> = {
  PENDING: 'gold',
  IN_PROGRESS: 'blue',
  COMPLETED: 'green',
};

/** 医生回复消息最大长度（与后端 OnlineConsultationConstant.MAX_REPLY_LENGTH=2000 一致） */
const CONSULT_MESSAGE_MAX = 2000;

/** 在线问诊工作台页面。 */
export default function OnlineConsultationPage() {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<OnlineStatus>('PENDING');
  const [selectedId, setSelectedId] = useState<number>();
  const [replyContent, setReplyContent] = useState('');
  const [starting, setStarting] = useState(false);
  const [replying, setReplying] = useState(false);
  const [ending, setEnding] = useState(false);
  const [submittingPrescription, setSubmittingPrescription] = useState(false);
  const [drugKeyword, setDrugKeyword] = useState('');
  const [form] = Form.useForm<PrescriptionFormValues>();

  // 保持最新选中问诊 ID 的 ref：WebSocket 回调读取它，选中切换时避免 socket 反复 teardown/reconnect
  const selectedIdRef = useRef(selectedId);
  selectedIdRef.current = selectedId;

  const listQuery = useQuery({
    queryKey: QUERY_KEYS.onlineConsultations(status),
    queryFn: () => getOnlineConsultations({ status, page: 1, size: 50 }),
    refetchInterval: POLL_INTERVAL_CONSULT,
  });
  const detailQuery = useQuery({
    queryKey: QUERY_KEYS.onlineConsultationDetail(selectedId ?? -1),
    queryFn: () => getOnlineConsultationDetail(selectedId as number),
    enabled: Boolean(selectedId),
  });
  const detail = detailQuery.data;

  useEffect(() => {
    const socket = createConsultationSocket((event) => {
      if (event.consultationId === selectedIdRef.current) {
        void queryClient.invalidateQueries({ queryKey: QUERY_KEYS.onlineConsultationDetail(event.consultationId) });
      }
      void queryClient.invalidateQueries({ queryKey: QUERY_KEYS.onlineConsultationsBase });
    });
    return () => { void socket?.deactivate(); };
  }, [queryClient]);
  const templatesQuery = useQuery({
    queryKey: ['prescription', 'templates', 'online-consultation'],
    queryFn: () => getTemplates({ page: 1, size: 100 }),
  });
  const drugsQuery = useQuery({
    queryKey: ['drug', 'online-consultation-options', drugKeyword],
    queryFn: () => getDoctorDrugs({
      name: drugKeyword.trim() || undefined,
      status: 'ENABLED',
      page: 1,
      size: 100,
    }),
    enabled: detail?.status === 'IN_PROGRESS',
    staleTime: STALE_TIME.onlineConsultDrugs,
  });

  // 空数组兜底用 useMemo 固定引用，否则每次渲染生成新数组会让下方 useMemo 依赖失效
  const templates = useMemo(
    () => templatesQuery.data?.list ?? [],
    [templatesQuery.data],
  );
  // 固定引用：避免每次 render 重建，破坏下方 Select 内部 memoization
  const drugOptions = useMemo(
    () =>
      (drugsQuery.data?.list ?? []).map((drug) => ({
        value: drug.id,
        label: `${drug.name}${drug.specification ? `（${drug.specification}）` : ''}`,
      })),
    [drugsQuery.data],
  );
  const patient = detail?.patientDetail.patient;
  const aiSummary = detail?.patientDetail.aiSummary;
  const allergyRows = readSummaryArray<AllergySummaryItem>(aiSummary, 'allergies');
  const medicalHistoryRows = readSummaryArray<MedicalHistorySummaryItem>(aiSummary, 'medicalHistories');
  const selectedTemplateOptions = useMemo(
    () => templates.map((item) => ({ value: item.id, label: item.name })),
    [templates],
  );

  /** 刷新当前详情和三个状态列表。 */
  async function refreshAll() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.onlineConsultationsBase }),
      selectedId
        ? queryClient.invalidateQueries({ queryKey: QUERY_KEYS.onlineConsultationDetail(selectedId) })
        : Promise.resolve(),
    ]);
  }

  /** 选择问诊并清理上一个患者的本地编辑数据。 */
  function selectConsultation(item: API.OnlineConsultationItem) {
    setSelectedId(item.consultId);
    setReplyContent('');
    form.resetFields();
  }

  /** 将待回复问诊切换到回复中。 */
  async function startReply() {
    if (!selectedId || starting) return;
    setStarting(true);
    try {
      await startOnlineConsultation(selectedId);
      message.success('已进入回复状态');
      await refreshAll();
    } catch (error: unknown) {
      message.error(getErrorMessage(error, '开始回复失败'));
    } finally {
      setStarting(false);
    }
  }

  /** 使用处方模板覆盖当前处方编辑项。 */
  function applyTemplate(templateId: number) {
    const template = templates.find((item) => item.id === templateId);
    if (!template) return;
    form.setFieldsValue({
      items: template.items.map((item) => ({
        drugId: item.drugId,
        dosage: item.dosage,
        frequency: item.frequency || '',
        usageMethod: item.usageMethod,
        days: item.days,
        quantity: item.quantity,
      })),
    });
  }

  /** 提交当前在线问诊处方。 */
  async function submitCurrentPrescription() {
    if (!selectedId || submittingPrescription) return;
    try {
      const values = await form.validateFields();
      setSubmittingPrescription(true);
      const result = await submitPrescription({ consultId: selectedId, items: values.items });
      if (result.auditRequired) {
        message.warning('处方已提交审核，审核通过后患者可见');
      } else {
        message.success('处方已开具，患者端已可查询');
      }
      form.resetFields();
      await refreshAll();
    } catch (error: unknown) {
      if (error && typeof error === 'object' && 'errorFields' in error) return;
      Modal.error({ title: '处方提交失败', content: getErrorMessage(error, '处方提交失败') });
    } finally {
      setSubmittingPrescription(false);
    }
  }

  /** 发送医生文字消息，最终状态仍由独立结束操作控制。 */
  async function sendMessage() {
    if (!selectedId || !replyContent.trim() || replying) return;
    setReplying(true);
    try {
      await sendOnlineConsultationMessage(selectedId, replyContent.trim(), crypto.randomUUID());
      setReplyContent('');
      await refreshAll();
    } catch (error: unknown) {
      message.error(getErrorMessage(error, '发送消息失败'));
    } finally {
      setReplying(false);
    }
  }

  /** 医生主动结束实时在线问诊。 */
  function finishConsultation() {
    if (!selectedId || ending) return;
    Modal.confirm({
      title: '结束在线问诊',
      content: '结束后双方均不能继续发送消息，已开处方不受影响。',
      okText: '确认结束',
      onOk: async () => {
        setEnding(true);
        try {
          await endOnlineConsultation(selectedId);
          message.success('在线问诊已结束');
          setStatus('COMPLETED');
          await refreshAll();
        } catch (error: unknown) {
          message.error(getErrorMessage(error, '结束问诊失败'));
        } finally {
          setEnding(false);
        }
      },
    });
  }

  return (
    <div className={styles.workspace}>
      <aside className={styles.listPanel}>
        <div className={styles.panelHeader}>
          <Title level={4}>在线问诊</Title>
          <Text type="secondary">异步回复，不依赖挂号和号源</Text>
        </div>
        <Tabs
          activeKey={status}
          onChange={(key) => {
            setStatus(key as OnlineStatus);
            setSelectedId(undefined);
            setReplyContent('');
          }}
          items={(Object.keys(STATUS_TEXT) as OnlineStatus[]).map((key) => ({
            key,
            label: STATUS_TEXT[key],
          }))}
        />
        <Spin spinning={listQuery.isLoading}>
          <List
            className={styles.consultList}
            dataSource={listQuery.data?.list ?? []}
            locale={{ emptyText: '暂无在线问诊' }}
            renderItem={(item) => (
              <List.Item
                className={`${styles.consultItem} ${selectedId === item.consultId ? styles.selected : ''}`}
                onClick={() => selectConsultation(item)}
              >
                <div className={styles.consultItemTop}>
                  <Space>
                    <UserOutlined />
                    <Text strong>{item.patientName || `患者 #${item.patientId}`}</Text>
                  </Space>
                  <Tag color={STATUS_COLOR[item.status]}>{STATUS_TEXT[item.status]}</Tag>
                </div>
                <Text className={styles.complaint} ellipsis>
                  {item.chiefComplaint || item.aiSummary?.chiefComplaint || '未填写主诉'}
                </Text>
                <Text type="secondary" className={styles.timeText}>
                  {item.submittedAt ? dayjs(item.submittedAt).format('MM-DD HH:mm') : '-'}
                </Text>
              </List.Item>
            )}
          />
        </Spin>
      </aside>

      <main className={styles.detailPanel}>
        {!selectedId ? (
          <Empty description="请从左侧选择在线问诊" />
        ) : detailQuery.isLoading ? (
          <Spin size="large" />
        ) : !detail ? (
          <Empty description="在线问诊详情加载失败" />
        ) : (
          <div className={styles.detailContent}>
            <header className={styles.detailHeader}>
              <div>
                <Title level={4}>{patient?.name || `患者 #${detail.patientDetail.patient.id}`}</Title>
                <Text type="secondary">问诊编号 #{detail.consultId}</Text>
              </div>
              <Tag color={STATUS_COLOR[detail.status]}>{STATUS_TEXT[detail.status]}</Tag>
            </header>

            <section className={styles.section}>
              <Title level={5}>患者信息</Title>
              <Descriptions size="small" column={3}>
                <Descriptions.Item label="性别">{formatGender(patient?.gender)}</Descriptions.Item>
                <Descriptions.Item label="出生日期">{patient?.dateOfBirth || '-'}</Descriptions.Item>
                <Descriptions.Item label="电话">{patient?.phone || '-'}</Descriptions.Item>
              </Descriptions>
            </section>

            <section className={styles.section}>
              <Title level={5}>AI 预问诊摘要</Title>
              <Descriptions size="small" column={1}>
                <Descriptions.Item label="现病史">
                  {detail.historyOfPresentIllness || '-'}
                </Descriptions.Item>
              </Descriptions>
              <div className={styles.summaryTables}>
                <Title level={5}>主诉</Title>
                <div className={styles.complaintBox}>
                  {detail.chiefComplaint || '-'}
                </div>
                <Title level={5}>过敏史</Title>
                <Table<AllergySummaryItem>
                  size="small"
                  bordered
                  pagination={false}
                  rowKey={(row, index) => `${row.allergen ?? 'allergy'}-${index}`}
                  locale={{ emptyText: '暂无过敏史' }}
                  dataSource={allergyRows}
                  columns={[
                    { title: '名称', dataIndex: 'allergen', key: 'allergen', render: (value) => value || '-' },
                    { title: '过敏反应', dataIndex: 'reaction', key: 'reaction', render: (value) => value || '-' },
                  ]}
                />
                <Title level={5} className={styles.historyTitle}>既往史</Title>
                <Table<MedicalHistorySummaryItem>
                  size="small"
                  bordered
                  pagination={false}
                  rowKey={(row, index) => `${row.name ?? row.content ?? 'history'}-${index}`}
                  locale={{ emptyText: '暂无既往史' }}
                  dataSource={medicalHistoryRows}
                  columns={[
                    {
                      title: '名称',
                      key: 'name',
                      render: (_, row) => row.name || row.content || '-',
                    },
                    {
                      title: '时间',
                      key: 'occurredAt',
                      render: (_, row) => formatHistoryDate(row.occurredAt || row.date),
                    },
                  ]}
                />
              </div>
            </section>

            {detail.status === 'PENDING' && (
              <section className={styles.actionSection}>
                <Button type="primary" icon={<MessageOutlined />} loading={starting} onClick={startReply}>
                  回复
                </Button>
              </section>
            )}

            {detail.status === 'IN_PROGRESS' && (
              <>
                <section className={styles.section}>
                  <div className={styles.sectionHeading}>
                    <Title level={5}><MedicineBoxOutlined /> 开具处方</Title>
                    <Select
                      allowClear
                      placeholder="应用处方模板"
                      options={selectedTemplateOptions}
                      onChange={applyTemplate}
                      style={{ width: 220 }}
                    />
                  </div>
                  <Form form={form} layout="vertical" initialValues={{ items: [{}] }}>
                    <Form.List name="items">
                      {(fields, { add, remove }) => (
                        <>
                          {fields.map((field) => (
                            <div className={styles.prescriptionRow} key={field.key}>
                              <Form.Item name={[field.name, 'drugId']} label="药品名称" rules={[{ required: true, message: '请选择药品' }]}> 
                                <Select
                                  showSearch
                                  allowClear
                                  placeholder="输入药品名称搜索"
                                  options={drugOptions}
                                  loading={drugsQuery.isFetching}
                                  filterOption={false}
                                  onSearch={setDrugKeyword}
                                  onClear={() => setDrugKeyword('')}
                                  notFoundContent={drugKeyword ? '未找到匹配药品' : '暂无可用药品'}
                                />
                              </Form.Item>
                              <Form.Item name={[field.name, 'dosage']} label="单次用量" rules={[{ required: true }]}> 
                                <Input placeholder="如 1 片" />
                              </Form.Item>
                              <Form.Item name={[field.name, 'frequency']} label="频次" rules={[{ required: true }]}> 
                                <Input placeholder="如 每日3次" />
                              </Form.Item>
                              <Form.Item name={[field.name, 'usageMethod']} label="用法" rules={[{ required: true }]}> 
                                <Input placeholder="如 口服" />
                              </Form.Item>
                              <Form.Item name={[field.name, 'days']} label="天数" rules={[{ required: true }]}> 
                                <InputNumber min={1} />
                              </Form.Item>
                              <Form.Item name={[field.name, 'quantity']} label="数量" rules={[{ required: true }]}> 
                                <InputNumber min={1} />
                              </Form.Item>
                              {fields.length > 1 && <Button danger type="link" onClick={() => remove(field.name)}>删除</Button>}
                            </div>
                          ))}
                          <Space>
                            <Button icon={<PlusOutlined />} onClick={() => add()}>添加药品</Button>
                            <Button
                              type="primary"
                              loading={submittingPrescription}
                              onClick={() => void submitCurrentPrescription()}
                            >
                              提交处方
                            </Button>
                          </Space>
                        </>
                      )}
                    </Form.List>
                  </Form>
                </section>

                <Divider />
                <section className={styles.section}>
                  <Title level={5}>问诊消息</Title>
                  <List
                    size="small"
                    dataSource={detail.messages}
                    locale={{ emptyText: '暂无消息' }}
                    renderItem={(item) => <List.Item><Text strong>{item.senderType === 'DOCTOR' ? '医生' : '患者'}：</Text>{item.content}<Text type="secondary">{item.createdAt ? dayjs(item.createdAt).format('MM-DD HH:mm') : ''}</Text></List.Item>}
                  />
                  <TextArea
                    rows={5}
                    maxLength={CONSULT_MESSAGE_MAX}
                    showCount
                    value={replyContent}
                    placeholder="输入发送给患者的文字消息"
                    onChange={(event) => setReplyContent(event.target.value)}
                  />
                  <Space className={styles.finishButton}>
                    <Button type="primary" icon={<SendOutlined />} loading={replying} disabled={!replyContent.trim()} onClick={() => void sendMessage()}>发送消息</Button>
                    <Button danger loading={ending} onClick={finishConsultation}>结束问诊</Button>
                  </Space>
                </section>
              </>
            )}

            {detail.prescriptions.length > 0 && (
              <section className={styles.section}>
                <Title level={5}>已开处方</Title>
                <Space wrap>
                  {detail.prescriptions.map((item) => (
                    <Tag key={item.id} color={item.status === 'APPROVED' ? 'green' : 'gold'}>
                      #{item.id} · {item.status} · {item.itemCount} 项
                    </Tag>
                  ))}
                </Space>
              </section>
            )}

            {detail.status === 'COMPLETED' && (
              <Alert
                type="success"
                showIcon
                message="本次在线问诊已完成"
                description={detail.messages.find((item) => item.senderType === 'DOCTOR')?.content || '医生已回复'}
              />
            )}
          </div>
        )}
      </main>
    </div>
  );
}

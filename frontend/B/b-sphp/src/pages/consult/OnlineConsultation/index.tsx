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
  List,
  Modal,
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
import { QUERY_KEYS } from '@/constants/queryKeys';
import { POLL_INTERVAL_CONSULT } from '@/constants/timing';
import { getErrorMessage } from '@/utils/error';
import { createIdempotencyKey } from '@/utils/idempotency';
import PrescriptionItemsForm, {
  toPrescriptionItemsPayload,
  type PrescriptionItemFormValue,
} from '@/components/prescription/PrescriptionItemsForm';
import styles from './less/index.module.less';
import { PAGE_SIZE_100, PAGE_SIZE_50 } from '@/constants/pageSize';
import { GENDER_FEMALE, GENDER_MALE, SENDER_DOCTOR, STATUS_APPROVED, STATUS_COMPLETED, STATUS_IN_PROGRESS, STATUS_PENDING } from '@/constants/businessStatus';

const { Text, Title } = Typography;
const { TextArea } = Input;

type OnlineStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';

interface PrescriptionFormValues {
  items: PrescriptionItemFormValue[];
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
  if (gender === GENDER_MALE || gender === '男') return '男';
  if (gender === GENDER_FEMALE || gender === '女') return '女';
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
  const [status, setStatus] = useState<OnlineStatus>(STATUS_PENDING);
  const [selectedId, setSelectedId] = useState<number>();
  const [replyContent, setReplyContent] = useState('');
  const [starting, setStarting] = useState(false);
  const [replying, setReplying] = useState(false);
  const [ending, setEnding] = useState(false);
  const [submittingPrescription, setSubmittingPrescription] = useState(false);
  const [form] = Form.useForm<PrescriptionFormValues>();

  // 保持最新选中问诊 ID 的 ref：WebSocket 回调读取它，选中切换时避免 socket 反复 teardown/reconnect
  const selectedIdRef = useRef(selectedId);
  selectedIdRef.current = selectedId;

  const listQuery = useQuery({
    queryKey: QUERY_KEYS.onlineConsultations(status),
    queryFn: () => getOnlineConsultations({ status, page: 1, size: PAGE_SIZE_50 }),
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
    queryFn: () => getTemplates({ page: 1, size: PAGE_SIZE_100 }),
  });
  // 空数组兜底用 useMemo 固定引用，否则每次渲染生成新数组会让下方 useMemo 依赖失效
  const templates = useMemo(
    () => templatesQuery.data?.list ?? [],
    [templatesQuery.data],
  );
  const patient = detail?.patientDetail.patient;
  const aiSummary = detail?.patientDetail.aiSummary;
  const allergyRows = readSummaryArray<AllergySummaryItem>(aiSummary, 'allergies');
  const medicalHistoryRows = readSummaryArray<MedicalHistorySummaryItem>(aiSummary, 'medicalHistories');

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
      await message.success('已进入回复状态');
      await refreshAll();
    } catch (error: unknown) {
      await message.error(getErrorMessage(error, '开始回复失败'));
    } finally {
      setStarting(false);
    }
  }

  /** 提交当前在线问诊处方。 */
  async function submitCurrentPrescription() {
    if (!selectedId || submittingPrescription) return;
    try {
      const values = await form.validateFields();
      setSubmittingPrescription(true);
      const result = await submitPrescription({
        consultId: selectedId,
        items: toPrescriptionItemsPayload(values.items ?? []),
      });
      if (result.auditRequired) {
        await message.warning('处方已提交审核，审核通过后患者可见');
      } else {
        await message.success('处方已开具，患者端已可查询');
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
      await sendOnlineConsultationMessage(selectedId, replyContent.trim(), createIdempotencyKey());
      setReplyContent('');
      await refreshAll();
    } catch (error: unknown) {
      await message.error(getErrorMessage(error, '发送消息失败'));
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
      cancelText: '取消',
      onOk: async () => {
        setEnding(true);
        try {
          await endOnlineConsultation(selectedId);
          await message.success('在线问诊已结束');
          setStatus(STATUS_COMPLETED);
          await refreshAll();
        } catch (error: unknown) {
          await message.error(getErrorMessage(error, '结束问诊失败'));
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

            {detail.status === STATUS_PENDING && (
              <section className={styles.actionSection}>
                <Button type="primary" icon={<MessageOutlined />} loading={starting} onClick={startReply}>
                  回复
                </Button>
              </section>
            )}

            {detail.status === STATUS_IN_PROGRESS && (
              <>
                <section className={styles.section}>
                  <div className={styles.sectionHeading}>
                    <Title level={5}><MedicineBoxOutlined /> 开具处方</Title>
                  </div>
                  <PrescriptionItemsForm
                    form={form}
                    fetchDrugs={getDoctorDrugs}
                    templates={templates}
                    templateLoading={templatesQuery.isLoading}
                    initialValues={{ items: [{}] }}
                  />
                  <Button
                    type="primary"
                    className={styles.prescriptionSubmit}
                    loading={submittingPrescription}
                    onClick={() => void submitCurrentPrescription()}
                  >
                    提交处方
                  </Button>
                </section>

                <Divider />
                <section className={styles.section}>
                  <Title level={5}>问诊消息</Title>
                  <div className={styles.chatViewport}>
                    {detail.messages.length === 0 ? (
                      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无消息" />
                    ) : detail.messages.map((item) => {
                      const isDoctor = item.senderType === SENDER_DOCTOR;
                      return <article className={`${styles.chatMessage} ${isDoctor ? styles.doctorMessage : styles.patientMessage}`} key={item.messageId}>
                        <div className={styles.messageBubble}>
                          <small>{isDoctor ? '医生' : '患者'} · {item.createdAt ? dayjs(item.createdAt).format('YYYY/MM/DD HH:mm') : '-'}</small>
                          <p>{item.content}</p>
                        </div>
                      </article>;
                    })}
                  </div>
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
                    <Tag key={item.id} color={item.status === STATUS_APPROVED ? 'green' : 'gold'}>
                      #{item.id} · {item.status} · {item.itemCount} 项
                    </Tag>
                  ))}
                </Space>
              </section>
            )}

            {detail.status === STATUS_COMPLETED && (
              <Alert
                type="success"
                showIcon
                message="本次在线问诊已完成"
                description={detail.messages.find((item) => item.senderType === SENDER_DOCTOR)?.content || '医生已回复'}
              />
            )}
          </div>
        )}
      </main>
    </div>
  );
}

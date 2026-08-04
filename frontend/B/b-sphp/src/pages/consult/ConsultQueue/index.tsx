/**
 * 接诊台 - 三栏布局主页面
 *
 * 左栏（35%）：待接诊队列（PENDING / IN_PROGRESS 切换）
 * 中栏（35%）：患者详情（基本信息、过敏史、既往史、AI 摘要、历史就诊、近期处方）
 * 右栏（30%）：接诊操作区（开始/结束接诊、病历编辑、留言板）
 */
import {
  Button,
  Card,
  Tag,
  Tabs,
  Input,
  List,
  Descriptions,
  Divider,
  Typography,
  Space,
  Spin,
  Empty,
  message,
  Modal,
  Badge,
} from 'antd';
import {
  UserOutlined,
  PhoneOutlined,
  MedicineBoxOutlined,
  HistoryOutlined,
  AlertOutlined,
  FileTextOutlined,
  SendOutlined,
  PlayCircleOutlined,
  StopOutlined,
  SaveOutlined,
  ClockCircleOutlined,
  OrderedListOutlined,
  ManOutlined,
  WomanOutlined,
} from '@ant-design/icons';
import { useModel } from '@umijs/max';
import { useEffect, useRef, useState, useCallback } from 'react';
import {
  getQueue,
  getPatientDetail,
  startConsult,
  endConsult,
  saveNote,
  getMessages,
  sendMessage,
  getConsultHistory,
  getConsultHistoryDetail,
} from '@/services/admin';
import dayjs from 'dayjs';
import styles from './index.module.less';

const { Text, Title } = Typography;
const { TextArea } = Input;

/** 性别映射 */
const GENDER_MAP: Record<string, { icon: React.ReactNode; color: string }> = {
  MALE: { icon: <ManOutlined />, color: '#1890ff' },
  FEMALE: { icon: <WomanOutlined />, color: '#eb2f96' },
  UNKNOWN: { icon: <UserOutlined />, color: '#999' },
};

/** 问诊状态配置 */
const STATUS_MAP: Record<string, { text: string; color: string }> = {
  PENDING: { text: '待接诊', color: 'processing' },
  IN_PROGRESS: { text: '接诊中', color: 'success' },
  COMPLETED: { text: '已完成', color: 'default' },
};

/** 消息发送方标签 */
const SENDER_LABEL: Record<string, string> = {
  PATIENT: '患者',
  DOCTOR: '医生',
  SYSTEM: '系统',
};

export default function ConsultQueue() {
  const { initialState } = useModel('@@initialState');
  const currentUser = initialState?.currentUser;
  const isAdmin = currentUser?.roles?.includes('ADMIN') ?? false;

  // 队列状态
  const [queueTab, setQueueTab] = useState<string>('PENDING');
  const [queueItems, setQueueItems] = useState<API.QueueItem[]>([]);
  const [queueLoading, setQueueLoading] = useState(false);
  const [queueTotal, setQueueTotal] = useState(0);
  const [queuePage, setQueuePage] = useState(1);

  // 选中状态
  const [selectedConsultId, setSelectedConsultId] = useState<number | null>(null);
  const [selectedStatus, setSelectedStatus] = useState<string | null>(null);

  // 患者详情
  const [patientDetail, setPatientDetail] = useState<API.PatientDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  // 接诊操作
  const [startingConsult, setStartingConsult] = useState(false);
  const [endingConsult, setEndingConsult] = useState(false);

  // 病历
  const [doctorNote, setDoctorNote] = useState('');
  const [savingNote, setSavingNote] = useState(false);
  const [noteChanged, setNoteChanged] = useState(false);

  // 留言板
  const [messages, setMessages] = useState<API.MessageVO[]>([]);
  const [messagesLoading, setMessagesLoading] = useState(false);
  const [messageInput, setMessageInput] = useState('');
  const [sendingMessage, setSendingMessage] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // 接诊历史
  const [historyItems, setHistoryItems] = useState<API.ConsultHistoryItem[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyTotal, setHistoryTotal] = useState(0);
  const [historyPage, setHistoryPage] = useState(1);
  const [historyDetail, setHistoryDetail] = useState<API.ConsultHistoryDetail | null>(null);
  const [historyDetailLoading, setHistoryDetailLoading] = useState(false);

  // ==================== 队列加载 ====================

  const loadQueue = useCallback(
    async (page = 1) => {
      setQueueLoading(true);
      try {
        const res = await getQueue({ status: queueTab, page, size: 20 });
        setQueueItems(res.list ?? []);
        setQueueTotal(res.total);
        setQueuePage(page);
      } catch (err: any) {
        message.error(err?.message || '加载队列失败');
      } finally {
        setQueueLoading(false);
      }
    },
    [queueTab],
  );

  /** 首次加载 & 切换 Tab 时重新加载（接诊历史 Tab 不刷新待接诊队列） */
  useEffect(() => {
    if (queueTab !== 'HISTORY') {
      loadQueue(1);
    }
    setSelectedConsultId(null);
    setPatientDetail(null);
    setSelectedStatus(null);
  }, [queueTab, loadQueue]);

  /** 轮询：每 15 秒刷新队列（仅待接诊/接诊中） */
  useEffect(() => {
    if (queueTab === 'HISTORY') {
      return;
    }
    const timer = setInterval(() => loadQueue(queuePage), 15000);
    return () => clearInterval(timer);
  }, [queueTab, queuePage, loadQueue]);

  // ==================== 患者详情 ====================

  const handleSelectItem = async (item: API.QueueItem) => {
    setSelectedConsultId(item.consultId);
    setSelectedStatus(item.status);
    setDetailLoading(true);
    setPatientDetail(null);
    setHistoryDetail(null);
    setDoctorNote('');
    setMessages([]);
    setNoteChanged(false);
    try {
      const detail = await getPatientDetail(item.consultId);
      setPatientDetail(detail);
    } catch (err: any) {
      message.error(err?.message || '加载患者详情失败');
    } finally {
      setDetailLoading(false);
    }
  };

  // ==================== 开始/结束接诊 ====================

  const handleStartConsult = async () => {
    if (!selectedConsultId) return;
    setStartingConsult(true);
    try {
      await startConsult(selectedConsultId);
      message.success('开始接诊');
      setSelectedStatus('IN_PROGRESS');
      setQueueTab('IN_PROGRESS');
      await loadQueue(1);
      loadMessages();
    } catch (err: any) {
      message.error(err?.message || '开始接诊失败');
    } finally {
      setStartingConsult(false);
    }
  };

  const handleEndConsult = () => {
    if (!selectedConsultId) return;
    Modal.confirm({
      title: '结束问诊',
      content:
        '确定结束当前问诊吗？结束前请确认已保存病历并无未签名的处方草稿。',
      okText: '确认结束',
      okButtonProps: { danger: true },
      onOk: async () => {
        setEndingConsult(true);
        try {
          await endConsult(selectedConsultId);
          message.success('问诊已结束');
          setSelectedStatus('COMPLETED');
          setSelectedConsultId(null);
          setPatientDetail(null);
          setDoctorNote('');
          setMessages([]);
          await loadQueue(1);
        } catch (err: any) {
          message.error(err?.message || '结束问诊失败');
        } finally {
          setEndingConsult(false);
        }
      },
    });
  };

  // ==================== 病历保存 ====================

  const handleSaveNote = async () => {
    if (!selectedConsultId) return;
    if (!doctorNote.trim()) {
      message.warning('请输入病历内容');
      return;
    }
    setSavingNote(true);
    try {
      await saveNote(selectedConsultId, { doctorNote });
      message.success('病历已保存');
      setNoteChanged(false);
    } catch (err: any) {
      message.error(err?.message || '保存病历失败');
    } finally {
      setSavingNote(false);
    }
  };

  // ==================== 留言板 ====================

  const loadMessages = useCallback(async () => {
    if (!selectedConsultId) return;
    setMessagesLoading(true);
    try {
      const res = await getMessages(selectedConsultId, { page: 1, size: 100 });
      setMessages(res.list ?? []);
    } catch {
      // 静默失败
    } finally {
      setMessagesLoading(false);
    }
  }, [selectedConsultId]);

  useEffect(() => {
    if (selectedStatus === 'IN_PROGRESS' && selectedConsultId) {
      loadMessages();
    }
  }, [selectedStatus, selectedConsultId, loadMessages]);

  /** 消息列表滚动到底部 */
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async () => {
    if (!selectedConsultId || !messageInput.trim()) return;
    setSendingMessage(true);
    try {
      const msg = await sendMessage(selectedConsultId, {
        content: messageInput.trim(),
      });
      setMessages((prev) => [...prev, msg]);
      setMessageInput('');
    } catch (err: any) {
      message.error(err?.message || '发送消息失败');
    } finally {
      setSendingMessage(false);
    }
  };

  const handleMessageKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  // ==================== 接诊历史 ====================

  const loadHistory = useCallback(async (page = 1) => {
    setHistoryLoading(true);
    try {
      const res = await getConsultHistory({ page, size: 10 });
      setHistoryItems(res.list ?? []);
      setHistoryTotal(res.total);
      setHistoryPage(page);
    } catch {
      // 静默失败
    } finally {
      setHistoryLoading(false);
    }
  }, []);

  /** 切换至接诊历史 Tab 时加载 */
  useEffect(() => {
    if (queueTab === 'HISTORY') {
      loadHistory(1);
    }
  }, [queueTab, loadHistory]);

  /** 选择历史接诊记录 */
  const handleSelectHistoryItem = async (item: API.ConsultHistoryItem) => {
    setSelectedConsultId(item.consultId);
    setSelectedStatus(item.status);
    setDetailLoading(true);
    setHistoryDetailLoading(true);
    setPatientDetail(null);
    setHistoryDetail(null);
    setDoctorNote('');
    setMessages([]);
    setNoteChanged(false);
    try {
      const [detail, hDetail] = await Promise.all([
        getPatientDetail(item.consultId),
        getConsultHistoryDetail(item.consultId),
      ]);
      setPatientDetail(detail);
      setHistoryDetail(hDetail);
    } catch (err: any) {
      message.error(err?.message || '加载详情失败');
    } finally {
      setDetailLoading(false);
      setHistoryDetailLoading(false);
    }
  };

  // ==================== 工具函数 ====================

  const calcAge = (dateOfBirth?: string): number | null => {
    if (!dateOfBirth) return null;
    return dayjs().diff(dayjs(dateOfBirth), 'year');
  };

  // ==================== 渲染：左栏 - 队列列表 ====================

  const renderQueuePanel = () => (
    <div className={styles.panel}>
      <div className={styles.panelHeader}>
        <Title level={5} style={{ margin: 0 }}>
          <OrderedListOutlined /> 接诊队列
        </Title>
        {queueTab !== 'HISTORY' && <Badge count={queueTotal} showZero color="#1890ff" />}
      </div>
      <Tabs
        activeKey={queueTab}
        onChange={(key) => {
          setQueueTab(key);
          if (key !== 'HISTORY') {
            setSelectedConsultId(null);
            setPatientDetail(null);
            setSelectedStatus(null);
          }
        }}
        size="small"
        items={[
          { key: 'PENDING', label: '待接诊' },
          { key: 'IN_PROGRESS', label: '接诊中' },
          { key: 'HISTORY', label: '接诊历史' },
        ]}
      />
      <div className={styles.queueList}>
        {queueTab === 'HISTORY' ? (
          // 接诊历史列表
          <Spin spinning={historyLoading}>
            {historyItems.length === 0 ? (
              <Empty description="暂无接诊历史" />
            ) : (
              <List
                dataSource={historyItems}
                renderItem={(item) => {
                  const isSelected = item.consultId === selectedConsultId;
                  const gender = GENDER_MAP[item.patientGender] ?? GENDER_MAP.UNKNOWN;
                  const age = item.patientDateOfBirth
                    ? dayjs().diff(dayjs(item.patientDateOfBirth), 'year')
                    : null;
                  return (
                    <List.Item
                      className={`${styles.queueItem} ${isSelected ? styles.queueItemSelected : ''}`}
                      onClick={() => handleSelectHistoryItem(item)}
                    >
                      <div className={styles.queueItemHeader}>
                        <Space>
                          <span style={{ color: gender.color }}>{gender.icon}</span>
                          <Text strong>{item.patientName}</Text>
                          {age !== null && (
                            <Text type="secondary" style={{ fontSize: 12 }}>{age}岁</Text>
                          )}
                        </Space>
                        <Tag>{item.status === 'COMPLETED' ? '已完成' : item.status}</Tag>
                      </div>
                      {item.chiefComplaint && (
                        <div className={styles.aiSummary}>
                          <Text type="secondary" style={{ fontSize: 12 }} ellipsis>
                            主诉：{item.chiefComplaint}
                          </Text>
                        </div>
                      )}
                      <div style={{ marginTop: 2 }}>
                        <Text type="secondary" style={{ fontSize: 11 }}>
                          {item.endedAt
                            ? dayjs(item.endedAt).format('MM-DD HH:mm')
                            : dayjs(item.createdAt).format('MM-DD HH:mm')}
                          接诊
                          {item.noteSummary && ` · ${item.noteSummary}`}
                        </Text>
                      </div>
                    </List.Item>
                  );
                }}
              />
            )}
          </Spin>
        ) : (
          // 待接诊/接诊中列表
          <Spin spinning={queueLoading}>
            {queueItems.length === 0 ? (
              <Empty description="暂无队列数据" />
            ) : (
              <List
                dataSource={queueItems}
                renderItem={(item) => {
                  const isSelected = item.consultId === selectedConsultId;
                  const gender = GENDER_MAP[item.patientGender] ?? GENDER_MAP.UNKNOWN;
                  const statusCfg = STATUS_MAP[item.status] ?? { text: item.status, color: 'default' };
                  return (
                    <List.Item
                      className={`${styles.queueItem} ${isSelected ? styles.queueItemSelected : ''}`}
                      onClick={() => handleSelectItem(item)}
                    >
                      <div className={styles.queueItemHeader}>
                        <Space>
                          <span style={{ color: gender.color }}>{gender.icon}</span>
                          <Text strong>{item.patientName}</Text>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            {item.patientAge}岁
                          </Text>
                        </Space>
                        <Tag color={statusCfg.color}>{statusCfg.text}</Tag>
                      </div>
                      <div className={styles.queueItemMeta}>
                        <Space size={12}>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            <ClockCircleOutlined /> #{item.queueNumber}
                          </Text>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            {item.appointmentTime ? dayjs(item.appointmentTime).format('HH:mm') : '-'}
                          </Text>
                        </Space>
                      </div>
                      {item.aiSummary?.chiefComplaint && (
                        <div className={styles.aiSummary}>
                          <Text type="secondary" style={{ fontSize: 12 }} ellipsis>
                            主诉：{item.aiSummary.chiefComplaint}
                          </Text>
                        </div>
                      )}
                    </List.Item>
                  );
                }}
              />
            )}
          </Spin>
        )}
      </div>
    </div>
  );

  // ==================== 渲染：中栏 - 患者详情 ====================

  const renderPatientPanel = () => (
    <div className={styles.panel}>
      <div className={styles.panelHeader}>
        <Title level={5} style={{ margin: 0 }}>
          <UserOutlined /> 患者详情
        </Title>
      </div>
      <div className={styles.patientContent}>
        {!selectedConsultId ? (
          <Empty description="请从左侧队列选择患者" />
        ) : detailLoading ? (
          <div style={{ textAlign: 'center', padding: 40 }}>
            <Spin tip="加载中..." />
          </div>
        ) : patientDetail ? (
          <>
            {/* 基本信息 */}
            <Card size="small" className={styles.sectionCard} title="基本信息">
              <Descriptions size="small" column={2}>
                <Descriptions.Item label="姓名">{patientDetail.patient.name}</Descriptions.Item>
                <Descriptions.Item label="性别">
                  {patientDetail.patient.gender === 'MALE'
                    ? '男'
                    : patientDetail.patient.gender === 'FEMALE'
                      ? '女'
                      : '未知'}
                </Descriptions.Item>
                <Descriptions.Item label="年龄">
                  {calcAge(patientDetail.patient.dateOfBirth) ?? '-'} 岁
                </Descriptions.Item>
                <Descriptions.Item label="出生日期">
                  {patientDetail.patient.dateOfBirth ?? '-'}
                </Descriptions.Item>
                <Descriptions.Item label="电话">
                  <PhoneOutlined /> {patientDetail.patient.phone ?? '-'}
                </Descriptions.Item>
                <Descriptions.Item label="紧急联系人">
                  {patientDetail.patient.emergencyContact ?? '-'}
                </Descriptions.Item>
              </Descriptions>
            </Card>

            {/* AI 预问诊摘要 */}
            {patientDetail.aiSummary && Object.keys(patientDetail.aiSummary).length > 0 && (
              <Card size="small" className={styles.sectionCard} title="AI 预问诊摘要">
                <pre className={styles.aiSummaryContent}>
                  {JSON.stringify(patientDetail.aiSummary, null, 2)}
                </pre>
              </Card>
            )}

            {/* 过敏史 */}
            {patientDetail.allergies.length > 0 && (
              <Card
                size="small"
                className={styles.sectionCard}
                title={
                  <>
                    <AlertOutlined /> 过敏史
                  </>
                }
              >
                <div>
                  {patientDetail.allergies.map((a) => (
                    <Tag key={a.id} color="red">
                      {a.allergen}
                      {a.reaction ? `（${a.reaction}）` : ''}
                      {a.severity ? ` [${a.severity}]` : ''}
                    </Tag>
                  ))}
                </div>
              </Card>
            )}

            {/* 既往史 */}
            {patientDetail.medicalHistories.length > 0 && (
              <Card
                size="small"
                className={styles.sectionCard}
                title={
                  <>
                    <HistoryOutlined /> 既往史
                  </>
                }
              >
                <List
                  size="small"
                  dataSource={patientDetail.medicalHistories}
                  renderItem={(h) => (
                    <List.Item>
                      <div>
                        <Text>{h.content}</Text>
                        {h.occurredAt && (
                          <div>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              {h.occurredAt}
                            </Text>
                          </div>
                        )}
                      </div>
                    </List.Item>
                  )}
                />
              </Card>
            )}

            {/* 历史就诊记录 */}
            {patientDetail.historyRecords.length > 0 && (
              <Card
                size="small"
                className={styles.sectionCard}
                title={
                  <>
                    <FileTextOutlined /> 历史就诊记录
                  </>
                }
              >
                <List
                  size="small"
                  dataSource={patientDetail.historyRecords}
                  renderItem={(r) => (
                    <List.Item>
                      <div style={{ width: '100%' }}>
                        <Space>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            {r.date}
                          </Text>
                          <Tag>{r.type}</Tag>
                          <Tag color={STATUS_MAP[r.status]?.color}>
                            {STATUS_MAP[r.status]?.text ?? r.status}
                          </Tag>
                        </Space>
                        {r.summary && (
                          <div>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              {r.summary}
                            </Text>
                          </div>
                        )}
                      </div>
                    </List.Item>
                  )}
                />
              </Card>
            )}

            {/* 近期处方 */}
            {patientDetail.recentPrescriptions.length > 0 && (
              <Card
                size="small"
                className={styles.sectionCard}
                title={
                  <>
                    <MedicineBoxOutlined /> 近期处方
                  </>
                }
              >
                <List
                  size="small"
                  dataSource={patientDetail.recentPrescriptions}
                  renderItem={(p) => (
                    <List.Item>
                      <Space>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          处方 #{p.id}
                        </Text>
                        <Tag>{p.status}</Tag>
                        {p.issuedAt && (
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            {dayjs(p.issuedAt).format('YYYY-MM-DD HH:mm')}
                          </Text>
                        )}
                      </Space>
                    </List.Item>
                  )}
                />
              </Card>
            )}
          </>
        ) : (
          <Empty description="患者信息加载失败" />
        )}
      </div>
    </div>
  );

  // ==================== 渲染：右栏 - 接诊操作区 ====================

  const renderConsultPanel = () => (
    <div className={styles.panel}>
      <div className={styles.panelHeader}>
        <Title level={5} style={{ margin: 0 }}>
          <MedicineBoxOutlined /> {queueTab === 'HISTORY' ? '接诊详情' : '接诊操作'}
        </Title>
      </div>
      <div className={styles.consultContent}>
        {!selectedConsultId ? (
          <Empty description="请选择患者" />
        ) : queueTab === 'HISTORY' ? (
          // 历史接诊详情
          <Spin spinning={historyDetailLoading}>
            {historyDetail ? (
              <div style={{ padding: '4px 0' }}>
                <div className={styles.sectionTitle}>
                  <FileTextOutlined /> 病历记录
                </div>
                <div style={{ margin: '8px 0', fontSize: 13, whiteSpace: 'pre-wrap', lineHeight: 1.7 }}>
                  {historyDetail.doctorNote || '无病历记录'}
                </div>

                {historyDetail.prescriptions.length > 0 && (
                  <>
                    <Divider style={{ margin: '12px 0' }} />
                    <div className={styles.sectionTitle}>
                      <MedicineBoxOutlined /> 关联处方
                    </div>
                    <List
                      size="small"
                      dataSource={historyDetail.prescriptions}
                      renderItem={(p) => (
                        <List.Item>
                          <Space>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              处方 #{p.id}
                            </Text>
                            <Tag>{p.status === 'APPROVED' ? '已通过' : p.status}</Tag>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              {p.itemCount} 项
                            </Text>
                            {p.issuedAt && (
                              <Text type="secondary" style={{ fontSize: 12 }}>
                                {dayjs(p.issuedAt).format('MM-DD HH:mm')}
                              </Text>
                            )}
                          </Space>
                        </List.Item>
                      )}
                    />
                  </>
                )}

                <Divider style={{ margin: '12px 0' }} />
                <div style={{ fontSize: 12, color: '#999' }}>
                  {historyDetail.endedAt ? (
                    <>接诊时间：{dayjs(historyDetail.endedAt).format('YYYY-MM-DD HH:mm')}</>
                  ) : (
                    <>创建时间：{dayjs(historyDetail.createdAt).format('YYYY-MM-DD HH:mm')}</>
                  )}
                </div>
              </div>
            ) : (
              <Empty description="加载中..." />
            )}
          </Spin>
        ) : selectedStatus === 'PENDING' ? (
          <div className={styles.startConsultArea}>
            <Button
              type="primary"
              size="large"
              icon={<PlayCircleOutlined />}
              loading={startingConsult}
              onClick={handleStartConsult}
              block
            >
              开始接诊
            </Button>
            <Text type="secondary" style={{ display: 'block', textAlign: 'center', marginTop: 8 }}>
              点击后开始接诊，将进入接诊中状态
            </Text>
          </div>
        ) : selectedStatus === 'IN_PROGRESS' ? (
          <div className={styles.inProgressArea}>
            {/* 结束问诊按钮 */}
            <Button
              danger
              icon={<StopOutlined />}
              loading={endingConsult}
              onClick={handleEndConsult}
              block
              style={{ marginBottom: 12 }}
            >
              结束问诊
            </Button>

            {/* 病历编辑 */}
            <div className={styles.noteSection}>
              <div className={styles.sectionTitle}>
                <FileTextOutlined /> 病历记录
              </div>
              <TextArea
                rows={6}
                value={doctorNote}
                onChange={(e) => {
                  setDoctorNote(e.target.value);
                  setNoteChanged(true);
                }}
                placeholder={'请输入病历内容\n主诉：...\n现病史：...\n查体：...\n诊断：...\n治疗方案：...'}
                style={{ marginBottom: 8 }}
              />
              <Button
                type="primary"
                icon={<SaveOutlined />}
                loading={savingNote}
                onClick={handleSaveNote}
                disabled={!noteChanged}
                size="small"
              >
                保存病历
              </Button>
            </div>

            <Divider style={{ margin: '12px 0' }} />

            {/* 留言板 */}
            <div className={styles.messageSection}>
              <div className={styles.sectionTitle}>
                <HistoryOutlined /> 留言板
              </div>
              <div className={styles.messageList}>
                <Spin spinning={messagesLoading}>
                  {messages.length === 0 ? (
                    <Text type="secondary" style={{ display: 'block', textAlign: 'center', padding: 16 }}>
                      暂无消息
                    </Text>
                  ) : (
                    messages.map((msg) => (
                      <div
                        key={msg.messageId}
                        className={`${styles.messageItem} ${
                          msg.senderType === 'DOCTOR' ? styles.messageRight : styles.messageLeft
                        }`}
                      >
                        <div className={styles.messageBubble}>
                          <div className={styles.messageHeader}>
                            <Text strong style={{ fontSize: 12 }}>
                              {SENDER_LABEL[msg.senderType] ?? msg.senderType}
                            </Text>
                            <Text type="secondary" style={{ fontSize: 11 }}>
                              {dayjs(msg.createdAt).format('HH:mm')}
                            </Text>
                          </div>
                          <div className={styles.messageContent}>{msg.content}</div>
                        </div>
                      </div>
                    ))
                  )}
                  <div ref={messagesEndRef} />
                </Spin>
              </div>
              <div className={styles.messageInput}>
                <TextArea
                  rows={2}
                  value={messageInput}
                  onChange={(e) => setMessageInput(e.target.value)}
                  onKeyDown={handleMessageKeyDown}
                  placeholder="输入消息，Enter 发送，Shift+Enter 换行"
                  disabled={sendingMessage}
                />
                <Button
                  type="primary"
                  icon={<SendOutlined />}
                  loading={sendingMessage}
                  onClick={handleSendMessage}
                  disabled={!messageInput.trim()}
                  style={{ marginTop: 4 }}
                  block
                >
                  发送
                </Button>
              </div>
            </div>
          </div>
        ) : (
          <Empty description="问诊已结束" />
        )}
      </div>
    </div>
  );

  // ==================== 主渲染 ====================

  if (isAdmin) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 300 }}>
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={<Text type="secondary">管理员不参与接诊，无需使用接诊台</Text>}
        />
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <div className={styles.leftPanel}>{renderQueuePanel()}</div>
      <div className={styles.middlePanel}>{renderPatientPanel()}</div>
      <div className={styles.rightPanel}>{renderConsultPanel()}</div>
    </div>
  );
}
/**
 * B 端无挂号在线问诊工作台。
 *
 * 医生从待回复列表进入编辑状态，可先提交处方，最后发送唯一一条文字回复并完成问诊。
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
import { useMemo, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import {
  getOnlineConsultationDetail,
  getOnlineConsultations,
  getTemplates,
  replyOnlineConsultation,
  startOnlineConsultation,
  submitPrescription,
} from '@/services/admin';
import { QUERY_KEYS } from '@/constants/queryKeys';
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

/** 在线问诊工作台页面。 */
export default function OnlineConsultationPage() {
  const queryClient = useQueryClient();
  const [status, setStatus] = useState<OnlineStatus>('PENDING');
  const [selectedId, setSelectedId] = useState<number>();
  const [replyContent, setReplyContent] = useState('');
  const [starting, setStarting] = useState(false);
  const [replying, setReplying] = useState(false);
  const [submittingPrescription, setSubmittingPrescription] = useState(false);
  const [form] = Form.useForm<PrescriptionFormValues>();

  const listQuery = useQuery({
    queryKey: QUERY_KEYS.onlineConsultations(status),
    queryFn: () => getOnlineConsultations({ status, page: 1, size: 50 }),
    refetchInterval: 15_000,
  });
  const detailQuery = useQuery({
    queryKey: QUERY_KEYS.onlineConsultationDetail(selectedId ?? -1),
    queryFn: () => getOnlineConsultationDetail(selectedId as number),
    enabled: Boolean(selectedId),
  });
  const templatesQuery = useQuery({
    queryKey: ['prescription', 'templates', 'online-consultation'],
    queryFn: () => getTemplates({ page: 1, size: 100 }),
  });

  const detail = detailQuery.data;
  const templates = templatesQuery.data?.list ?? [];
  const patient = detail?.patientDetail.patient;
  const selectedTemplateOptions = useMemo(
    () => templates.map((item) => ({ value: item.id, label: item.name })),
    [templates],
  );

  /** 刷新当前详情和三个状态列表。 */
  async function refreshAll() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['consult', 'online'] }),
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
    } catch (error) {
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
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) return;
      Modal.error({ title: '处方提交失败', content: getErrorMessage(error, '处方提交失败') });
    } finally {
      setSubmittingPrescription(false);
    }
  }

  /** 发送唯一医生回复并结束在线问诊。 */
  function finishReply() {
    if (!selectedId || !replyContent.trim() || replying) return;
    Modal.confirm({
      title: '发送回复并完成问诊',
      content: '回复发送后本次在线问诊立即结束，不能再次回复或补开处方。',
      okText: '确认发送',
      onOk: async () => {
        setReplying(true);
        try {
          await replyOnlineConsultation(selectedId, replyContent.trim());
          message.success('回复已发送，在线问诊已完成');
          setReplyContent('');
          setStatus('COMPLETED');
          await refreshAll();
        } catch (error) {
          message.error(getErrorMessage(error, '发送回复失败'));
        } finally {
          setReplying(false);
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
                <Descriptions.Item label="性别">{patient?.gender || '-'}</Descriptions.Item>
                <Descriptions.Item label="出生日期">{patient?.dateOfBirth || '-'}</Descriptions.Item>
                <Descriptions.Item label="电话">{patient?.phone || '-'}</Descriptions.Item>
              </Descriptions>
            </section>

            <section className={styles.section}>
              <Title level={5}>AI 预问诊摘要</Title>
              <Descriptions size="small" column={1}>
                <Descriptions.Item label="主诉">{detail.chiefComplaint || '-'}</Descriptions.Item>
                <Descriptions.Item label="现病史">
                  {detail.historyOfPresentIllness || '-'}
                </Descriptions.Item>
              </Descriptions>
              {detail.patientDetail.aiSummary && (
                <pre className={styles.summaryJson}>
                  {JSON.stringify(detail.patientDetail.aiSummary, null, 2)}
                </pre>
              )}
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
                              <Form.Item name={[field.name, 'drugId']} label="药品 ID" rules={[{ required: true }]}> 
                                <InputNumber min={1} />
                              </Form.Item>
                              <Form.Item name={[field.name, 'dosage']} label="单次用量" rules={[{ required: true }]}> 
                                <Input placeholder="如 1 片" />
                              </Form.Item>
                              <Form.Item name={[field.name, 'frequency']} label="频次" rules={[{ required: true }]}> 
                                <Input placeholder="如 每日三次" />
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
                  <Title level={5}>医生回复</Title>
                  <TextArea
                    rows={5}
                    maxLength={2000}
                    showCount
                    value={replyContent}
                    placeholder="输入本次在线问诊的最终回复"
                    onChange={(event) => setReplyContent(event.target.value)}
                  />
                  <Button
                    type="primary"
                    icon={<SendOutlined />}
                    loading={replying}
                    disabled={!replyContent.trim()}
                    onClick={finishReply}
                    className={styles.finishButton}
                  >
                    发送回复并完成
                  </Button>
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

/**
 * 接诊台右栏顶部 - 患者信息条。
 *
 * 一行展示核心信息（姓名/性别/年龄/过敏预警），点击「详情」展开 Drawer
 * 查看完整信息（AI 预问诊摘要 / 过敏史 / 既往史 / 历史就诊 / 近期处方）。
 * 收起时只占一行高度，把版面让给接诊操作区。
 */
import {
  AlertOutlined,
  DownOutlined,
  FileTextOutlined,
  HistoryOutlined,
  HomeOutlined,
  MedicineBoxOutlined,
  PhoneOutlined,
  PlusOutlined,
  UserOutlined,
} from '@ant-design/icons';
import {
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  List,
  Modal,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
  message,
} from 'antd';
import { useState } from 'react';
import dayjs from 'dayjs';
import styles from './PatientInfoBar.module.less';
import { STATUS_MAP } from './constants';
import { getErrorMessage } from '@/utils/error';
import { GENDER_FEMALE, GENDER_MALE } from '@/constants/businessStatus';

const { Text } = Typography;

interface PatientInfoBarProps {
  selectedConsultId: number | null;
  detailLoading: boolean;
  patientDetail: API.PatientDetail | undefined;
  /** 是否允许补录过敏史（仅接诊中 IN_PROGRESS 为 true，历史只读） */
  canEditAllergy: boolean;
  /** 补录过敏史（父层负责刷新患者详情缓存） */
  onAddAllergy: (data: API.AllergyCreateReq) => Promise<void>;
}

/** 严重程度选项 */
const SEVERITY_OPTIONS = [
  { value: 'MILD', label: '轻度' },
  { value: 'MODERATE', label: '中度' },
  { value: 'SEVERE', label: '重度' },
];

/** 过敏史补录表单值（对齐 API.AllergyCreateReq） */
interface AllergyFormValue {
  allergen: string;
  reaction?: string;
  severity: 'MILD' | 'MODERATE' | 'SEVERE';
}

/** 根据出生日期计算年龄（岁） */
function calcAge(dateOfBirth?: string): number | null {
  if (!dateOfBirth) return null;
  return dayjs().diff(dayjs(dateOfBirth), 'year');
}

/** 性别文本 */
function genderText(gender: string): string {
  if (gender === GENDER_MALE) return '男';
  if (gender === GENDER_FEMALE) return '女';
  return '未知';
}

export default function PatientInfoBar({
  selectedConsultId,
  detailLoading,
  patientDetail,
  canEditAllergy,
  onAddAllergy,
}: PatientInfoBarProps) {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [allergyModalOpen, setAllergyModalOpen] = useState(false);
  const [allergySubmitting, setAllergySubmitting] = useState(false);
  const [allergyForm] = Form.useForm<AllergyFormValue>();

  /** 打开补录过敏弹窗（每次重置表单，避免残留上次输入） */
  const openAllergyModal = () => {
    allergyForm.resetFields();
    setAllergyModalOpen(true);
  };

  /** 提交补录过敏史：成功后关闭弹窗，父层刷新患者详情（过敏标签即时更新） */
  const handleAllergySubmit = async () => {
    try {
      const values = await allergyForm.validateFields();
      setAllergySubmitting(true);
      await onAddAllergy({
        allergen: values.allergen.trim(),
        reaction: values.reaction?.trim() || undefined,
        severity: values.severity,
      });
      message.success('过敏史已保存');
      setAllergyModalOpen(false);
    } catch (err: unknown) {
      const errMsg = getErrorMessage(err, '');
      if (errMsg) message.error(errMsg);
    } finally {
      setAllergySubmitting(false);
    }
  };

  if (!selectedConsultId) {
    return (
      <div className={styles.patientInfoBar}>
        <Text type="secondary">请从左侧队列选择患者</Text>
      </div>
    );
  }

  if (detailLoading) {
    return (
      <div className={styles.patientInfoBar}>
        <Spin size="small" /> <Text type="secondary">加载中...</Text>
      </div>
    );
  }

  if (!patientDetail) {
    return (
      <div className={styles.patientInfoBar}>
        <Text type="secondary">患者信息加载失败</Text>
      </div>
    );
  }

  const { patient, allergies } = patientDetail;
  const age = calcAge(patient.dateOfBirth);
  // 过敏预警：存在过敏史即高亮（红点 + 数量），无过敏史时显示"无过敏"灰标
  const hasAllergy = allergies.length > 0;

  return (
    <>
      <div className={styles.patientInfoBar}>
        <div className={styles.infoPrimary}>
          <UserOutlined className={styles.infoIcon} />
          <Text strong className={styles.nameKeep}>
            {patient.name}
          </Text>
          <Text type="secondary" className={styles.textSmall}>
            {genderText(patient.gender)} · {age !== null ? `${age}岁` : '年龄未知'}
          </Text>
          {hasAllergy ? (
            <Tag color="red" icon={<AlertOutlined />} className={styles.allergyTag}>
              过敏 {allergies.length}
            </Tag>
          ) : (
            <Tag className={styles.allergyTag}>无过敏</Tag>
          )}
          {/* 仅接诊中可补录：医生发现患者过敏可立即录入，下次开方即参与拦截；历史/待接诊只读 */}
          {canEditAllergy && (
            <Button
              type="link"
              size="small"
              icon={<PlusOutlined />}
              className={styles.linkBtn}
              onClick={openAllergyModal}
            >
              过敏
            </Button>
          )}
        </div>
        <div className={styles.infoSecondary}>
          {patient.phone && (
            <span className={styles.infoSecondaryItem}>
              <PhoneOutlined /> {patient.phone}
            </span>
          )}
          {patient.emergencyContact && (
            <span className={styles.infoSecondaryItem}>
              <HomeOutlined /> 紧急联系：{patient.emergencyContact}
            </span>
          )}
          <Button
            type="link"
            size="small"
            icon={<DownOutlined />}
            onClick={() => setDrawerOpen(true)}
          >
            详情
          </Button>
        </div>
      </div>

      <Drawer
        title={
          <Space>
            <UserOutlined />
            {patient.name} · 患者详情
          </Space>
        }
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={480}
      >
        <Spin spinning={detailLoading}>
          {/* 基本信息 */}
          <Card size="small" className={styles.sectionCard} title="基本信息">
            <Descriptions size="small" column={2}>
              <Descriptions.Item label="姓名">{patient.name}</Descriptions.Item>
              <Descriptions.Item label="性别">{genderText(patient.gender)}</Descriptions.Item>
              <Descriptions.Item label="年龄">{age ?? '-'} 岁</Descriptions.Item>
              <Descriptions.Item label="出生日期">
                {patient.dateOfBirth ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="电话">
                <PhoneOutlined /> {patient.phone ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="紧急联系人">
                {patient.emergencyContact ?? '-'}
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

          {/* 过敏史（常驻展示；空记录时显示空态，标题提供补录入口） */}
          <Card
            size="small"
            className={styles.sectionCard}
            title={
              <>
                <AlertOutlined /> 过敏史
              </>
            }
            extra={
              canEditAllergy ? (
                <Button
                  type="link"
                  size="small"
                  icon={<PlusOutlined />}
                  onClick={openAllergyModal}
                >
                  添加
                </Button>
              ) : null
            }
          >
            {allergies.length > 0 ? (
              <div>
                {allergies.map((a) => (
                  <Tag key={a.id} color="red">
                    {a.allergen}
                    {a.reaction ? `（${a.reaction}）` : ''}
                    {a.severity ? ` [${a.severity}]` : ''}
                  </Tag>
                ))}
              </div>
            ) : (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无过敏史" />
            )}
          </Card>

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
                          <Text type="secondary" className={styles.textSmall}>
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
                    <div className={styles.fullWidth}>
                      <Space>
                        <Text type="secondary" className={styles.textSmall}>
                          {r.date}
                        </Text>
                        <Tag>{r.type}</Tag>
                        {r.doctorName && (
                          <Text type="secondary" className={styles.textSmall}>
                            医生：{r.doctorName}
                          </Text>
                        )}
                        <Tag color={STATUS_MAP[r.status]?.color}>
                          {STATUS_MAP[r.status]?.text ?? r.status}
                        </Tag>
                      </Space>
                      {r.summary && (
                        <div>
                          <Text type="secondary" className={styles.textSmall}>
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
                      <Text type="secondary" className={styles.textSmall}>
                        处方 #{p.id}
                      </Text>
                      <Tag>{p.status}</Tag>
                      {p.issuedAt && (
                        <Text type="secondary" className={styles.textSmall}>
                          {dayjs(p.issuedAt).format('YYYY-MM-DD HH:mm')}
                        </Text>
                      )}
                    </Space>
                  </List.Item>
                )}
              />
            </Card>
          )}

          {!patientDetail.aiSummary &&
            allergies.length === 0 &&
            patientDetail.medicalHistories.length === 0 &&
            patientDetail.historyRecords.length === 0 &&
            patientDetail.recentPrescriptions.length === 0 && (
              <Empty description="暂无更多信息" />
            )}
        </Spin>
      </Drawer>

      {/* 补录过敏史弹窗 */}
      <Modal
        title="补录过敏史"
        open={allergyModalOpen}
        onCancel={() => setAllergyModalOpen(false)}
        onOk={handleAllergySubmit}
        okText="保存"
        cancelText="取消"
        confirmLoading={allergySubmitting}
        width={420}
        destroyOnHidden
      >
        <Form form={allergyForm} layout="vertical">
          <Form.Item
            name="allergen"
            label="过敏原"
            rules={[
              { required: true, message: '请输入过敏原' },
              { max: 200, message: '最多 200 个字符' },
            ]}
          >
            <Input placeholder="如：青霉素、布洛芬" />
          </Form.Item>
          <Form.Item
            name="reaction"
            label="反应描述"
            rules={[{ max: 500, message: '最多 500 个字符' }]}
          >
            <Input placeholder="如：皮疹伴瘙痒（选填）" />
          </Form.Item>
          <Form.Item
            name="severity"
            label="严重程度"
            rules={[{ required: true, message: '请选择严重程度' }]}
          >
            <Select options={SEVERITY_OPTIONS} placeholder="请选择" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}

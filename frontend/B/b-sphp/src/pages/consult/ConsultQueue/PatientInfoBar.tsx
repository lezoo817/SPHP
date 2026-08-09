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
  MedicineBoxOutlined,
  PhoneOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Button, Card, Descriptions, Drawer, Empty, List, Space, Spin, Tag, Typography } from 'antd';
import { useState } from 'react';
import dayjs from 'dayjs';
import styles from './index.module.less';
import { STATUS_MAP } from './constants';

const { Text } = Typography;

interface PatientInfoBarProps {
  selectedConsultId: number | null;
  detailLoading: boolean;
  patientDetail: API.PatientDetail | undefined;
}

/** 根据出生日期计算年龄（岁） */
function calcAge(dateOfBirth?: string): number | null {
  if (!dateOfBirth) return null;
  return dayjs().diff(dayjs(dateOfBirth), 'year');
}

/** 性别文本 */
function genderText(gender: string): string {
  if (gender === 'MALE') return '男';
  if (gender === 'FEMALE') return '女';
  return '未知';
}

export default function PatientInfoBar({
  selectedConsultId,
  detailLoading,
  patientDetail,
}: PatientInfoBarProps) {
  const [drawerOpen, setDrawerOpen] = useState(false);

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
        <Space size={10} align="center" style={{ minWidth: 0 }}>
          <UserOutlined style={{ color: '#1890ff' }} />
          <Text strong style={{ wordBreak: 'keep-all' }}>
            {patient.name}
          </Text>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {genderText(patient.gender)} {age !== null ? `${age}岁` : '年龄未知'}
          </Text>
          {hasAllergy ? (
            <Tag color="red" icon={<AlertOutlined />} style={{ marginInlineEnd: 0 }}>
              过敏 {allergies.length}
            </Tag>
          ) : (
            <Tag style={{ marginInlineEnd: 0 }}>无过敏</Tag>
          )}
        </Space>
        <Button
          type="link"
          size="small"
          icon={<DownOutlined />}
          onClick={() => setDrawerOpen(true)}
        >
          详情
        </Button>
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

          {/* 过敏史 */}
          {allergies.length > 0 && (
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
                {allergies.map((a) => (
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
                        {r.doctorName && (
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            医生：{r.doctorName}
                          </Text>
                        )}
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

          {!patientDetail.aiSummary &&
            allergies.length === 0 &&
            patientDetail.medicalHistories.length === 0 &&
            patientDetail.historyRecords.length === 0 &&
            patientDetail.recentPrescriptions.length === 0 && (
              <Empty description="暂无更多信息" />
            )}
        </Spin>
      </Drawer>
    </>
  );
}

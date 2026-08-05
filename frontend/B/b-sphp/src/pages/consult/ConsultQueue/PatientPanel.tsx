/**
 * 接诊台中间栏 - 患者详情。
 *
 * 展示基本信息 / AI 预问诊摘要 / 过敏史 / 既往史 / 历史就诊记录 / 近期处方。
 */
import { AlertOutlined, FileTextOutlined, HistoryOutlined, PhoneOutlined, MedicineBoxOutlined, UserOutlined } from '@ant-design/icons';
import { Card, Descriptions, Empty, List, Space, Spin, Tag, Typography } from 'antd';
import dayjs from 'dayjs';
import styles from './index.module.less';
import { STATUS_MAP } from './constants';

const { Text, Title } = Typography;

interface PatientPanelProps {
  selectedConsultId: number | null;
  detailLoading: boolean;
  patientDetail: API.PatientDetail | undefined;
}

/** 根据出生日期计算年龄（岁） */
function calcAge(dateOfBirth?: string): number | null {
  if (!dateOfBirth) return null;
  return dayjs().diff(dayjs(dateOfBirth), 'year');
}

export default function PatientPanel({
  selectedConsultId,
  detailLoading,
  patientDetail,
}: PatientPanelProps) {
  return (
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
}

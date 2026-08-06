/**
 * 接诊历史详情面板。
 *
 * 展示结构化病历报告（JSON 解析）或兼容旧版纯文本病历，以及关联处方与接诊时间。
 */
import type { ReactNode } from 'react';
import { Divider, Empty, List, Space, Spin, Tag, Typography } from 'antd';
import { MedicineBoxOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import styles from './index.module.less';

const { Text } = Typography;

interface HistoryDetailPanelProps {
  loading: boolean;
  detail: API.ConsultHistoryDetail | undefined;
}

/** 病历字段区块 */
function ReportSection({ title, text }: { title: string; text: string }) {
  return (
    <div style={{ marginBottom: 12 }}>
      <div className={styles.sectionTitle}>{title}</div>
      <div style={{ fontSize: 13, whiteSpace: 'pre-wrap', lineHeight: 1.7, marginTop: 4 }}>
        {text}
      </div>
    </div>
  );
}

/** 渲染病历内容：结构化 JSON → 分区块；纯文本 → 原样展示 */
function renderReport(doctorNote?: string): ReactNode {
  if (!doctorNote) return <Text type="secondary">无病历记录</Text>;

  let reportData: Record<string, string> | null = null;
  try {
    reportData = JSON.parse(doctorNote);
  } catch {
    // 兼容旧数据（纯文本病历）
  }

  if (reportData && reportData.chiefComplaint !== undefined) {
    return (
      <div>
        {reportData.chiefComplaint && <ReportSection title="主诉" text={reportData.chiefComplaint} />}
        {reportData.presentIllness && <ReportSection title="现病史" text={reportData.presentIllness} />}
        {reportData.physicalExamination && <ReportSection title="查体" text={reportData.physicalExamination} />}
        {reportData.diagnosis && <ReportSection title="诊断" text={reportData.diagnosis} />}
        {reportData.treatmentPlan && <ReportSection title="治疗方案" text={reportData.treatmentPlan} />}
        {(reportData.doctorName || reportData.generatedAt) && (
          <div style={{ fontSize: 12, color: '#999', marginTop: 8 }}>
            {reportData.doctorName && (
              <span style={{ marginRight: 16 }}>医生签名：{reportData.doctorName}</span>
            )}
            {reportData.generatedAt && <>病历报告生成时间：{reportData.generatedAt}</>}
          </div>
        )}
      </div>
    );
  }

  return <div style={{ fontSize: 13, whiteSpace: 'pre-wrap', lineHeight: 1.7 }}>{doctorNote}</div>;
}

export default function HistoryDetailPanel({ loading, detail }: HistoryDetailPanelProps) {
  return (
    <Spin spinning={loading}>
      {detail ? (
        <div style={{ padding: '4px 0' }}>
          {renderReport(detail.doctorNote)}

          {detail.prescriptions.length > 0 && (
            <>
              <Divider style={{ margin: '12px 0' }} />
              <div className={styles.sectionTitle}>
                <MedicineBoxOutlined /> 关联处方
              </div>
              <List
                size="small"
                dataSource={detail.prescriptions}
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
            {detail.endedAt ? (
              <>接诊时间：{dayjs(detail.endedAt).format('YYYY-MM-DD HH:mm')}</>
            ) : (
              <>创建时间：{dayjs(detail.createdAt).format('YYYY-MM-DD HH:mm')}</>
            )}
          </div>
        </div>
      ) : (
        <Empty description="加载中..." />
      )}
    </Spin>
  );
}

/**
 * 接诊历史详情面板。
 *
 * 展示病历全文（纯文本）、接诊医生姓名、关联处方与接诊时间。
 */
import { Divider, Empty, List, Space, Spin, Tag, Typography } from 'antd';
import { MedicineBoxOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import styles from './index.module.less';

const { Text } = Typography;

interface HistoryDetailPanelProps {
  loading: boolean;
  detail: API.ConsultHistoryDetail | undefined;
}

/**
 * 将文本中的换行/回车归一化为空格，避免脏数据（如 "u\ns\ne\nr\n1"）
 * 导致 React 渲染为每字符一行的竖排。
 */
function flattenText(value: string | undefined | null): string {
  if (!value) return '';
  return value.replace(/[\r\n]+/g, ' ').trim();
}

export default function HistoryDetailPanel({ loading, detail }: HistoryDetailPanelProps) {
  const doctorName = flattenText(detail?.doctorName);
  const doctorNote = flattenText(detail?.doctorNote);
  return (
    <Spin spinning={loading}>
      {detail ? (
        <div style={{ padding: '4px 0' }}>
          {/* 病历全文：纯文本（按"字段名：值"换行），wordBreak 防止窄容器字符级竖排 */}
          <div
            style={{
              fontSize: 13,
              whiteSpace: 'pre-wrap',
              lineHeight: 1.7,
              wordBreak: 'break-word',
              overflowWrap: 'break-word',
            }}
          >
            {doctorNote || '无病历记录'}
          </div>

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
          <Space size={16} wrap style={{ fontSize: 12, color: '#999', width: '100%' }}>
            {doctorName && (
              <span style={{ whiteSpace: 'nowrap' }}>接诊医生：{doctorName}</span>
            )}
            <span style={{ whiteSpace: 'nowrap' }}>
              {detail.endedAt
                ? `接诊时间：${dayjs(detail.endedAt).format('YYYY-MM-DD HH:mm')}`
                : `创建时间：${dayjs(detail.createdAt).format('YYYY-MM-DD HH:mm')}`}
            </span>
          </Space>
        </div>
      ) : (
        <Empty description="加载中..." />
      )}
    </Spin>
  );
}

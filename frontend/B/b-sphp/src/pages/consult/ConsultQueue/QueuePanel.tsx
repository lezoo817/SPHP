/**
 * 接诊台左栏 - 接诊队列。
 *
 * 待接诊 / 接诊中 / 接诊历史三 Tab：前两者展示实时队列，后者展示历史记录。
 */
import { Badge, Empty, List, Space, Spin, Tabs, Tag, Typography } from 'antd';
import {
  ClockCircleOutlined,
  OrderedListOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import styles from './index.module.less';
import { GENDER_MAP, STATUS_MAP } from './constants';
import type { QueueTab } from './constants';

const { Text, Title } = Typography;

interface QueuePanelProps {
  queueTab: QueueTab;
  queueItems: API.QueueItem[];
  queueLoading: boolean;
  queueTotal: number;
  selectedConsultId: number | null;
  historyItems: API.ConsultHistoryItem[];
  historyLoading: boolean;
  handleTabChange: (key: string) => void;
  handleSelectItem: (item: API.QueueItem) => void;
  handleSelectHistoryItem: (item: API.ConsultHistoryItem) => void;
}

export default function QueuePanel({
  queueTab,
  queueItems,
  queueLoading,
  queueTotal,
  selectedConsultId,
  historyItems,
  historyLoading,
  handleTabChange,
  handleSelectItem,
  handleSelectHistoryItem,
}: QueuePanelProps) {
  return (
    <div className={styles.panel}>
      <div className={styles.panelHeader}>
        <Title level={5} style={{ margin: 0 }}>
          <OrderedListOutlined /> 接诊队列
        </Title>
        {queueTab !== 'HISTORY' && <Badge count={queueTotal} showZero color="#1890ff" />}
      </div>
      <Tabs
        activeKey={queueTab}
        onChange={handleTabChange}
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
                      className={`${styles.queueItem} ${
                        isSelected ? styles.queueItemSelected : ''
                      }`}
                      onClick={() => handleSelectHistoryItem(item)}
                    >
                      <div className={styles.queueItemHeader}>
                        <Space>
                          <span style={{ color: gender.color }}>{gender.icon}</span>
                          <Text strong>{item.patientName}</Text>
                          {age !== null && (
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              {age}岁
                            </Text>
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
                  const statusCfg =
                    STATUS_MAP[item.status] ?? { text: item.status, color: 'default' };
                  return (
                    <List.Item
                      className={`${styles.queueItem} ${
                        isSelected ? styles.queueItemSelected : ''
                      }`}
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
                            {item.appointmentTime
                              ? dayjs(item.appointmentTime).format('HH:mm')
                              : '-'}
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
}

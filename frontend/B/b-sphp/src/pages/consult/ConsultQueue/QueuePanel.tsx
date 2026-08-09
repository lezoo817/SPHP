/**
 * 接诊台左栏 - 队列三块面板。
 *
 * 待接诊 / 接诊中 / 接诊历史三块纵向排列（不再用 Tab 切换），
 * 各自独立分页；三块共用「选中患者」高亮与右栏联动。
 * 接诊历史默认折叠，展开查看。
 */
import { Badge, Collapse, Empty, List, Pagination, Space, Spin, Tag, Typography } from 'antd';
import {
  ClockCircleOutlined,
  HistoryOutlined,
  OrderedListOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import styles from './index.module.less';
import { GENDER_MAP, STATUS_MAP } from './constants';

const { Text, Title } = Typography;

interface QueuePanelProps {
  // 待接诊
  pendingItems: API.QueueItem[];
  pendingLoading: boolean;
  pendingTotal: number;
  pendingPage: number;
  setPendingPage: (page: number) => void;
  // 接诊中
  inProgressItems: API.QueueItem[];
  inProgressLoading: boolean;
  inProgressTotal: number;
  inProgressPage: number;
  setInProgressPage: (page: number) => void;
  // 接诊历史
  historyItems: API.ConsultHistoryItem[];
  historyLoading: boolean;
  historyTotal: number;
  historyPage: number;
  setHistoryPage: (page: number) => void;
  // 选中
  selectedConsultId: number | null;
  handleSelectItem: (item: API.QueueItem) => void;
  handleSelectHistoryItem: (item: API.ConsultHistoryItem) => void;
}

/** 待接诊/接诊中队列项渲染 */
function QueueItemView({
  item,
  selected,
  onClick,
}: {
  item: API.QueueItem;
  selected: boolean;
  onClick: () => void;
}) {
  const gender = GENDER_MAP[item.patientGender] ?? GENDER_MAP.UNKNOWN;
  const statusCfg = STATUS_MAP[item.status] ?? { text: item.status, color: 'default' };
  return (
    <List.Item
      className={`${styles.queueItem} ${selected ? styles.queueItemSelected : ''}`}
      onClick={onClick}
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
            {item.slotStartTime && item.slotEndTime
              ? `${item.slotStartTime} - ${item.slotEndTime}`
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
}

/** 接诊历史项渲染 */
function HistoryItemView({
  item,
  selected,
  onClick,
}: {
  item: API.ConsultHistoryItem;
  selected: boolean;
  onClick: () => void;
}) {
  const gender = GENDER_MAP[item.patientGender] ?? GENDER_MAP.UNKNOWN;
  const age = item.patientDateOfBirth
    ? dayjs().diff(dayjs(item.patientDateOfBirth), 'year')
    : null;
  return (
    <List.Item
      className={`${styles.queueItem} ${selected ? styles.queueItemSelected : ''}`}
      onClick={onClick}
    >
      <div className={styles.queueItemHeader}>
        <Space style={{ minWidth: 0, flexShrink: 1 }}>
          <span style={{ color: gender.color }}>{gender.icon}</span>
          <Text strong style={{ wordBreak: 'keep-all', overflowWrap: 'normal' }}>
            {item.patientName}
          </Text>
          {age !== null && (
            <Text
              type="secondary"
              style={{ fontSize: 12, wordBreak: 'keep-all', overflowWrap: 'normal' }}
            >
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
        <Text
          type="secondary"
          style={{
            fontSize: 11,
            wordBreak: 'break-word',
            overflowWrap: 'anywhere',
          }}
        >
          {item.endedAt
            ? dayjs(item.endedAt).format('MM-DD HH:mm')
            : dayjs(item.createdAt).format('MM-DD HH:mm')}
          接诊
          {item.noteSummary &&
            ` · ${item.noteSummary.replace(/[\r\n]+/g, ' ').trim()}`}
        </Text>
      </div>
    </List.Item>
  );
}

export default function QueuePanel({
  pendingItems,
  pendingLoading,
  pendingTotal,
  pendingPage,
  setPendingPage,
  inProgressItems,
  inProgressLoading,
  inProgressTotal,
  inProgressPage,
  setInProgressPage,
  historyItems,
  historyLoading,
  historyTotal,
  historyPage,
  setHistoryPage,
  selectedConsultId,
  handleSelectItem,
  handleSelectHistoryItem,
}: QueuePanelProps) {
  return (
    <div className={styles.panel}>
      <div className={styles.panelHeader}>
        <Title level={5} style={{ margin: 0 }}>
          <OrderedListOutlined /> 接诊队列
        </Title>
      </div>

      <div className={styles.queueBlocks}>
        {/* 待接诊 */}
        <div className={styles.queueBlock}>
          <div className={styles.queueBlockTitle}>
            <span>待接诊</span>
            <Badge count={pendingTotal} showZero color="#1890ff" />
          </div>
          <div className={styles.queueBlockList}>
            <Spin spinning={pendingLoading}>
              {pendingItems.length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无待接诊" />
              ) : (
                <List
                  dataSource={pendingItems}
                  renderItem={(item) => (
                    <QueueItemView
                      item={item}
                      selected={item.consultId === selectedConsultId}
                      onClick={() => handleSelectItem(item)}
                    />
                  )}
                />
              )}
            </Spin>
          </div>
          <Pagination
            size="small"
            current={pendingPage}
            total={pendingTotal}
            pageSize={pendingItems.length || 1}
            onChange={setPendingPage}
            showSizeChanger={false}
          />
        </div>

        {/* 接诊中 */}
        <div className={styles.queueBlock}>
          <div className={styles.queueBlockTitle}>
            <span>接诊中</span>
            <Badge count={inProgressTotal} showZero color="#52c41a" />
          </div>
          <div className={styles.queueBlockList}>
            <Spin spinning={inProgressLoading}>
              {inProgressItems.length === 0 ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无接诊中" />
              ) : (
                <List
                  dataSource={inProgressItems}
                  renderItem={(item) => (
                    <QueueItemView
                      item={item}
                      selected={item.consultId === selectedConsultId}
                      onClick={() => handleSelectItem(item)}
                    />
                  )}
                />
              )}
            </Spin>
          </div>
          <Pagination
            size="small"
            current={inProgressPage}
            total={inProgressTotal}
            pageSize={inProgressItems.length || 1}
            onChange={setInProgressPage}
            showSizeChanger={false}
          />
        </div>

        {/* 接诊历史（默认折叠） */}
        <Collapse
          ghost
          className={styles.historyCollapse}
          defaultActiveKey={['history']}
          items={[
            {
              key: 'history',
              label: (
                <div className={styles.queueBlockTitle}>
                  <span>
                    <HistoryOutlined /> 接诊历史
                  </span>
                  <Badge count={historyTotal} showZero color="#8c8c8c" />
                </div>
              ),
              children: (
                <>
                  <div className={styles.queueBlockList}>
                    <Spin spinning={historyLoading}>
                      {historyItems.length === 0 ? (
                        <Empty
                          image={Empty.PRESENTED_IMAGE_SIMPLE}
                          description="暂无接诊历史"
                        />
                      ) : (
                        <List
                          dataSource={historyItems}
                          renderItem={(item) => (
                            <HistoryItemView
                              item={item}
                              selected={item.consultId === selectedConsultId}
                              onClick={() => handleSelectHistoryItem(item)}
                            />
                          )}
                        />
                      )}
                    </Spin>
                  </div>
                  <Pagination
                    size="small"
                    current={historyPage}
                    total={historyTotal}
                    pageSize={historyItems.length || 1}
                    onChange={setHistoryPage}
                    showSizeChanger={false}
                  />
                </>
              ),
            },
          ]}
        />
      </div>
    </div>
  );
}

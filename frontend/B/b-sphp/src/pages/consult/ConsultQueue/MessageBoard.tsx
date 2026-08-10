/**
 * 留言板（消息列表 + 输入框）。
 *
 * 底部薄条展示，占比小；标题栏可点击折叠/展开消息列表。
 * 消息按发送方左右分栏；Enter 发送 / Shift+Enter 换行。
 */
import { Button, Input, Spin, Typography } from 'antd';
import { HistoryOutlined, SendOutlined } from '@ant-design/icons';
import type { KeyboardEvent } from 'react';
import { useState } from 'react';
import dayjs from 'dayjs';
import styles from './MessageBoard.module.less';
import { SENDER_LABEL } from './constants';

const { Text } = Typography;
const { TextArea } = Input;

interface MessageBoardProps {
  messages: API.MessageVO[];
  loading: boolean;
  inputValue: string;
  sending: boolean;
  messagesEndRef: React.RefObject<HTMLDivElement>;
  onInputChange: (value: string) => void;
  onSend: () => void;
  onKeyDown: (e: KeyboardEvent<HTMLTextAreaElement>) => void;
}

export default function MessageBoard({
  messages,
  loading,
  inputValue,
  sending,
  messagesEndRef,
  onInputChange,
  onSend,
  onKeyDown,
}: MessageBoardProps) {
  const [collapsed, setCollapsed] = useState(true);

  return (
    <div className={styles.messageSection}>
      <div className={styles.messageHeader} onClick={() => setCollapsed(!collapsed)}>
        <div className={styles.sectionTitle}>
          <HistoryOutlined /> 留言板
        </div>
        <Text type="secondary" className={styles.textSmall}>
          {messages.length} 条 · {collapsed ? '展开' : '收起'}
        </Text>
      </div>

      {!collapsed && (
        <>
          <div className={styles.messageList}>
            <Spin spinning={loading}>
              {messages.length === 0 ? (
                <Text type="secondary" className={styles.emptyHint}>
                  暂无消息
                </Text>
              ) : (
                messages.map((msg) => (
                  <div
                    key={msg.messageId}
                    className={`${styles.messageItem} ${
                      msg.senderType === 'DOCTOR'
                        ? styles.messageRight
                        : styles.messageLeft
                    }`}
                  >
                    <div className={styles.messageBubble}>
                      <div className={styles.messageBubbleHeader}>
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
              value={inputValue}
              onChange={(e) => onInputChange(e.target.value)}
              onKeyDown={onKeyDown}
              placeholder="输入消息，Enter 发送，Shift+Enter 换行"
              disabled={sending}
            />
            <Button
              type="primary"
              icon={<SendOutlined />}
              loading={sending}
              onClick={onSend}
              disabled={!inputValue.trim()}
              className={styles.messageSend}
              block
            >
              发送
            </Button>
          </div>
        </>
      )}
    </div>
  );
}

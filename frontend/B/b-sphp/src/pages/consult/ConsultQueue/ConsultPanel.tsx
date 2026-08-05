/**
 * 接诊台右栏 - 接诊操作区。
 *
 * 按选中状态分支渲染：待接诊（开始接诊）/ 接诊中（病历编辑 + 留言板）/ 历史（详情）。
 */
import type { KeyboardEvent } from 'react';
import { Button, Divider, Empty, Typography } from 'antd';
import { MedicineBoxOutlined, StopOutlined } from '@ant-design/icons';
import styles from './index.module.less';
import type { QueueTab, SelectedStatus } from './constants';
import type { NoteField } from './NoteForm';
import StartConsultArea from './StartConsultArea';
import NoteForm from './NoteForm';
import MessageBoard from './MessageBoard';
import HistoryDetailPanel from './HistoryDetailPanel';

const { Title } = Typography;

interface ConsultPanelProps {
  queueTab: QueueTab;
  selectedConsultId: number | null;
  selectedStatus: SelectedStatus | null;
  historyDetail: API.ConsultHistoryDetail | undefined;
  historyDetailLoading: boolean;
  startingConsult: boolean;
  endingConsult: boolean;
  handleStartConsult: () => void;
  handleEndConsult: () => void;
  queueItems: API.QueueItem[];
  noteChanged: boolean;
  savingNote: boolean;
  reportChiefComplaint: string;
  reportPresentIllness: string;
  reportPhysicalExam: string;
  reportDiagnosis: string;
  reportTreatmentPlan: string;
  reportGeneratedAt: string;
  handleFieldChange: (field: NoteField, value: string) => void;
  handleSaveNote: () => void;
  consultPrescriptions: API.Prescription[];
  messages: API.MessageVO[];
  messagesLoading: boolean;
  messageInput: string;
  sendingMessage: boolean;
  messagesEndRef: React.RefObject<HTMLDivElement>;
  setMessageInput: (value: string) => void;
  handleSendMessage: () => void;
  handleMessageKeyDown: (e: KeyboardEvent<HTMLTextAreaElement>) => void;
}

export default function ConsultPanel({
  queueTab,
  selectedConsultId,
  selectedStatus,
  historyDetail,
  historyDetailLoading,
  startingConsult,
  endingConsult,
  handleStartConsult,
  handleEndConsult,
  queueItems,
  noteChanged,
  savingNote,
  reportChiefComplaint,
  reportPresentIllness,
  reportPhysicalExam,
  reportDiagnosis,
  reportTreatmentPlan,
  reportGeneratedAt,
  handleFieldChange,
  handleSaveNote,
  consultPrescriptions,
  messages,
  messagesLoading,
  messageInput,
  sendingMessage,
  messagesEndRef,
  setMessageInput,
  handleSendMessage,
  handleMessageKeyDown,
}: ConsultPanelProps) {
  const selectedItem = queueItems.find((i) => i.consultId === selectedConsultId);

  return (
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
          <HistoryDetailPanel loading={historyDetailLoading} detail={historyDetail} />
        ) : selectedStatus === 'PENDING' ? (
          <StartConsultArea
            slotStartTime={selectedItem?.slotStartTime}
            slotEndTime={selectedItem?.slotEndTime}
            starting={startingConsult}
            onStart={handleStartConsult}
          />
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

            {/* 病历记录 + 已开处方 */}
            <NoteForm
              values={{
                chiefComplaint: reportChiefComplaint,
                presentIllness: reportPresentIllness,
                physicalExam: reportPhysicalExam,
                diagnosis: reportDiagnosis,
                treatmentPlan: reportTreatmentPlan,
              }}
              noteChanged={noteChanged}
              savingNote={savingNote}
              generatedAt={reportGeneratedAt}
              consultPrescriptions={consultPrescriptions}
              onFieldChange={handleFieldChange}
              onSave={handleSaveNote}
            />

            <Divider style={{ margin: '12px 0' }} />

            {/* 留言板 */}
            <MessageBoard
              messages={messages}
              loading={messagesLoading}
              inputValue={messageInput}
              sending={sendingMessage}
              messagesEndRef={messagesEndRef}
              onInputChange={setMessageInput}
              onSend={handleSendMessage}
              onKeyDown={handleMessageKeyDown}
            />
          </div>
        ) : (
          <Empty description="问诊已结束" />
        )}
      </div>
    </div>
  );
}

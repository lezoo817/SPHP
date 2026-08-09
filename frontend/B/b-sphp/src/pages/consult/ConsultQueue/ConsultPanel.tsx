/**
 * 接诊台右栏 - 接诊操作区。
 *
 * 顶部患者信息条（姓名/性别/年龄/过敏预警，点详情展开 Drawer）；
 * 按选中状态分支渲染：待接诊（开始接诊）/ 接诊中（病历编辑+开处方+留言板）/ 历史（详情）。
 */
import type { KeyboardEvent } from 'react';
import { Button, Divider, Empty, Typography } from 'antd';
import { MedicineBoxOutlined, StopOutlined } from '@ant-design/icons';
import styles from './index.module.less';
import type { SelectedStatus } from './constants';
import type { NoteField } from './NoteForm';
import PatientInfoBar from './PatientInfoBar';
import StartConsultArea from './StartConsultArea';
import NoteForm from './NoteForm';
import MessageBoard from './MessageBoard';
import HistoryDetailPanel from './HistoryDetailPanel';

const { Title } = Typography;

interface ConsultPanelProps {
  selectedConsultId: number | null;
  selectedStatus: SelectedStatus | null;
  // 患者信息条
  patientDetail: API.PatientDetail | undefined;
  detailLoading: boolean;
  // 历史详情
  historyDetail: API.ConsultHistoryDetail | undefined;
  historyDetailLoading: boolean;
  // 待接诊选中项（用于开始接诊的时段展示与校验）
  pendingSelectedItem: API.QueueItem | undefined;
  startingConsult: boolean;
  endingConsult: boolean;
  handleStartConsult: () => void;
  handleEndConsult: () => void;
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
  // 开处方
  onOpenPrescription: () => void;
  // 留言板
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
  selectedConsultId,
  selectedStatus,
  patientDetail,
  detailLoading,
  historyDetail,
  historyDetailLoading,
  pendingSelectedItem,
  startingConsult,
  endingConsult,
  handleStartConsult,
  handleEndConsult,
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
  onOpenPrescription,
  messages,
  messagesLoading,
  messageInput,
  sendingMessage,
  messagesEndRef,
  setMessageInput,
  handleSendMessage,
  handleMessageKeyDown,
}: ConsultPanelProps) {
  return (
    <div className={styles.panel}>
      <div className={styles.panelHeader}>
        <Title level={5} style={{ margin: 0 }}>
          <MedicineBoxOutlined /> 接诊操作
        </Title>
      </div>

      {/* 患者信息条：一行展示核心信息 + 详情 Drawer，不占用操作区版面 */}
      <div className={styles.patientInfoBarWrap}>
        <PatientInfoBar
          selectedConsultId={selectedConsultId}
          detailLoading={detailLoading}
          patientDetail={patientDetail}
        />
      </div>

      <div className={styles.consultContent}>
        {!selectedConsultId ? (
          <Empty description="请选择患者" />
        ) : selectedStatus === 'COMPLETED' ? (
          // 历史接诊详情
          <HistoryDetailPanel loading={historyDetailLoading} detail={historyDetail} />
        ) : selectedStatus === 'PENDING' ? (
          <StartConsultArea
            slotStartTime={pendingSelectedItem?.slotStartTime}
            slotEndTime={pendingSelectedItem?.slotEndTime}
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

            {/* 病历记录 + 开处方 + 已开处方 */}
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
              onOpenPrescription={onOpenPrescription}
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

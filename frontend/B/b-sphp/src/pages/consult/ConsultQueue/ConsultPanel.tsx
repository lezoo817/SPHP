/**
 * 接诊台右栏 - 接诊操作区。
 *
 * 顶部患者信息条（姓名/性别/年龄/过敏预警，点详情展开 Drawer）；
 * 接诊中主工作区：病历记录（左）与已开处方（右）左右分栏，留言板折叠在底部；
 * 按选中状态分支渲染：待接诊（开始接诊）/ 接诊中（病历+处方+留言板）/ 历史（详情）。
 */
import type { KeyboardEvent } from 'react';
import { Button, Empty, Typography } from 'antd';
import { MedicineBoxOutlined, StopOutlined } from '@ant-design/icons';
import styles from './ConsultPanel.module.less';
import type { SelectedStatus } from './constants';
import type { NoteField } from './NoteForm';
import PatientInfoBar from './PatientInfoBar';
import StartConsultArea from './StartConsultArea';
import NoteForm from './NoteForm';
import PrescriptionPanel from './PrescriptionPanel';
import MessageBoard from './MessageBoard';
import HistoryDetailPanel from './HistoryDetailPanel';

const { Title } = Typography;

interface ConsultPanelProps {
  selectedConsultId: number | null;
  selectedStatus: SelectedStatus | null;
  // 患者信息条
  patientDetail: API.PatientDetail | undefined;
  detailLoading: boolean;
  /** 补录过敏史（成功后刷新患者详情） */
  handleAddAllergy: (data: API.AllergyCreateReq) => Promise<void>;
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
  onViewPrescription: (id: number) => void;
  onReopenPrescription: (id: number) => void;
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
  handleAddAllergy,
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
  onViewPrescription,
  onReopenPrescription,
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
        {/* 结束问诊：接诊中常驻在标题行右侧，随时可结束 */}
        {selectedStatus === 'IN_PROGRESS' && (
          <Button danger icon={<StopOutlined />} loading={endingConsult} onClick={handleEndConsult}>
            结束问诊
          </Button>
        )}
      </div>

      {/* 患者信息条：一行展示核心信息 + 详情 Drawer，不占用操作区版面 */}
      <div className={styles.patientInfoBarWrap}>
        <PatientInfoBar
          selectedConsultId={selectedConsultId}
          detailLoading={detailLoading}
          patientDetail={patientDetail}
          canEditAllergy={selectedStatus === 'IN_PROGRESS'}
          onAddAllergy={handleAddAllergy}
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
            {/* 主工作区：病历记录 + 已开处方 左右分栏 */}
            <div className={styles.workspaceRow}>
              <div className={styles.noteColumn}>
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
                  onFieldChange={handleFieldChange}
                  onSave={handleSaveNote}
                />
              </div>
              <div className={styles.prescriptionColumn}>
                <PrescriptionPanel
                  consultPrescriptions={consultPrescriptions}
                  onOpenPrescription={onOpenPrescription}
                  onViewPrescription={onViewPrescription}
                  onReopenPrescription={onReopenPrescription}
                />
              </div>
            </div>

            {/* 留言板：底部薄条，可折叠 */}
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

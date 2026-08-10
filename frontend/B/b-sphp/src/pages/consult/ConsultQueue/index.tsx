/**
 * 接诊台 - 两栏布局主页面
 *
 * 左栏：接诊队列（待接诊 / 接诊中 / 接诊历史 三块，各带分页）
 * 右栏：接诊操作区（患者信息条 + 开始/结束接诊 + 病历编辑 + 开处方 + 留言板）
 *
 * 数据与操作逻辑集中在 useConsultQueue；本组件仅组合两栏并处理管理员占位。
 */
import { Empty, Typography } from 'antd';
import { useConsultQueue } from './useConsultQueue';
import QueuePanel from './QueuePanel';
import ConsultPanel from './ConsultPanel';
import PrescriptionFormModal from './PrescriptionFormModal';
import PrescriptionDetailModal from '@/pages/prescription/PrescriptionList/PrescriptionDetailModal';
import styles from './index.module.less';

const { Text } = Typography;

export default function ConsultQueuePage() {
  const consult = useConsultQueue();

  if (consult.isAdmin) {
    return (
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          minHeight: 300,
        }}
      >
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={<Text type="secondary">管理员不参与接诊，无需使用接诊台</Text>}
        />
      </div>
    );
  }

  // 待接诊选中的队列项：供开始接诊区域的号源时段展示与校验
  const pendingSelectedItem = consult.pendingItems.find(
    (i) => i.consultId === consult.selectedConsultId,
  );

  return (
    <div className={styles.container}>
      <div className={styles.leftPanel}>
        <QueuePanel
          pendingItems={consult.pendingItems}
          pendingLoading={consult.pendingLoading}
          pendingTotal={consult.pendingTotal}
          pendingPage={consult.pendingPage}
          setPendingPage={consult.setPendingPage}
          inProgressItems={consult.inProgressItems}
          inProgressLoading={consult.inProgressLoading}
          inProgressTotal={consult.inProgressTotal}
          inProgressPage={consult.inProgressPage}
          setInProgressPage={consult.setInProgressPage}
          historyItems={consult.historyItems}
          historyLoading={consult.historyLoading}
          historyTotal={consult.historyTotal}
          historyPage={consult.historyPage}
          setHistoryPage={consult.setHistoryPage}
          selectedConsultId={consult.selectedConsultId}
          handleSelectItem={consult.handleSelectItem}
          handleSelectHistoryItem={consult.handleSelectHistoryItem}
        />
      </div>
      <div className={styles.rightPanel}>
        <ConsultPanel
          {...consult}
          pendingSelectedItem={pendingSelectedItem}
          onOpenPrescription={() => consult.setPrescriptionModalOpen(true)}
          onViewPrescription={consult.handleViewPrescription}
          onReopenPrescription={consult.handleReopenPrescription}
        />
      </div>

      <PrescriptionFormModal
        open={consult.prescriptionModalOpen}
        submitting={consult.submittingPrescription}
        initialItems={consult.prescriptionPrefill ?? undefined}
        doctorDeptId={consult.doctorDeptId}
        consultId={consult.selectedConsultId}
        onCancel={consult.closePrescriptionModal}
        onSubmit={consult.handleSubmitPrescription}
      />

      <PrescriptionDetailModal
        open={consult.prescriptionDetailOpen}
        loading={consult.prescriptionDetailLoading}
        data={consult.prescriptionDetailData}
        onCancel={consult.handleClosePrescriptionDetail}
      />
    </div>
  );
}

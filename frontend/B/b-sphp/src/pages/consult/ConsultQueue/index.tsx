/**
 * 接诊台 - 三栏布局主页面
 *
 * 左栏（35%）：待接诊队列（PENDING / IN_PROGRESS / HISTORY 切换）
 * 中栏（35%）：患者详情（基本信息、过敏史、既往史、AI 摘要、历史就诊、近期处方）
 * 右栏（30%）：接诊操作区（开始/结束接诊、病历编辑、留言板）
 *
 * 数据与操作逻辑集中在 useConsultQueue；本组件仅组合三栏并处理管理员占位。
 */
import { Empty, Typography } from 'antd';
import { useConsultQueue } from './useConsultQueue';
import QueuePanel from './QueuePanel';
import PatientPanel from './PatientPanel';
import ConsultPanel from './ConsultPanel';
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

  return (
    <div className={styles.container}>
      <div className={styles.leftPanel}>
        <QueuePanel {...consult} />
      </div>
      <div className={styles.middlePanel}>
        <PatientPanel {...consult} />
      </div>
      <div className={styles.rightPanel}>
        <ConsultPanel {...consult} />
      </div>
    </div>
  );
}

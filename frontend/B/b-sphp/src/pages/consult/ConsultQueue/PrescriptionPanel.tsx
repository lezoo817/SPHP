/**
 * 接诊台右栏 - 已开处方面板。
 *
 * 与病历记录左右分栏展示；SUBMITTED 展示风险快照 Tag，REJECTED 提供「重新开方」
 * （带明细预填重提）。空态引导打开开方弹窗。
 */
import { Button, Empty, List, Space, Tag, Tooltip, Typography } from 'antd';
import { MedicineBoxOutlined, PlusOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import styles from './less/ConsultPanel.module.less';
import { STATUS_APPROVED, STATUS_REJECTED, STATUS_SUBMITTED } from '@/constants/businessStatus';

const { Text } = Typography;

interface PrescriptionPanelProps {
  consultPrescriptions: API.Prescription[];
  /** 打开开处方弹窗 */
  onOpenPrescription: () => void;
  /** 查看处方详情（含风险快照 / 驳回原因） */
  onViewPrescription: (id: number) => void;
  /** 驳回重开：取被驳回处方明细预填进开方弹窗 */
  onReopenPrescription: (id: number) => void;
}

/** 处方状态文案 */
function statusText(status: string): string {
  if (status === STATUS_APPROVED) return '已通过';
  if (status === STATUS_SUBMITTED) return '待审核';
  if (status === STATUS_REJECTED) return '已驳回';
  return status;
}

export default function PrescriptionPanel({
  consultPrescriptions,
  onOpenPrescription,
  onViewPrescription,
  onReopenPrescription,
}: PrescriptionPanelProps) {
  return (
    <div className={styles.prescriptionSection}>
      <div className={styles.prescriptionHeader}>
        <div className={styles.sectionTitle}>
          <MedicineBoxOutlined /> 已开处方
        </div>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={onOpenPrescription}
          size="small"
        >
          开处方
        </Button>
      </div>

      {consultPrescriptions.length === 0 ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="暂无处方，点击右上角开处方"
          className={styles.prescriptionEmpty}
        />
      ) : (
        <List
          size="small"
          dataSource={consultPrescriptions}
          renderItem={(p) => {
            const riskCount = p.riskWarnings?.length ?? 0;
            const actions = [
              <Button
                key="view"
                type="link"
                size="small"
                className={styles.linkBtn}
                onClick={() => onViewPrescription(p.id)}
              >
                查看
              </Button>,
              ...(p.status === STATUS_REJECTED
                ? [
                    <Button
                      key="reopen"
                      type="link"
                      size="small"
                      danger
                      className={styles.linkBtn}
                      onClick={() => onReopenPrescription(p.id)}
                    >
                      重新开方
                    </Button>,
                  ]
                : []),
            ];
            return (
              <List.Item actions={actions} className={styles.prescriptionItem}>
                <Space direction="vertical" size={0}>
                  <div className={styles.prescriptionItemMain}>
                    <Text className={styles.textSmall}>处方 #{p.id}</Text>
                    <div className={styles.prescriptionItemTags}>
                      <Tag className={styles.tagInline}>{statusText(p.status)}</Tag>
                      {riskCount > 0 && (
                        <Tooltip
                          title={
                            <Space direction="vertical" size={2}>
                              {p.riskWarnings?.map((w, i) => (
                                <span key={i} className={styles.textSmall}>
                                  {w.message}
                                </span>
                              ))}
                            </Space>
                          }
                        >
                          <Tag color="orange" className={styles.tagRisk}>
                            ⚠ {riskCount} 条风险
                          </Tag>
                        </Tooltip>
                      )}
                    </div>
                  </div>
                  <div className={styles.prescriptionItemMeta}>
                    <Text type="secondary" className={styles.textSmall}>
                      {p.itemCount} 项
                    </Text>
                    {p.issuedAt && (
                      <Text type="secondary" className={styles.textSmall}>
                        {dayjs(p.issuedAt).format('MM-DD HH:mm')}
                      </Text>
                    )}
                  </div>
                </Space>
              </List.Item>
            );
          }}
        />
      )}
    </div>
  );
}

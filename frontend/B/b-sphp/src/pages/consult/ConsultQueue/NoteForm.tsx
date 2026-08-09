/**
 * 病历记录表单（结构化病历：主诉/现病史/查体/诊断/治疗方案）+ 已开处方列表。
 *
 * 受控组件：字段值与变更回调由父级传入；保存按钮由 noteChanged 控制可用性。
 * 已开处方区：SUBMITTED 展示风险快照 Tag，REJECTED 提供「重新开方」（带明细预填重提）。
 */
import { Button, Divider, Input, List, Space, Tag, Tooltip, Typography } from 'antd';
import { FileTextOutlined, SaveOutlined, MedicineBoxOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import styles from './index.module.less';

const { Text } = Typography;
const { TextArea } = Input;

/** 病历字段键 */
export type NoteField =
  | 'chiefComplaint'
  | 'presentIllness'
  | 'physicalExam'
  | 'diagnosis'
  | 'treatmentPlan';

/** 字段渲染配置 */
const FIELD_CONFIGS: { key: NoteField; label: string; placeholder: string }[] = [
  { key: 'chiefComplaint', label: '主诉', placeholder: '患者主要症状及持续时间' },
  { key: 'presentIllness', label: '现病史', placeholder: '发病经过、诊治情况' },
  { key: 'physicalExam', label: '查体', placeholder: '生命体征、专科检查' },
  { key: 'diagnosis', label: '诊断', placeholder: '初步诊断' },
  { key: 'treatmentPlan', label: '治疗方案', placeholder: '治疗建议、注意事项' },
];

interface NoteFormProps {
  values: Record<NoteField, string>;
  noteChanged: boolean;
  savingNote: boolean;
  generatedAt: string;
  consultPrescriptions: API.Prescription[];
  onFieldChange: (field: NoteField, value: string) => void;
  onSave: () => void;
  /** 打开开处方弹窗 */
  onOpenPrescription: () => void;
  /** 查看处方详情（含风险快照 / 驳回原因） */
  onViewPrescription: (id: number) => void;
  /** 驳回重开：取被驳回处方明细预填进开方弹窗 */
  onReopenPrescription: (id: number) => void;
}

export default function NoteForm({
  values,
  noteChanged,
  savingNote,
  generatedAt,
  consultPrescriptions,
  onFieldChange,
  onSave,
  onOpenPrescription,
  onViewPrescription,
  onReopenPrescription,
}: NoteFormProps) {
  return (
    <div className={styles.noteSection}>
      <div className={styles.sectionTitle}>
        <FileTextOutlined /> 病历记录
      </div>

      <div style={{ marginBottom: 8 }}>
        {FIELD_CONFIGS.map((cfg) => (
          <div key={cfg.key}>
            <div style={{ fontSize: 12, fontWeight: 500, marginBottom: 4 }}>{cfg.label}</div>
            <TextArea
              rows={2}
              value={values[cfg.key]}
              onChange={(e) => onFieldChange(cfg.key, e.target.value)}
              placeholder={cfg.placeholder}
              style={{ marginBottom: 8 }}
            />
          </div>
        ))}
      </div>

      <Space style={{ marginBottom: 8 }}>
        <Button
          type="primary"
          icon={<SaveOutlined />}
          loading={savingNote}
          onClick={onSave}
          disabled={!noteChanged}
          size="small"
        >
          保存病历
        </Button>
        <Button
          icon={<MedicineBoxOutlined />}
          onClick={onOpenPrescription}
          size="small"
        >
          开处方
        </Button>
        {generatedAt && (
          <Text type="secondary" style={{ fontSize: 12 }}>
            病历报告生成时间：{generatedAt}
          </Text>
        )}
      </Space>

      {consultPrescriptions.length > 0 && (
        <div style={{ marginTop: 12 }}>
          <Divider style={{ margin: '8px 0' }} />
          <div className={styles.sectionTitle}>
            <MedicineBoxOutlined /> 已开处方
          </div>
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
                  style={{ padding: 0 }}
                  onClick={() => onViewPrescription(p.id)}
                >
                  查看
                </Button>,
                ...(p.status === 'REJECTED'
                  ? [
                      <Button
                        key="reopen"
                        type="link"
                        size="small"
                        danger
                        style={{ padding: 0 }}
                        onClick={() => onReopenPrescription(p.id)}
                      >
                        重新开方
                      </Button>,
                    ]
                  : []),
              ];
              return (
                <List.Item actions={actions}>
                  <Space direction="vertical" size={0}>
                    <Space size={6} wrap>
                      <Text style={{ fontSize: 12 }}>处方 #{p.id}</Text>
                      <Tag style={{ marginRight: 0 }}>
                        {p.status === 'APPROVED'
                          ? '已通过'
                          : p.status === 'SUBMITTED'
                            ? '待审核'
                            : p.status === 'REJECTED'
                              ? '已驳回'
                              : p.status}
                      </Tag>
                      {riskCount > 0 && (
                        <Tooltip
                          title={
                            <Space direction="vertical" size={2}>
                              {p.riskWarnings?.map((w, i) => (
                                <span key={i} style={{ fontSize: 12 }}>
                                  {w.message}
                                </span>
                              ))}
                            </Space>
                          }
                        >
                          <Tag color="orange" style={{ marginRight: 0, cursor: 'pointer' }}>
                            ⚠ {riskCount} 条风险
                          </Tag>
                        </Tooltip>
                      )}
                    </Space>
                    <Space size={12}>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        {p.itemCount} 项
                      </Text>
                      {p.issuedAt && (
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {dayjs(p.issuedAt).format('MM-DD HH:mm')}
                        </Text>
                      )}
                    </Space>
                  </Space>
                </List.Item>
              );
            }}
          />
        </div>
      )}
    </div>
  );
}

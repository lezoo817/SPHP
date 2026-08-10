/**
 * 病历记录表单（结构化病历：主诉/现病史/查体/诊断/治疗方案）。
 *
 * 受控组件：字段值与变更回调由父级传入；保存按钮由 noteChanged 控制可用性。
 * 已开处方列表已拆分为独立 PrescriptionPanel，与病历左右分栏展示。
 */
import { Button, Input, Typography } from 'antd';
import { FileTextOutlined, SaveOutlined } from '@ant-design/icons';
import styles from './NoteForm.module.less';

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
  onFieldChange: (field: NoteField, value: string) => void;
  onSave: () => void;
}

export default function NoteForm({
  values,
  noteChanged,
  savingNote,
  generatedAt,
  onFieldChange,
  onSave,
}: NoteFormProps) {
  return (
    <div className={styles.noteSection}>
      <div className={styles.sectionTitle}>
        <FileTextOutlined /> 病历记录
      </div>

      {FIELD_CONFIGS.map((cfg) => (
        <div key={cfg.key} className={styles.fieldItem}>
          <div className={styles.fieldLabel}>{cfg.label}</div>
          <TextArea
            rows={2}
            value={values[cfg.key]}
            onChange={(e) => onFieldChange(cfg.key, e.target.value)}
            placeholder={cfg.placeholder}
          />
        </div>
      ))}

      <div className={styles.toolbar}>
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
        {generatedAt && (
          <Text type="secondary" className={styles.textSmall}>
            病历报告生成时间：{generatedAt}
          </Text>
        )}
      </div>
    </div>
  );
}

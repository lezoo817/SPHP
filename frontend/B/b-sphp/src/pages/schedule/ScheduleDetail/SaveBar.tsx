/**
 * 排班详情页保存栏（号源总和提示 + 保存按钮；已发布时展示禁用态）。
 */
import { Button, Tooltip } from 'antd';
import { SaveOutlined } from '@ant-design/icons';

interface Props {
  canEdit: boolean;
  isPublished: boolean;
  slotSum: number;
  totalSlots: number;
  saving: boolean;
  onSave: () => void;
}

export default function SaveBar({
  canEdit,
  isPublished,
  slotSum,
  totalSlots,
  saving,
  onSave,
}: Props) {
  if (!canEdit && !isPublished) return null;

  const overLimit = slotSum > totalSlots;

  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'flex-end',
        alignItems: 'center',
        marginTop: 16,
      }}
    >
      {canEdit && slotSum !== totalSlots ? (
        <span
          style={{
            color: overLimit ? '#ff4d4f' : '#faad14',
            fontSize: 12,
            marginRight: 12,
          }}
        >
          {overLimit
            ? `时段号源总和（${slotSum}）大于排班总号源数（${totalSlots}），请调整后再保存`
            : `时段号源总和（${slotSum}）小于排班总号源数（${totalSlots}），须补齐至相等方可发布`}
        </span>
      ) : null}
      {canEdit ? (
        <Button
          type="primary"
          icon={<SaveOutlined />}
          loading={saving}
          style={
            overLimit
              ? {
                  backgroundColor: '#f5f5f5',
                  borderColor: '#d9d9d9',
                  color: 'rgba(0, 0, 0, 0.25)',
                }
              : undefined
          }
          onClick={onSave}
        >
          保存配置
        </Button>
      ) : isPublished ? (
        <Tooltip title="排班已发布，不可修改时段配置">
          <Button type="primary" icon={<SaveOutlined />} disabled>
            保存配置
          </Button>
        </Tooltip>
      ) : null}
    </div>
  );
}

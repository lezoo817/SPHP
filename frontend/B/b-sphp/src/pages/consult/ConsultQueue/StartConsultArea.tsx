/**
 * 待接诊状态下的开始接诊操作区。
 *
 * 前端时段校验：当前时间不在号源时段内时禁用开始按钮并给出提示。
 */
import { Button, Tooltip, Typography } from 'antd';
import { PlayCircleOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import styles from './index.module.less';

const { Text } = Typography;

interface StartConsultAreaProps {
  /** 号源时段开始时间（HH:mm），可能未配置 */
  slotStartTime?: string;
  /** 号源时段结束时间（HH:mm） */
  slotEndTime?: string;
  starting: boolean;
  onStart: () => void;
}

export default function StartConsultArea({
  slotStartTime,
  slotEndTime,
  starting,
  onStart,
}: StartConsultAreaProps) {
  const hasSlotTime = Boolean(slotStartTime && slotEndTime);
  const slotInfo = hasSlotTime ? `${slotStartTime}~${slotEndTime}` : '';
  const now = dayjs();
  const start = hasSlotTime ? dayjs(slotStartTime, 'HH:mm') : null;
  const end = hasSlotTime ? dayjs(slotEndTime, 'HH:mm') : null;
  const currentTime = dayjs(now.format('HH:mm'), 'HH:mm');
  const withinSlot = !start || !end || (!currentTime.isBefore(start) && !currentTime.isAfter(end));

  return (
    <div className={styles.startConsultArea}>
      <Tooltip title={!withinSlot ? `不在接诊时间内（${slotInfo}）` : ''}>
        <Button
          type="primary"
          size="large"
          icon={<PlayCircleOutlined />}
          loading={starting}
          onClick={onStart}
          disabled={!withinSlot}
          block
        >
          开始接诊
        </Button>
      </Tooltip>
      <Text type="secondary" style={{ display: 'block', textAlign: 'center', marginTop: 8 }}>
        {slotInfo ? `预约时段：${slotInfo}` : '未配置号源时段'}
        {slotInfo && !withinSlot ? '（当前不在接诊时间内）' : ''}
      </Text>
      <Text type="secondary" style={{ display: 'block', textAlign: 'center', marginTop: 4 }}>
        点击后开始接诊，将进入接诊中状态
      </Text>
    </div>
  );
}

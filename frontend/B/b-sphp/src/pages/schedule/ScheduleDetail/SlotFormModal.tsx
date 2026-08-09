/**
 * 添加/编辑时段弹窗。
 *
 * - 时段起止时间须落在班次窗口内（不可跨越 12:00-14:00 非上班时段）；
 * - 排班日期为今天时，不可选择早于当前时刻的时段；
 * - 开始/结束时间的禁用项随窗口、当前时刻、已选开始时间实时联动。
 */
import { Form, Modal, TimePicker, InputNumber, message } from 'antd';
import { useMemo } from 'react';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import { getShiftWindow, getShiftConfig } from '../constants';

/** 时段表单值（时间为 Dayjs，提交时格式化为 HH:mm） */
interface SlotFormValues {
  startTime: Dayjs;
  endTime: Dayjs;
  count: number;
}

interface Props {
  open: boolean;
  /** 当前编辑的时段；null=新增 */
  editingSlot: API.SlotConfig | null;
  shift: string;
  scheduleDate: string;
  totalSlots: number;
  onCancel: () => void;
  onSubmit: (slot: API.SlotConfig) => void;
}

export default function SlotFormModal({
  open,
  editingSlot,
  shift,
  scheduleDate,
  totalSlots,
  onCancel,
  onSubmit,
}: Props) {
  const [slotForm] = Form.useForm<SlotFormValues>();
  /** 已选的开始时间（useWatch 保证结束时间禁用项随其实时更新） */
  const startTimeValue = Form.useWatch('startTime', slotForm);

  /** 当前排班班次对应的时间窗（小时）；未知班次时不限制窗口 */
  const shiftWin = useMemo(() => getShiftWindow(shift), [shift]);
  /** 排班日期是否为今天（仅当天限制不可选早于当前时刻的时段） */
  const isScheduleToday = dayjs(scheduleDate).isSame(dayjs(), 'day');

  /** 开始时间禁用项：须落在班次窗口内（不含结束时刻）；今天叠加不可早于当前时刻 */
  const startDisabledTime = () => {
    const win = shiftWin;
    const now = dayjs();
    const disHours = new Set<number>();
    const disMinByHour = new Map<number, Set<number>>();
    if (win) {
      for (let h = 0; h < win.start; h++) disHours.add(h);
      for (let h = win.end; h < 24; h++) disHours.add(h);
    }
    if (isScheduleToday) {
      for (let h = 0; h < now.hour(); h++) disHours.add(h);
      const set = disMinByHour.get(now.hour()) ?? new Set<number>();
      for (let m = 0; m < now.minute(); m++) set.add(m);
      disMinByHour.set(now.hour(), set);
    }
    return {
      disabledHours: () => [...disHours].sort((a, b) => a - b),
      disabledMinutes: (h: number) =>
        [...(disMinByHour.get(h) ?? [])].sort((a, b) => a - b),
    };
  };

  /** 结束时间禁用项：须落在班次窗口内（含结束时刻，仅整点如 12:00/18:00）；不可早于开始时间；今天叠加不可早于当前时刻 */
  const endDisabledTime = () => {
    const win = shiftWin;
    const now = dayjs();
    const disHours = new Set<number>();
    const disMinByHour = new Map<number, Set<number>>();
    if (win) {
      for (let h = 0; h < win.start; h++) disHours.add(h);
      for (let h = win.end + 1; h < 24; h++) disHours.add(h);
      // 窗口结束时刻仅允许 :00，防止出现 12:30 这类越界时刻
      const endSet = disMinByHour.get(win.end) ?? new Set<number>();
      for (let m = 1; m < 60; m++) endSet.add(m);
      disMinByHour.set(win.end, endSet);
    }
    if (startTimeValue) {
      for (let h = 0; h < startTimeValue.hour(); h++) disHours.add(h);
      const set = disMinByHour.get(startTimeValue.hour()) ?? new Set<number>();
      for (let m = 0; m < startTimeValue.minute(); m++) set.add(m);
      disMinByHour.set(startTimeValue.hour(), set);
    }
    if (isScheduleToday) {
      for (let h = 0; h < now.hour(); h++) disHours.add(h);
      const set = disMinByHour.get(now.hour()) ?? new Set<number>();
      for (let m = 0; m < now.minute(); m++) set.add(m);
      disMinByHour.set(now.hour(), set);
    }
    return {
      disabledHours: () => [...disHours].sort((a, b) => a - b),
      disabledMinutes: (h: number) =>
        [...(disMinByHour.get(h) ?? [])].sort((a, b) => a - b),
    };
  };

  /** 表单初始值：编辑时回填已有时段；新增时为空 */
  const formInitialValues = useMemo(
    () =>
      editingSlot
        ? {
            startTime: dayjs(editingSlot.startTime, 'HH:mm'),
            endTime: dayjs(editingSlot.endTime, 'HH:mm'),
            count: editingSlot.totalCount,
          }
        : undefined,
    [editingSlot],
  );

  /** 提交：校验后组装时段配置项 */
  const handleSubmit = async () => {
    const values = await slotForm.validateFields();
    if (!values.endTime.isAfter(values.startTime)) {
      message.error('结束时间需晚于开始时间');
      return;
    }
    onSubmit({
      startTime: values.startTime.format('HH:mm'),
      endTime: values.endTime.format('HH:mm'),
      totalCount: values.count,
      remainCount: values.count,
    });
  };

  return (
    <Modal
      title={editingSlot ? '编辑时段' : '添加时段'}
      open={open}
      onOk={handleSubmit}
      onCancel={onCancel}
      destroyOnHidden
      width={420}
    >
      <Form form={slotForm} layout="vertical" initialValues={formInitialValues}>
        {shiftWin ? (
          <div style={{ fontSize: 12, color: '#faad14', marginBottom: 12 }}>
            本排班为「{getShiftConfig(shift)?.text ?? shift}」班次，号源时段须在{' '}
            {String(shiftWin.start).padStart(2, '0')}:00-
            {String(shiftWin.end).padStart(2, '0')}:00 内配置，不可跨越非上班时段
          </div>
        ) : null}
        <Form.Item
          name="startTime"
          label="开始时间"
          rules={[{ required: true, message: '请选择开始时间' }]}
        >
          <TimePicker
            format="HH:mm"
            style={{ width: '100%' }}
            placeholder="选择开始时间"
            minuteStep={5}
            disabledTime={startDisabledTime}
            hideDisabledOptions
          />
        </Form.Item>
        <Form.Item
          name="endTime"
          label="结束时间"
          rules={[{ required: true, message: '请选择结束时间' }]}
        >
          <TimePicker
            format="HH:mm"
            style={{ width: '100%' }}
            placeholder="选择结束时间"
            minuteStep={5}
            disabledTime={endDisabledTime}
            hideDisabledOptions
          />
        </Form.Item>
        <Form.Item
          name="count"
          label="号源数"
          rules={[
            { required: true, message: '请输入该时段号源数' },
            { type: 'number', min: 1, message: '号源数至少为 1' },
            {
              type: 'number',
              max: totalSlots,
              message: `号源数不能大于排班总号源数（${totalSlots}）`,
            },
          ]}
        >
          {/* 不设 InputNumber max，避免超出时静默钳制；由上方校验规则以红色小字提示 */}
          <InputNumber
            min={1}
            style={{ width: '100%' }}
            placeholder="请输入该时段号源数"
            addonAfter="个"
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}

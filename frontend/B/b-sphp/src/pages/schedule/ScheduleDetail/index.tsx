/**
 * 排班详情页（号源时段配置）
 * - 展示排班元信息 + 时段列表
 * - 仅 DRAFT 且 ADMIN 可增删改时段并保存；PUBLISHED 全部编辑能力禁用（业务铁律）
 * - 时段号源数之和不得超过排班总号源数（剩余留作机动号源）
 * - 元信息（医生/日期/班次/状态）由列表页通过 query 参数携带，刷新后可恢复
 */
import {
  Button,
  Card,
  Table,
  Modal,
  Form,
  InputNumber,
  TimePicker,
  Tag,
  message,
  Alert,
  Descriptions,
  Space,
  Tooltip,
} from 'antd';
import { PlusOutlined, ArrowLeftOutlined, SaveOutlined } from '@ant-design/icons';
import { useModel, useNavigate, useParams, useSearchParams } from '@umijs/max';
import { useEffect, useState } from 'react';
import { getScheduleSlots, configureScheduleSlots } from '@/services/admin';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';

/** 班次映射 */
const SHIFT_MAP: Record<string, { text: string; color: string }> = {
  MORNING: { text: '上午', color: 'blue' },
  AFTERNOON: { text: '下午', color: 'geekblue' },
};

/** 班次可排班时间窗（小时）：号源时段起止均须落在对应班次窗口内，禁止跨越 12:00-14:00 非上班时段 */
const SHIFT_WINDOWS: Record<string, { start: number; end: number }> = {
  MORNING: { start: 8, end: 12 },
  AFTERNOON: { start: 14, end: 18 },
};

/** 排班状态映射 */
const STATUS_MAP: Record<string, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  PUBLISHED: { text: '已发布', color: 'green' },
  CANCELLED: { text: '已作废', color: 'red' },
};

/** 排班已发布时对增删改操作的统一禁用提示 */
const PUBLISHED_LOCK_TOOLTIP = '排班已发布，不可修改';

export default function ScheduleDetail() {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const { initialState } = useModel('@@initialState');
  const navigate = useNavigate();
  const [slotForm] = Form.useForm();

  const isAdmin = initialState?.currentUser?.roles?.includes('ADMIN') ?? false;
  const scheduleId = Number(id);

  // 排班元信息（由列表页跳转时携带，query 参数在刷新后保留）
  const status = searchParams.get('status') ?? '';
  const doctorName = searchParams.get('doctorName') ?? '';
  const scheduleDate = searchParams.get('scheduleDate') ?? '';
  const shift = searchParams.get('shift') ?? '';
  const totalSlots = Number(searchParams.get('totalSlots') ?? 0);

  const isPublished = status === 'PUBLISHED';
  const canEdit = isAdmin && !isPublished && status === 'DRAFT';

  /** 排班日期是否为今天（仅当天限制不可选早于当前时刻的时段） */
  const isScheduleToday = dayjs(scheduleDate).isSame(dayjs(), 'day');
  /** 当前排班班次对应的时间窗（小时），如上午 {8,12}；未知班次时为 undefined（不限制窗口） */
  const shiftWin = SHIFT_WINDOWS[shift];
  /** 时段弹窗已选的开始时间（useWatch 保证结束时间禁用项随其实时更新） */
  const startTimeValue = Form.useWatch('startTime', slotForm);

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
      disabledMinutes: (h: number) => [...(disMinByHour.get(h) ?? [])].sort((a, b) => a - b),
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
      disabledMinutes: (h: number) => [...(disMinByHour.get(h) ?? [])].sort((a, b) => a - b),
    };
  };

  const [slots, setSlots] = useState<API.SlotConfig[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editingIndex, setEditingIndex] = useState<number | null>(null);

  /** 加载时段配置 */
  const loadSlots = async () => {
    setLoading(true);
    try {
      const list = await getScheduleSlots(scheduleId);
      setSlots(list ?? []);
    } catch (err: any) {
      message.error(err?.message || '时段加载失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (scheduleId) loadSlots();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scheduleId]);

  /** 打开新增时段弹窗 */
  const handleAdd = () => {
    setEditingIndex(null);
    slotForm.resetFields();
    setEditOpen(true);
  };

  /** 打开编辑时段弹窗 */
  const handleEdit = (index: number) => {
    const slot = slots[index];
    setEditingIndex(index);
    slotForm.setFieldsValue({
      startTime: dayjs(slot.startTime, 'HH:mm'),
      endTime: dayjs(slot.endTime, 'HH:mm'),
      count: slot.totalCount,
    });
    setEditOpen(true);
  };

  /** 删除时段（仅本地，保存时统一提交） */
  const handleDelete = (index: number) => {
    setSlots((prev) => prev.filter((_, i) => i !== index));
  };

  /** 提交时段弹窗（新增/编辑） */
  const handleSlotSubmit = async () => {
    const values = await slotForm.validateFields();
    if (!values.endTime.isAfter(values.startTime)) {
      message.error('结束时间需晚于开始时间');
      return;
    }
    const item: API.SlotConfig = {
      startTime: values.startTime.format('HH:mm'),
      endTime: values.endTime.format('HH:mm'),
      totalCount: values.count,
      remainCount: values.count,
    };
    if (editingIndex == null) {
      setSlots((prev) => [...prev, item]);
    } else {
      setSlots((prev) => prev.map((s, i) => (i === editingIndex ? { ...s, ...item } : s)));
    }
    setEditOpen(false);
  };

  /** 保存全部时段配置 */
  const handleSave = async () => {
    if (slots.length === 0) {
      message.warning('请至少配置一个时段');
      return;
    }
    const configs: API.SlotConfigItem[] = slots.map((s) => ({
      startTime: s.startTime,
      endTime: s.endTime,
      count: s.totalCount,
    }));
    const sum = configs.reduce((acc, c) => acc + c.count, 0);
    if (sum > totalSlots) {
      message.error(`时段号源数合计 ${sum} 已超过排班总号源数 ${totalSlots}，请调整`);
      return;
    }
    setSaving(true);
    try {
      await configureScheduleSlots(scheduleId, configs);
      message.success('时段配置已保存');
      await loadSlots();
    } catch (err: any) {
      message.error(err?.message || '保存失败，请重试');
    } finally {
      setSaving(false);
    }
  };

  const slotSum = slots.reduce((acc, s) => acc + s.totalCount, 0);

  const columns = [
    { title: '开始时间', dataIndex: 'startTime', width: 130 },
    { title: '结束时间', dataIndex: 'endTime', width: 130 },
    {
      title: '号源数',
      dataIndex: 'totalCount',
      width: 110,
      align: 'right' as const,
    },
    {
      title: '剩余号源',
      dataIndex: 'remainCount',
      width: 110,
      align: 'right' as const,
      render: (_: unknown, record: API.SlotConfig) =>
        record.remainCount != null ? record.remainCount : '-',
    },
    {
      title: '操作',
      width: 140,
      render: (_: unknown, __: API.SlotConfig, index: number) =>
        canEdit ? (
          <Space size={0}>
            <Button type="link" size="small" onClick={() => handleEdit(index)}>
              编辑
            </Button>
            <Button type="link" size="small" danger onClick={() => handleDelete(index)}>
              删除
            </Button>
          </Space>
        ) : isPublished ? (
          <Tooltip title={PUBLISHED_LOCK_TOOLTIP}>
            <Space size={0}>
              <Button type="link" size="small" disabled>
                编辑
              </Button>
              <Button type="link" size="small" danger disabled>
                删除
              </Button>
            </Space>
          </Tooltip>
        ) : (
          <span style={{ color: '#999' }}>-</span>
        ),
    },
  ];

  return (
    <Card
      title={`排班详情（ID: ${scheduleId}）`}
      extra={
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/schedule/list')}>
          返回列表
        </Button>
      }
    >
      <Descriptions size="small" column={3} style={{ marginBottom: 16 }}>
        <Descriptions.Item label="排班日期">{scheduleDate || '-'}</Descriptions.Item>
        <Descriptions.Item label="医生">{doctorName || '-'}</Descriptions.Item>
        <Descriptions.Item label="班次">
          <Tag color={SHIFT_MAP[shift]?.color}>
            {SHIFT_MAP[shift]?.text ?? (shift || '-')}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="号源总数">{totalSlots}</Descriptions.Item>
        <Descriptions.Item label="状态">
          <Tag color={STATUS_MAP[status]?.color}>
            {STATUS_MAP[status]?.text ?? (status || '-')}
          </Tag>
        </Descriptions.Item>
      </Descriptions>

      {isPublished && (
        <Alert
          type="warning"
          showIcon
          message="该排班已发布，时段配置不可修改"
          style={{ marginBottom: 16 }}
        />
      )}

      <Space style={{ marginBottom: 16 }}>
        {canEdit ? (
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            添加时段
          </Button>
        ) : isPublished ? (
          <Tooltip title="排班已发布，不可新增时段">
            <Button type="primary" icon={<PlusOutlined />} disabled>
              添加时段
            </Button>
          </Tooltip>
        ) : null}
        <span style={{ color: '#999' }}>
          已配置 {slots.length} 个时段，合计 {slotSum} 号源，上限 {totalSlots}
          （剩余 {Math.max(0, totalSlots - slotSum)} 留作机动号源）
        </span>
      </Space>

      <Table<API.SlotConfig>
        rowKey={(_, index) => String(index)}
        loading={loading}
        columns={columns}
        dataSource={slots}
        pagination={false}
        size="middle"
        locale={{ emptyText: '尚未配置号源时段' }}
      />

      <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', marginTop: 16 }}>
        {canEdit && slotSum !== totalSlots ? (
          <span
            style={{
              color: slotSum > totalSlots ? '#ff4d4f' : '#faad14',
              fontSize: 12,
              marginRight: 12,
            }}
          >
            {slotSum > totalSlots
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
              slotSum > totalSlots
                ? { backgroundColor: '#f5f5f5', borderColor: '#d9d9d9', color: 'rgba(0, 0, 0, 0.25)' }
                : undefined
            }
            onClick={() => {
              if (slotSum > totalSlots) {
                message.error(`时段号源数总和（${slotSum}）大于排班总号源数（${totalSlots}），请调整后再保存`);
                return;
              }
              handleSave();
            }}
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

      {/* ====== 添加/编辑时段弹窗 ====== */}
      <Modal
        title={editingIndex == null ? '添加时段' : '编辑时段'}
        open={editOpen}
        onOk={handleSlotSubmit}
        onCancel={() => setEditOpen(false)}
        destroyOnClose
        width={420}
      >
        <Form form={slotForm} layout="vertical">
          {shiftWin ? (
            <div style={{ fontSize: 12, color: '#faad14', marginBottom: 12 }}>
              本排班为「{SHIFT_MAP[shift]?.text ?? shift}」班次，号源时段须在{' '}
              {String(shiftWin.start).padStart(2, '0')}:00-{String(shiftWin.end).padStart(2, '0')}:00 内配置，
              不可跨越非上班时段
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
    </Card>
  );
}

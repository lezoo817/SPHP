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
  message,
  Alert,
  Descriptions,
  Space,
  Tooltip,
  Tag,
} from 'antd';
import { PlusOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import { useNavigate, useParams, useSearchParams } from '@umijs/max';
import { useMemo, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getScheduleSlots, configureScheduleSlots } from '@/services/admin';
import { useHasRole } from '@/hooks/useCurrentUser';
import { getErrorMessage } from '@/utils/error';
import { QUERY_KEYS, STALE_TIME } from '@/constants/queryKeys';
import { getShiftConfig, getStatusConfig } from '../constants';
import { getSlotColumns } from './columns';
import SaveBar from './SaveBar';
import SlotFormModal from './SlotFormModal';

export default function ScheduleDetail() {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const isAdmin = useHasRole('ADMIN');
  const navigate = useNavigate();
  const scheduleId = Number(id);

  // 排班元信息（由列表页跳转时携带，query 参数在刷新后保留）
  const status = searchParams.get('status') ?? '';
  const doctorName = searchParams.get('doctorName') ?? '';
  const scheduleDate = searchParams.get('scheduleDate') ?? '';
  const shift = searchParams.get('shift') ?? '';
  const totalSlots = Number(searchParams.get('totalSlots') ?? 0);

  const isPublished = status === 'PUBLISHED';
  const canEdit = isAdmin && !isPublished && status === 'DRAFT';

  /** 时段接口数据：React Query 拉取；本地增删改在保存时统一提交 */
  const { data: serverSlots, isLoading, refetch } = useQuery({
    queryKey: QUERY_KEYS.scheduleSlots(scheduleId),
    queryFn: () => getScheduleSlots(scheduleId),
    staleTime: STALE_TIME.scheduleSlots,
    enabled: scheduleId > 0,
  });

  /** 时段工作副本：接口数据首次到达时初始化；增删改仅改本地，避免每次 refetch 覆盖 */
  const [slots, setSlots] = useState<API.SlotConfig[]>([]);
  const seededIdRef = useRef<number | undefined>(undefined);
  if (serverSlots && seededIdRef.current !== scheduleId) {
    seededIdRef.current = scheduleId;
    setSlots(serverSlots);
  }

  const [saving, setSaving] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editingIndex, setEditingIndex] = useState<number | null>(null);

  /** 打开新增时段弹窗 */
  const handleAdd = () => {
    setEditingIndex(null);
    setEditOpen(true);
  };

  /** 打开编辑时段弹窗 */
  const handleEdit = (index: number) => {
    setEditingIndex(index);
    setEditOpen(true);
  };

  /** 删除时段（仅本地，保存时统一提交） */
  const handleDelete = (index: number) => {
    setSlots((prev) => prev.filter((_, i) => i !== index));
  };

  /** 时段弹窗提交（新增/编辑） */
  const handleSlotSubmit = (slot: API.SlotConfig) => {
    if (editingIndex === null) {
      setSlots((prev) => [...prev, slot]);
    } else {
      setSlots((prev) => prev.map((s, i) => (i === editingIndex ? { ...s, ...slot } : s)));
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
      // 号源之和等于总号源数时已满足发布条件，直接返回列表便于继续发布
      if (sum === totalSlots) {
        navigate('/schedule/list');
        return;
      }
      // 重新拉取服务器最新时段并重建工作副本
      const fresh = await refetch();
      if (fresh.data) setSlots(fresh.data);
    } catch (err: unknown) {
      message.error(getErrorMessage(err, '保存失败，请重试'));
    } finally {
      setSaving(false);
    }
  };

  /** 时段号源总数（派生，用于发布条件提示与上限校验） */
  const slotSum = useMemo(() => slots.reduce((acc, s) => acc + s.totalCount, 0), [slots]);

  /** 当前编辑的时段（null=新增） */
  const editingSlot = useMemo(
    () => (editingIndex === null ? null : (slots[editingIndex] ?? null)),
    [editingIndex, slots],
  );

  const columns = getSlotColumns({
    canEdit,
    isPublished,
    onEdit: handleEdit,
    onDelete: handleDelete,
  });

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
          <Tag color={getShiftConfig(shift)?.color}>
            {getShiftConfig(shift)?.text ?? (shift || '-')}
          </Tag>
        </Descriptions.Item>
        <Descriptions.Item label="号源总数">{totalSlots}</Descriptions.Item>
        <Descriptions.Item label="状态">
          <Tag color={getStatusConfig(status)?.color}>
            {getStatusConfig(status)?.text ?? (status || '-')}
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
        rowKey={(record, index) => (record.id ? `slot-${record.id}` : `draft-${index}`)}
        loading={isLoading}
        columns={columns}
        dataSource={slots}
        pagination={false}
        size="middle"
        locale={{ emptyText: '尚未配置号源时段' }}
      />

      <SaveBar
        canEdit={canEdit}
        isPublished={isPublished}
        slotSum={slotSum}
        totalSlots={totalSlots}
        saving={saving}
        onSave={handleSave}
      />

      <SlotFormModal
        open={editOpen}
        editingSlot={editingSlot}
        shift={shift}
        scheduleDate={scheduleDate}
        totalSlots={totalSlots}
        onCancel={() => setEditOpen(false)}
        onSubmit={handleSlotSubmit}
      />
    </Card>
  );
}

/**
 * 锁定号源看板
 * - 按日期（必填）+ 科室过滤；日期变更自动刷新
 * - 倒计时展示锁定剩余时间（expireAt = lockedAt + 15 分钟），每秒更新
 * - “手动释放”按钮仅 ADMIN 展示，释放前二次确认
 */
import { Button, Tag, message, DatePicker, Select, Modal } from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { useMemo, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getLockedSlots, forceReleaseSlot, getDepartments } from '@/services/admin';
import { useHasRole } from '@/hooks/useCurrentUser';
import { getErrorMessage } from '@/utils/error';
import { QUERY_KEYS, STALE_TIME } from '@/constants/queryKeys';
import useCountdown from '@/hooks/useCountdown';
import dayjs from 'dayjs';

/** 锁定剩余时间倒计时组件 */
function RemainCountdown({ expireAt }: { expireAt?: string }) {
  const targetMs = expireAt ? new Date(expireAt).getTime() : 0;
  const remainMs = useCountdown(targetMs);
  const totalSeconds = Math.max(0, Math.ceil(remainMs / 1000));
  const expired = totalSeconds <= 0;
  const mm = String(Math.floor(totalSeconds / 60)).padStart(2, '0');
  const ss = String(totalSeconds % 60).padStart(2, '0');
  return (
    <span
      style={{
        color: expired ? '#ff4d4f' : '#1677ff',
        fontVariantNumeric: 'tabular-nums',
      }}
    >
      {expired ? '已过期' : `${mm}:${ss}`}
    </span>
  );
}

export default function LockedSlotsBoard() {
  const isAdmin = useHasRole('ADMIN');
  const actionRef = useRef<ActionType>();

  const [query, setQuery] = useState<{ date: string; deptId?: number }>({
    date: dayjs().format('YYYY-MM-DD'),
  });

  /** 科室选项：React Query 缓存，仅 ADMIN 拉取（筛选按钮仅 ADMIN 展示） */
  const { data: deptResult } = useQuery({
    queryKey: QUERY_KEYS.departments,
    queryFn: () => getDepartments({ page: 1, size: 200 }),
    enabled: isAdmin,
    staleTime: STALE_TIME.departments,
  });
  const deptOptions = useMemo(
    () => (deptResult?.list ?? []).map((d) => ({ label: d.name, value: d.id })),
    [deptResult],
  );

  /** 手动释放锁定号源（仅 ADMIN） */
  const handleForceRelease = (record: API.LockedSlot) => {
    Modal.confirm({
      title: '手动释放锁定号源',
      content: `确定手动释放「${record.patientName}」在 ${record.doctorName} 锁定的号源吗？释放后该号源将重新开放预约。`,
      okText: '确认释放',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await forceReleaseSlot(record.slotId);
          message.success('号源已释放');
          actionRef.current?.reload();
        } catch (err: unknown) {
          message.error(getErrorMessage(err, '释放失败'));
        }
      },
    });
  };

  const columns: ProColumns<API.LockedSlot>[] = [
    {
      title: '就诊人',
      dataIndex: 'patientName',
      width: 110,
      ellipsis: true,
    },
    {
      title: '医生',
      dataIndex: 'doctorName',
      width: 110,
      ellipsis: true,
    },
    {
      title: '锁定时间',
      dataIndex: 'lockedAt',
      width: 170,
      render: (_, record) =>
        record.lockedAt ? dayjs(record.lockedAt).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '剩余时间',
      dataIndex: 'expireAt',
      width: 110,
      render: (_, record) => <RemainCountdown expireAt={record.expireAt} />,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: () => <Tag color="orange">锁定中</Tag>,
    },
  ];

  if (isAdmin) {
    columns.push({
      title: '操作',
      dataIndex: 'operation',
      width: 110,
      render: (_, record) => (
        <Button type="link" size="small" danger onClick={() => handleForceRelease(record)}>
          手动释放
        </Button>
      ),
    });
  }

  return (
    <ProTable<API.LockedSlot, { date: string; deptId?: number }>
      actionRef={actionRef}
      rowKey="slotId"
      columns={columns}
      params={query}
      request={async (params) => {
        const { current, pageSize, date, deptId } = params;
        try {
          const res = await getLockedSlots({
            date,
            deptId,
            page: current,
            size: pageSize,
          });
          return { data: res.list, total: res.total, success: true };
        } catch (err: unknown) {
          message.error(getErrorMessage(err, '查询失败，请重试'));
          return { data: [], total: 0, success: true };
        }
      }}
      search={false}
      options={{ reload: true }}
      toolBarRender={() => [
        <DatePicker
          key="date"
          value={dayjs(query.date)}
          allowClear={false}
          style={{ width: 140 }}
          onChange={(d) => d && setQuery((prev) => ({ ...prev, date: d.format('YYYY-MM-DD') }))}
        />,
        isAdmin ? (
          <Select
            key="dept"
            value={query.deptId}
            allowClear
            placeholder="全部科室"
            style={{ width: 160 }}
            options={deptOptions}
            onChange={(v) => setQuery((prev) => ({ ...prev, deptId: v }))}
          />
        ) : null,
      ]}
      pagination={{ pageSize: 10, showSizeChanger: true }}
    />
  );
}

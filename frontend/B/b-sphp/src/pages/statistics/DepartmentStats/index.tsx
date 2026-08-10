/**
 * 按科室统计页
 * - 日期范围筛选 + 可选科室过滤
 * - ProTable 展示各科室挂号量/接诊量/处方量/号源利用率
 */
import { Tag, message, DatePicker, Select } from 'antd';
import { ProTable } from '@ant-design/pro-components';
import { useMemo, useRef, useState } from 'react';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import { getDepartmentStats, getDepartments } from '@/services/admin';
import { getErrorMessage } from '@/utils/error';
import { QUERY_KEYS, STALE_TIME } from '@/constants/queryKeys';
import dayjs from 'dayjs';
import { PAGE_SIZE_200, PAGE_SIZE_DEFAULT } from '@/constants/pageSize';

const { RangePicker } = DatePicker;

/** 百分比格式化 */
function formatPercent(rate: number): string {
  return `${(rate * 100).toFixed(1)}%`;
}

export default function DepartmentStats() {
  const actionRef = useRef<ActionType>();
  const [dates, setDates] = useState<[dayjs.Dayjs, dayjs.Dayjs]>([
    dayjs().startOf('month'),
    dayjs(),
  ]);
  const [deptId, setDeptId] = useState<number | undefined>(undefined);

  /** 科室选项（React Query 缓存 5min） */
  const { data: deptRes } = useQuery({
    queryKey: QUERY_KEYS.departments,
    queryFn: () => getDepartments({ page: 1, size: PAGE_SIZE_200 }),
    staleTime: STALE_TIME.departments,
  });
  const deptOptions = useMemo(
    () => (deptRes?.list ?? []).map((d) => ({ label: d.name, value: d.id })),
    [deptRes],
  );

  const columns: ProColumns<API.DepartmentStatItem>[] = [
    {
      title: '科室名称',
      dataIndex: 'deptName',
      width: 180,
      ellipsis: true,
    },
    {
      title: '挂号量',
      dataIndex: 'appointmentCount',
      width: 100,
      sorter: true,
    },
    {
      title: '接诊量',
      dataIndex: 'consultCount',
      width: 100,
      sorter: true,
    },
    {
      title: '处方量',
      dataIndex: 'prescriptionCount',
      width: 100,
      sorter: true,
    },
    {
      title: '号源利用率',
      dataIndex: 'slotUsageRate',
      width: 120,
      sorter: true,
      render: (_, record) => {
        const rate = record.slotUsageRate;
        const color = rate >= 0.8 ? 'green' : rate >= 0.5 ? 'orange' : 'red';
        return <Tag color={color}>{formatPercent(rate)}</Tag>;
      },
    },
  ];

  return (
    <ProTable<API.DepartmentStatItem>
      actionRef={actionRef}
      rowKey="deptId"
      columns={columns}
      params={{ dates, deptId }}
      request={async () => {
        try {
          const list = await getDepartmentStats({
            startDate: dates[0].format('YYYY-MM-DD'),
            endDate: dates[1].format('YYYY-MM-DD'),
            deptId,
          });
          return { data: list, total: list.length, success: true };
        } catch (err: unknown) {
          message.error(getErrorMessage(err, '查询科室统计失败'));
          return { data: [], total: 0, success: true };
        }
      }}
      search={false}
      options={{ reload: true }}
      toolBarRender={() => [
        <RangePicker
          key="date"
          value={dates}
          onChange={(v) => {
            if (v && v[0] && v[1]) setDates([v[0], v[1]]);
          }}
          allowClear={false}
          style={{ width: 240 }}
        />,
        <Select
          key="dept"
          value={deptId}
          allowClear
          placeholder="全部科室"
          style={{ width: 160 }}
          options={deptOptions}
          onChange={(v) => setDeptId(v)}
        />,
      ]}
      pagination={{ pageSize: PAGE_SIZE_DEFAULT }}
    />
  );
}
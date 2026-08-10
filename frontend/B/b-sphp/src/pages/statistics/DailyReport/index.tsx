/**
 * 按日期统计页
 * - 日期范围筛选（必填）
 * - ProTable 展示每日挂号量/接诊量/处方量/收入
 */
import { message, DatePicker } from 'antd';
import { ProTable } from '@ant-design/pro-components';
import { useRef, useState } from 'react';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { getDailyStats } from '@/services/admin';
import { getErrorMessage } from '@/utils/error';
import dayjs from 'dayjs';
import { PAGE_SIZE_DEFAULT } from '@/constants/pageSize';

const { RangePicker } = DatePicker;

/** 分转元 */
function formatYuan(cent: number): string {
  return (cent / 100).toFixed(2);
}

export default function DailyReport() {
  const actionRef = useRef<ActionType>();
  const [dates, setDates] = useState<[dayjs.Dayjs, dayjs.Dayjs]>([
    dayjs().startOf('month'),
    dayjs(),
  ]);

  const columns: ProColumns<API.DailyStatItem>[] = [
    {
      title: '日期',
      dataIndex: 'date',
      width: 140,
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
      title: '收入',
      dataIndex: 'revenueCent',
      width: 120,
      sorter: true,
      render: (_, record) => `¥${formatYuan(record.revenueCent)}`,
    },
  ];

  return (
    <ProTable<API.DailyStatItem>
      actionRef={actionRef}
      rowKey="date"
      columns={columns}
      params={{ dates }}
      request={async () => {
        try {
          const list = await getDailyStats({
            startDate: dates[0].format('YYYY-MM-DD'),
            endDate: dates[1].format('YYYY-MM-DD'),
          });
          return { data: list, total: list.length, success: true };
        } catch (err: unknown) {
          message.error(getErrorMessage(err, '查询日报统计失败'));
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
      ]}
      pagination={{ pageSize: PAGE_SIZE_DEFAULT }}
    />
  );
}
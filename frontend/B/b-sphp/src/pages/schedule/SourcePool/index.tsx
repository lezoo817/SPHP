/**
 * 号源池页面
 * - 按已发布排班明细展示：日期/班次/科室（诊室）/医生/总号源数/剩余/已约/锁定
 * - 默认以今天为中心前后各 3 天（总窗口 7 天）；排序：今天优先 → 日期倒序 → 班次（MORNING→AFTERNOON）→ id
 * - 过期排班（PUBLISHED 且 schedule_date < today）在日期列后挂灰色"过期"Tag
 * - 三角色可访问，数据范围由后端 DataScope 过滤（ADMIN 全院 / DEPT_HEAD 本科室 / DOCTOR 本人）
 * - 科室/医生筛选仅 ADMIN 生效；本页只读，无操作列
 */
import { Space, Tag, message } from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import { getSourcePool, getDepartments, getDoctors } from '@/services/admin';
import { useHasRole } from '@/hooks/useCurrentUser';
import { getErrorMessage } from '@/utils/error';
import { getShiftConfig } from '../constants';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import { PAGE_SIZE_100, PAGE_SIZE_200, PAGE_SIZE_DEFAULT } from '@/constants/pageSize';
import { ROLE_ADMIN, STATUS_ENABLED } from '@/constants/businessStatus';

export default function SourcePool() {
  const isAdmin = useHasRole(ROLE_ADMIN);

  /** 日期参数归一化：ProTable 可能传入 dayjs 或字符串 */
  const toDateParam = (v: unknown): string | undefined => {
    if (!v) return undefined;
    if (typeof v === 'string') return v;
    return (v as Dayjs).format('YYYY-MM-DD');
  };

  /** 科室选项（供筛选，仅 ADMIN） */
  const fetchDepartments = async () => {
    try {
      const res = await getDepartments({ page: 1, size: PAGE_SIZE_200 });
      return (res.list ?? []).map((dept) => ({ label: dept.name, value: dept.id }));
    } catch {
      return [];
    }
  };

  /** 医生选项（供筛选，仅 ADMIN） */
  const fetchDoctors = async (keyword?: string) => {
    try {
      const res = await getDoctors({
        name: keyword || undefined,
        status: STATUS_ENABLED,
        page: 1,
        size: PAGE_SIZE_100,
      });
      return (res.list ?? []).map((doc) => ({ label: doc.name, value: doc.id }));
    } catch {
      return [];
    }
  };

  const columns: ProColumns<API.SourcePoolVO>[] = [
    // 日期区间搜索项（默认近 7 天含今天），拆分后传给后端 startDate/endDate
    {
      title: '日期区间',
      dataIndex: 'dateRange',
      valueType: 'dateRange',
      hideInTable: true,
      initialValue: [dayjs().subtract(3, 'day'), dayjs().add(3, 'day')],
    },
    {
      title: '日期',
      dataIndex: 'scheduleDate',
      width: 150,
      hideInSearch: true,
      render: (_, record) => (
        <Space size={4}>
          <span>{dayjs(record.scheduleDate).format('YYYY-MM-DD')}</span>
          {record.isExpired && <Tag color="default">过期</Tag>}
        </Space>
      ),
    },
    {
      title: '班次',
      dataIndex: 'shift',
      width: 80,
      hideInSearch: true,
      render: (_, record) => {
        const cfg = getShiftConfig(record.shift);
        return <Tag color={cfg?.color}>{cfg?.text ?? record.shift}</Tag>;
      },
    },
    {
      title: '科室（诊室）',
      dataIndex: 'deptName',
      width: 120,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '医生',
      dataIndex: 'doctorName',
      width: 110,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '总号源数',
      dataIndex: 'totalSlots',
      width: 90,
      align: 'right',
      hideInSearch: true,
    },
    {
      title: '剩余号源数',
      dataIndex: 'remainSlots',
      width: 100,
      align: 'right',
      hideInSearch: true,
      render: (_, record) =>
        record.remainSlots <= 0 ? (
          <span style={{ color: '#ff4d4f' }}>{record.remainSlots}</span>
        ) : (
          record.remainSlots
        ),
    },
    {
      title: '已约数',
      dataIndex: 'soldSlots',
      width: 80,
      align: 'right',
      hideInSearch: true,
    },
    {
      title: '锁定数',
      dataIndex: 'lockedSlots',
      width: 80,
      align: 'right',
      hideInSearch: true,
    },
  ];

  // 科室/医生筛选仅 ADMIN 生效（后端对非 ADMIN 忽略该过滤）
  if (isAdmin) {
    columns.splice(1, 0, {
      title: '科室',
      dataIndex: 'deptId',
      valueType: 'select',
      hideInTable: true,
      request: fetchDepartments,
      fieldProps: { showSearch: true, allowClear: true, placeholder: '请选择科室' },
    });
    columns.splice(2, 0, {
      title: '医生',
      dataIndex: 'doctorId',
      valueType: 'select',
      hideInTable: true,
      request: fetchDoctors,
      fieldProps: { showSearch: true, allowClear: true, placeholder: '请选择医生' },
    });
  }

  return (
    <ProTable<API.SourcePoolVO, API.SourcePoolParams & { dateRange?: [Dayjs, Dayjs] | [string, string] }>
      rowKey={(record) => record.scheduleId}
      columns={columns}
      request={async (params) => {
        const { current, pageSize, ...rest } = params;
        try {
          const [start, end] = rest.dateRange ?? [];
          const res = await getSourcePool({
            page: current,
            size: pageSize,
            startDate: toDateParam(start),
            endDate: toDateParam(end),
            deptId: rest.deptId,
            doctorId: rest.doctorId,
          });
          return { data: res.list, total: res.total, success: true };
        } catch (err: unknown) {
          message.error(getErrorMessage(err, '查询失败，请重试'));
          return { data: [], total: 0, success: true };
        }
      }}
      search={{
        labelWidth: 'auto',
        span: 6,
        defaultFormItemsNumber: 4,
      }}
      pagination={{ pageSize: PAGE_SIZE_DEFAULT, showSizeChanger: true }}
    />
  );
}

/**
 * 号源池页面
 * - 按已发布排班明细展示：日期/班次/科室（诊室）/医生/总号源数/剩余/已约/锁定，日期倒序
 * - 三角色可访问，数据范围由后端 DataScope 过滤（ADMIN 全院 / DEPT_HEAD 本科室 / DOCTOR 本人）
 * - 科室/医生筛选仅 ADMIN 生效；本页只读，无操作列
 */
import { Tag, message } from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import { useModel } from '@umijs/max';
import { getSourcePool, getDepartments, getDoctors } from '@/services/admin';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';

/** 班次映射 */
const SHIFT_MAP: Record<string, { text: string; color: string }> = {
  MORNING: { text: '上午', color: 'blue' },
  AFTERNOON: { text: '下午', color: 'geekblue' },
};

export default function SourcePool() {
  const { initialState } = useModel('@@initialState');
  const isAdmin = initialState?.currentUser?.roles?.includes('ADMIN') ?? false;

  /** 日期参数归一化：ProTable 可能传入 dayjs 或字符串 */
  const toDateParam = (v: unknown): string | undefined => {
    if (!v) return undefined;
    if (typeof v === 'string') return v;
    return (v as Dayjs).format('YYYY-MM-DD');
  };

  /** 科室选项（供筛选，仅 ADMIN） */
  const fetchDepartments = async () => {
    try {
      const res = await getDepartments({ page: 1, size: 200 });
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
        status: 'ENABLED',
        page: 1,
        size: 100,
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
      initialValue: [dayjs().subtract(6, 'day'), dayjs()],
    },
    {
      title: '日期',
      dataIndex: 'scheduleDate',
      width: 110,
      hideInSearch: true,
    },
    {
      title: '班次',
      dataIndex: 'shift',
      width: 80,
      hideInSearch: true,
      render: (_, record) => (
        <Tag color={SHIFT_MAP[record.shift]?.color}>{SHIFT_MAP[record.shift]?.text ?? record.shift}</Tag>
      ),
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
        } catch (err: any) {
          message.error(err?.message || '查询失败，请重试');
          return { data: [], total: 0, success: true };
        }
      }}
      search={{
        labelWidth: 'auto',
        span: 6,
        defaultFormItemsNumber: 4,
      }}
      pagination={{ pageSize: 10, showSizeChanger: true }}
    />
  );
}

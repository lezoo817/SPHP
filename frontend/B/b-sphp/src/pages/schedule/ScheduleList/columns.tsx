/**
 * 排班列表页列配置。
 *
 * 通过 getColumns(deps) 工厂生成：操作列依赖 ADMIN 角色与发布/取消发布/作废/详情回调；
 * 科室/医生筛选仅 ADMIN 生效（后端对非 ADMIN 忽略该过滤）。
 */
import { Tag, Button, Tooltip, Space, Select } from 'antd';
import type { ProColumns } from '@ant-design/pro-components';
import { getDepartments, getDoctors } from '@/services/admin';
import {
  STATUS_OPTIONS,
  PUBLISHED_LOCK_TOOLTIP,
  getShiftConfig,
  getStatusConfig,
  isScheduleExpired,
} from '../constants';

interface ColumnsDeps {
  isAdmin: boolean;
  onGoDetail: (record: API.Schedule) => void;
  onPublish: (record: API.Schedule) => void;
  onUnpublish: (record: API.Schedule) => void;
  onCancel: (record: API.Schedule) => void;
}

/** 科室选项（供筛选，仅 ADMIN） */
async function fetchDepartments() {
  try {
    const res = await getDepartments({ page: 1, size: 200 });
    return (res.list ?? []).map((dept) => ({ label: dept.name, value: dept.id }));
  } catch {
    return [];
  }
}

/** 医生选项（供筛选，仅 ADMIN，仅启用医生） */
async function fetchDoctors(keyword?: string) {
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
}

export function getColumns(deps: ColumnsDeps): ProColumns<API.Schedule>[] {
  const { isAdmin, onGoDetail, onPublish, onUnpublish, onCancel } = deps;

  const columns: ProColumns<API.Schedule>[] = [
    {
      title: '排班日期',
      dataIndex: 'scheduleDate',
      width: 110,
      hideInSearch: true,
    },
    // 隐藏的日期搜索项：dataIndex 需与后端参数一致为 date
    {
      title: '日期',
      dataIndex: 'date',
      valueType: 'date',
      hideInTable: true,
    },
    {
      title: '医生',
      dataIndex: 'doctorName',
      width: 110,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '科室',
      dataIndex: 'deptName',
      width: 120,
      ellipsis: true,
      hideInSearch: true,
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
      title: '号源',
      dataIndex: 'totalSlots',
      width: 70,
      align: 'right',
      hideInSearch: true,
    },
    {
      title: '已约',
      dataIndex: 'bookedCount',
      width: 70,
      align: 'right',
      hideInSearch: true,
    },
    {
      title: '剩余',
      dataIndex: 'remainCount',
      width: 70,
      align: 'right',
      hideInSearch: true,
      render: (_, record) =>
        record.remainCount <= 0 ? (
          <span style={{ color: '#ff4d4f' }}>{record.remainCount}</span>
        ) : (
          record.remainCount
        ),
    },
    {
      title: '锁定',
      dataIndex: 'lockedCount',
      width: 70,
      align: 'right',
      hideInSearch: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      valueEnum: {
        DRAFT: { text: '草稿', status: 'Default' },
        PUBLISHED: { text: '已发布', status: 'Success' },
        CANCELLED: { text: '已作废', status: 'Error' },
      },
      renderFormItem: () => (
        <Select allowClear placeholder="全部" options={STATUS_OPTIONS} />
      ),
      render: (_, record) => {
        if (isScheduleExpired(record)) {
          return <Tag color="default">已过期</Tag>;
        }
        const cfg = getStatusConfig(record.status);
        return <Tag color={cfg?.color}>{cfg?.text ?? record.status}</Tag>;
      },
    },
    {
      title: '操作',
      width: 280,
      hideInSearch: true,
      render: (_, record) => {
        const published = record.status === 'PUBLISHED';
        const draft = record.status === 'DRAFT';
        return (
          <Space size={0} wrap>
            <Button type="link" size="small" onClick={() => onGoDetail(record)}>
              详情
            </Button>
            {draft && isAdmin && (
              <Button type="link" size="small" onClick={() => onGoDetail(record)}>
                配置时段
              </Button>
            )}
            {published && isAdmin && (
              <Tooltip title={PUBLISHED_LOCK_TOOLTIP}>
                <Button type="link" size="small" disabled>
                  配置时段
                </Button>
              </Tooltip>
            )}
            {draft && isAdmin && (
              <Tooltip
                title={
                  record.remainCount !== record.totalSlots
                    ? '时段号源数之和须等于总号源数方可发布'
                    : undefined
                }
              >
                <span>
                  <Button
                    type="link"
                    size="small"
                    disabled={record.remainCount !== record.totalSlots}
                    onClick={() => onPublish(record)}
                  >
                    发布
                  </Button>
                </span>
              </Tooltip>
            )}
            {draft && isAdmin && (
              <Button type="link" size="small" danger onClick={() => onCancel(record)}>
                作废
              </Button>
            )}
            {published && isAdmin && !isScheduleExpired(record) && (
              <Button type="link" size="small" danger onClick={() => onUnpublish(record)}>
                取消发布
              </Button>
            )}
            {published && isAdmin && isScheduleExpired(record) && (
              <Tooltip title="排班已过期，不可取消发布">
                <Button type="link" size="small" disabled>
                  取消发布
                </Button>
              </Tooltip>
            )}
          </Space>
        );
      },
    },
  ];

  // 科室/医生筛选仅 ADMIN 生效（后端对非 ADMIN 忽略该过滤）
  if (isAdmin) {
    columns.splice(2, 0, {
      title: '科室',
      dataIndex: 'deptId',
      valueType: 'select',
      hideInTable: true,
      request: fetchDepartments,
      fieldProps: { showSearch: true, allowClear: true, placeholder: '请选择科室' },
    });
    columns.splice(3, 0, {
      title: '医生',
      dataIndex: 'doctorId',
      valueType: 'select',
      hideInTable: true,
      request: () => fetchDoctors(),
      fieldProps: { showSearch: true, allowClear: true, placeholder: '请选择医生' },
    });
  }

  return columns;
}

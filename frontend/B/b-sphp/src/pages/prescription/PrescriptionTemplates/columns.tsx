/**
 * 处方模板列表列配置。
 *
 * 通过 getColumns(deps) 工厂生成：科室选项来自父组件（React Query），
 * 操作列依赖查看/删除回调；搜索项（模板名称/科室）置于列表首部且隐藏于表格。
 */
import { Button, Space, Popconfirm } from 'antd';
import { EyeOutlined, DeleteOutlined, EditOutlined } from '@ant-design/icons';
import type { ProColumns } from '@ant-design/pro-components';
import dayjs from 'dayjs';

interface ColumnsDeps {
  deptOptions: { label: string; value: number }[];
  /** 是否显示科室搜索筛选（仅 ADMIN 可见；DEPT_HEAD/DOCTOR 后端强制本科室+全院通用，筛选无意义） */
  showDeptFilter: boolean;
  /** 当前用户是否可管理该模板（编辑/删除按钮显隐：ADMIN 全部；DEPT_HEAD 本科室+全院通用；DOCTOR 本科室） */
  canManageRecord: (record: API.PrescriptionTemplate) => boolean;
  onViewDetail: (record: API.PrescriptionTemplate) => void;
  onEdit: (record: API.PrescriptionTemplate) => void;
  onDelete: (id: number) => void;
}

export function getColumns(deps: ColumnsDeps): ProColumns<API.PrescriptionTemplate>[] {
  const { deptOptions, showDeptFilter, canManageRecord, onViewDetail, onEdit, onDelete } = deps;

  // 科室搜索筛选（仅 ADMIN 可见）：DEPT_HEAD/DOCTOR 后端强制本科室+全院通用，筛选无意义
  const deptSearchColumn: ProColumns<API.PrescriptionTemplate> = {
    title: '科室',
    dataIndex: 'deptId',
    valueType: 'select',
    hideInTable: true,
    fieldProps: { allowClear: true, placeholder: '全部科室', options: deptOptions },
  };

  return [
    // 搜索项：模板名称、科室（隐藏于表格，仅用于筛选）
    {
      title: '模板名称',
      dataIndex: 'name',
      valueType: 'text',
      hideInTable: true,
      fieldProps: { placeholder: '输入模板名称搜索' },
    },
    ...(showDeptFilter ? [deptSearchColumn] : []),
    {
      title: '模板名称',
      dataIndex: 'name',
      width: 160,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '科室',
      dataIndex: 'deptName',
      width: 120,
      ellipsis: true,
      hideInSearch: true,
      render: (_: unknown, record: API.PrescriptionTemplate) => record.deptName ?? '全院通用',
    },
    {
      title: '更新人',
      dataIndex: 'updatedByName',
      width: 100,
      hideInSearch: true,
    },
    {
      title: '药品数',
      dataIndex: 'itemCount',
      width: 70,
      align: 'right',
      hideInSearch: true,
      render: (_: unknown, record: API.PrescriptionTemplate) => `${record.itemCount} 项`,
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      width: 160,
      hideInSearch: true,
      render: (_: unknown, record: API.PrescriptionTemplate) =>
        record.updatedAt ? dayjs(record.updatedAt).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: '操作',
      width: 160,
      hideInSearch: true,
      render: (_: unknown, record: API.PrescriptionTemplate) => {
        // 仅可管理范围内的模板显示编辑/删除（ADMIN 全部；DEPT_HEAD 本科室+全院通用；DOCTOR 仅本科室）
        const manageable = canManageRecord(record);
        return (
          <Space size={0}>
            <Button
              type="link"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => onViewDetail(record)}
            >
              查看
            </Button>
            {manageable && (
              <Button
                type="link"
                size="small"
                icon={<EditOutlined />}
                onClick={() => onEdit(record)}
              >
                编辑
              </Button>
            )}
            {manageable && (
              <Popconfirm
                title="确认删除"
                description="删除后不可恢复，确定删除该模板吗？"
                onConfirm={() => onDelete(record.id)}
                okText="确认删除"
                cancelText="取消"
              >
                <Button type="link" size="small" danger icon={<DeleteOutlined />}>
                  删除
                </Button>
              </Popconfirm>
            )}
          </Space>
        );
      },
    },
  ];
}

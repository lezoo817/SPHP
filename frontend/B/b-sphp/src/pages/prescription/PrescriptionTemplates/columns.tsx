/**
 * 处方模板列表列配置。
 *
 * 通过 getColumns(deps) 工厂生成：科室选项来自父组件（React Query），
 * 操作列依赖查看/删除回调；搜索项（模板名称/科室）置于列表首部且隐藏于表格。
 */
import { Button, Space, Popconfirm } from 'antd';
import { EyeOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ProColumns } from '@ant-design/pro-components';
import dayjs from 'dayjs';

interface ColumnsDeps {
  deptOptions: { label: string; value: number }[];
  onViewDetail: (record: API.PrescriptionTemplate) => void;
  onDelete: (id: number) => void;
}

export function getColumns(deps: ColumnsDeps): ProColumns<API.PrescriptionTemplate>[] {
  const { deptOptions, onViewDetail, onDelete } = deps;

  return [
    // 搜索项：模板名称、科室（隐藏于表格，仅用于筛选）
    {
      title: '模板名称',
      dataIndex: 'name',
      valueType: 'text',
      hideInTable: true,
      fieldProps: { placeholder: '输入模板名称搜索' },
    },
    {
      title: '科室',
      dataIndex: 'deptId',
      valueType: 'select',
      hideInTable: true,
      fieldProps: { allowClear: true, placeholder: '全部科室', options: deptOptions },
    },
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
      title: '创建人',
      dataIndex: 'doctorName',
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
      title: '创建时间',
      dataIndex: 'createdAt',
      width: 160,
      hideInSearch: true,
      render: (_: unknown, record: API.PrescriptionTemplate) =>
        record.createdAt ? dayjs(record.createdAt).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: '操作',
      width: 160,
      hideInSearch: true,
      render: (_: unknown, record: API.PrescriptionTemplate) => (
        <Space size={0}>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => onViewDetail(record)}
          >
            查看
          </Button>
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
        </Space>
      ),
    },
  ];
}

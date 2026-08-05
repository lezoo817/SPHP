/**
 * 药品目录页列配置。
 *
 * 通过 getColumns(deps) 工厂生成：操作列依赖 ADMIN 角色与编辑/切换状态回调，
 * 权限或回调变化时重建列配置，避免在组件内闭包捕获过期状态。
 */
import { Button, Badge, Switch, Select } from 'antd';
import type { ProColumns } from '@ant-design/pro-components';

interface ColumnsDeps {
  isAdmin: boolean;
  onEdit: (record: API.Drug) => void;
  onToggleStatus: (record: API.Drug) => void;
}

export function getColumns(deps: ColumnsDeps): ProColumns<API.Drug>[] {
  const { isAdmin, onEdit, onToggleStatus } = deps;

  return [
    { title: '药品名称', dataIndex: 'name', ellipsis: true, width: 200 },
    { title: '规格', dataIndex: 'specification', width: 140, hideInSearch: true },
    { title: '单位', dataIndex: 'unit', width: 80, hideInSearch: true },
    {
      title: '生产厂家',
      dataIndex: 'manufacturer',
      width: 180,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '批准文号',
      dataIndex: 'approvalNumber',
      width: 170,
      ellipsis: true,
      hideInSearch: true,
    },
    { title: '适应症', dataIndex: 'indication', ellipsis: true, hideInSearch: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      valueEnum: {
        ENABLED: { text: '启用', status: 'Success' },
        DISABLED: { text: '停用', status: 'Error' },
      },
      renderFormItem: () => (
        <Select
          allowClear
          placeholder="全部"
          options={[
            { label: '启用', value: 'ENABLED' },
            { label: '停用', value: 'DISABLED' },
          ]}
        />
      ),
      render: (_, record) => (
        <Badge
          status={record.status === 'ENABLED' ? 'success' : 'error'}
          text={record.status === 'ENABLED' ? '启用' : '停用'}
        />
      ),
    },
    {
      title: '操作',
      width: 180,
      hideInSearch: true,
      render: (_, record) => (
        <>
          {isAdmin && (
            <Button type="link" size="small" onClick={() => onEdit(record)}>
              编辑
            </Button>
          )}
          {isAdmin && (
            <Switch
              checked={record.status === 'ENABLED'}
              checkedChildren="启用"
              unCheckedChildren="停用"
              size="small"
              style={{ marginLeft: 8 }}
              onChange={() => onToggleStatus(record)}
            />
          )}
        </>
      ),
    },
  ];
}

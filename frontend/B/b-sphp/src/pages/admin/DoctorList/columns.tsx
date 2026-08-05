/**
 * 医生列表列配置。
 *
 * 列定义与操作菜单依赖页面状态（角色、操作回调），故以 getColumns(deps) 工厂方式导出，
 * 由页面组件调用生成；依赖变化时由调用方重新生成，无需 useMemo（与 ProTable 使用习惯一致）。
 */
import { Tag, Button, Dropdown, Space } from 'antd';
import {
  MoreOutlined,
  EditOutlined,
  KeyOutlined,
  UserOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import type { MenuProps } from 'antd';
import { ProFormSelect } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import { STATUS_MAP, fetchDepartmentOptions } from './constants';

/** getColumns 依赖的操作回调与角色信息。 */
export interface ColumnsDeps {
  isAdmin: boolean;
  isDeptHead: boolean;
  onEditProfile: (record: API.Doctor) => void;
  onEditAccount: (record: API.Doctor) => void;
  onResetPwd: (record: API.Doctor) => void;
  onChangeStatus: (record: API.Doctor) => void;
}

/** 生成操作列 Dropdown 菜单（按角色裁剪）。 */
function buildActionMenu(
  deps: ColumnsDeps,
  record: API.Doctor,
): MenuProps['items'] {
  const items: MenuProps['items'] = [];

  // ADMIN：编辑资料 / 修改账号 / 重置密码 / 切换状态
  if (deps.isAdmin) {
    items.push(
      {
        key: 'editProfile',
        icon: <EditOutlined />,
        label: '编辑资料',
        onClick: () => deps.onEditProfile(record),
      },
      {
        key: 'editAccount',
        icon: <UserOutlined />,
        label: '修改账号',
        onClick: () => deps.onEditAccount(record),
      },
      {
        key: 'resetPwd',
        icon: <KeyOutlined />,
        label: '重置密码',
        onClick: () => deps.onResetPwd(record),
      },
      { type: 'divider' },
      {
        key: 'changeStatus',
        icon: <SwapOutlined />,
        label: '切换状态',
        onClick: () => deps.onChangeStatus(record),
      },
    );
  } else if (deps.isDeptHead) {
    // DEPT_HEAD：仅编辑资料
    items.push({
      key: 'editProfile',
      icon: <EditOutlined />,
      label: '编辑资料',
      onClick: () => deps.onEditProfile(record),
    });
  }

  return items.length > 0 ? items : undefined;
}

/** 医生列表列配置。 */
export function getColumns(deps: ColumnsDeps): ProColumns<API.Doctor>[] {
  return [
    {
      title: '姓名',
      dataIndex: 'name',
      width: 100,
      ellipsis: true,
    },
    {
      title: '科室',
      dataIndex: 'deptName',
      width: 120,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '所属科室',
      dataIndex: 'deptId',
      hideInTable: true,
      hideInSearch: false,
      renderFormItem: () => (
        <ProFormSelect
          name="deptId"
          noStyle
          request={fetchDepartmentOptions}
          placeholder="请选择科室"
          allowClear
        />
      ),
    },
    {
      title: '职称',
      dataIndex: 'title',
      width: 100,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '专长',
      dataIndex: 'specialty',
      width: 160,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '电话',
      dataIndex: 'phone',
      width: 130,
      copyable: true,
      hideInSearch: true,
    },
    {
      title: '挂号费',
      dataIndex: 'registrationFeeCent',
      width: 100,
      hideInSearch: true,
      render: (_, record) => {
        const yuan = (record.registrationFeeCent / 100).toFixed(2);
        return `${yuan} 元`;
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      valueEnum: {
        ENABLED: { text: '启用', status: 'Success' },
        DISABLED: { text: '停用', status: 'Error' },
        SUSPENDED: { text: '暂停', status: 'Warning' },
      },
      render: (_, record) => {
        const s = STATUS_MAP[record.status];
        return <Tag color={s?.color}>{s?.text || record.status}</Tag>;
      },
    },
    {
      title: '操作',
      width: 100,
      hideInSearch: true,
      render: (_, record) => {
        const menu = buildActionMenu(deps, record);
        if (!menu) return <span style={{ color: '#999' }}>-</span>;
        return (
          <Dropdown menu={{ items: menu }} trigger={['click']}>
            <Button type="link" size="small">
              <Space>
                操作
                <MoreOutlined />
              </Space>
            </Button>
          </Dropdown>
        );
      },
    },
  ];
}

import { Outlet, useNavigate, useLocation, useModel, history } from '@umijs/max';
import { Layout, Menu, Button, Dropdown, Avatar, Space, Typography, Drawer } from 'antd';
import {
  UserOutlined,
  LogoutOutlined,
  TeamOutlined,
  ScheduleOutlined,
  MedicineBoxOutlined,
  FileTextOutlined,
  BarChartOutlined,
  AuditOutlined,
  HomeOutlined,
  SettingOutlined,
  AlertOutlined,
  ContainerOutlined,
  ProfileOutlined,
  RobotOutlined,
} from '@ant-design/icons';
import type { MenuProps } from 'antd';
import { request } from '@umijs/max';
import { useState, useEffect } from 'react';
import { AgentFloatingButton } from '@/components/agent/AgentFloatingButton';
import { AiPanel } from '@/components/agent/AiPanel';
import { buildAgentContext } from '@/models/agent';

const { Header, Sider, Content } = Layout;
const { Text } = Typography;

/**
 * 侧边栏菜单渲染 - 根据角色数组动态过滤
 */
function buildMenuItems(roles: string[]): MenuProps['items'] {
  const isAdmin = roles.includes('ADMIN');
  const canAudit = isAdmin || roles.includes('DEPT_HEAD');

  return [
    ...(isAdmin
      ? [
          {
            key: '/admin',
            label: '医院管理',
            icon: <HomeOutlined />,
            children: [
              { key: '/admin/hospital', label: '医院信息' },
              { key: '/admin/departments', label: '科室管理' },
              { key: '/admin/doctors', label: '医生管理' },
            ],
          },
        ]
      : []),
    {
      key: '/schedule',
      label: '排班管理',
      icon: <ScheduleOutlined />,
      children: [
        { key: '/schedule/list', label: '排班列表' },
        { key: '/schedule/locked', label: '锁定时段' },
      ],
    },
    ...(isAdmin
      ? []
      : [
          {
            key: '/consult',
            label: '接诊台',
            icon: <TeamOutlined />,
          },
        ]),
    {
      key: '/prescription',
      label: '处方管理',
      icon: <FileTextOutlined />,
      children: [
        { key: '/prescription/list', label: '处方列表' },
        ...(canAudit
          ? [{ key: '/prescription/pending-audit', label: '待审核' }]
          : []),
        { key: '/prescription/templates', label: '处方模板' },
      ],
    },
    ...(isAdmin
      ? [
          {
            key: '/drug',
            label: '药品库存',
            icon: <MedicineBoxOutlined />,
            children: [
              { key: '/drug/catalog', label: '药品目录' },
              { key: '/drug/inventory', label: '库存管理' },
              { key: '/drug/alerts', label: '库存预警' },
            ],
          },
        ]
      : []),
    {
      key: '/patient',
      label: '患者管理',
      icon: <UserOutlined />,
    },
    {
      key: '/agent',
      label: 'AI 助手',
      icon: <RobotOutlined />,
    },
    ...(isAdmin
      ? [
          {
            key: '/statistics',
            label: '统计报表',
            icon: <BarChartOutlined />,
            children: [
              { key: '/statistics/overview', label: '概览' },
              { key: '/statistics/department', label: '科室统计' },
              { key: '/statistics/daily', label: '日报统计' },
            ],
          },
        ]
      : []),
  ];
}

export default function MainLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { initialState, setInitialState } = useModel('@@initialState');
  const [collapsed, setCollapsed] = useState(false);
  const [authChecked, setAuthChecked] = useState(false);
  const [showAgentDrawer, setShowAgentDrawer] = useState(false);

  const currentUser = initialState?.currentUser;
  const roles = currentUser?.roles ?? [];

  // 未登录跳转登录页；已登录时根据角色做首次路由修正
  useEffect(() => {
    if (!authChecked) {
      setAuthChecked(true);
      if (!currentUser) {
        navigate('/login', { replace: true });
        return;
      }
      // ADMIN 无接诊台权限，登录后默认跳转排班列表
      const isAdmin = roles.includes('ADMIN');
      const path = location.pathname;
      if (isAdmin && (path === '/' || path === '/consult/queue')) {
        navigate('/schedule/list', { replace: true });
      }
    }
  }, [currentUser, authChecked, navigate, roles, location.pathname]);

  if (!currentUser) {
    return null; // 跳转前不渲染任何内容，避免闪烁
  }

  const menuItems = buildMenuItems(roles);

  /** 菜单点击跳转 */
  const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
    navigate(key);
  };

  /** 选中高亮：取当前路径的第一级或第二级前缀 */
  const selectedKeys = [location.pathname];
  const openKeys = ['/' + location.pathname.split('/').filter(Boolean)[0]];

  /** 退出登录 */
  const handleLogout = async () => {
    try {
      await request('/api/b/auth/logout', { method: 'POST' });
    } catch {
      // 即使接口失败也清理本地状态
    }
    localStorage.removeItem('b_access_token');
    setInitialState({ currentUser: undefined });
    history.push('/login');
  };

  /** 构建 Agent 上下文 */
  const agentContext = buildAgentContext(location.pathname, {
    hospitalId: currentUser?.hospitalId,
    doctorId: currentUser?.id,
  });

  /** 需要隐藏悬浮球的页面 */
  const hideAgentPages = ['/login', '/agent'];
  const shouldHideAgent = hideAgentPages.some((path) =>
    location.pathname.startsWith(path),
  );

  const userMenuItems: MenuProps['items'] = [
    {
      key: 'profile',
      label: `${currentUser?.name ?? '未知用户'}`,
      disabled: true,
    },
    { type: 'divider' },
    {
      key: 'logout',
      label: '退出登录',
      icon: <LogoutOutlined />,
      danger: true,
      onClick: handleLogout,
    },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        theme="dark"
        width={220}
      >
        <div
          style={{
            height: 64,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            borderBottom: '1px solid rgba(255,255,255,0.1)',
          }}
        >
          <Text
            strong
            style={{ color: '#fff', fontSize: collapsed ? 16 : 18 }}
          >
            {collapsed ? 'SP' : 'SPHP'}
          </Text>
        </div>

        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={selectedKeys}
          defaultOpenKeys={[openKeys[0]]}
          items={menuItems}
          onClick={handleMenuClick}
        />
      </Sider>

      <Layout>
        <Header
          style={{
            background: '#fff',
            padding: '0 24px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            borderBottom: '1px solid #f0f0f0',
          }}
        >
          <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
            <Space style={{ cursor: 'pointer' }}>
              <Avatar icon={<UserOutlined />} />
              <Text>{currentUser?.name ?? '用户'}</Text>
            </Space>
          </Dropdown>
        </Header>

        <Content style={{ margin: 16, minHeight: 280 }}>
          <Outlet />
        </Content>
      </Layout>

      {/* Agent 悬浮球 */}
      {!shouldHideAgent && (
        <AgentFloatingButton onClick={() => setShowAgentDrawer(true)} />
      )}

      {/* Agent 聊天面板抽屉 */}
      <Drawer
        title="AI 助手"
        placement="right"
        width={480}
        open={showAgentDrawer}
        onClose={() => setShowAgentDrawer(false)}
        styles={{ body: { padding: 0, overflow: 'hidden' } }}
      >
        <AiPanel context={agentContext} embedded={true} />
      </Drawer>
    </Layout>
  );
}
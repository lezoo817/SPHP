import { Card, Form, Input, Button, Typography, message } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { request } from '@umijs/max';
import { useState } from 'react';
import { getErrorMessage } from '@/utils/error';
import { API_URLS } from '@/constants/urls';

const { Title } = Typography;

export default function LoginPage() {
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      const res = await request<{
        code: string;
        data: { accessToken: string };
      }>(API_URLS.AUTH_LOGIN, {
        method: 'POST',
        data: values,
      });
      const token = res?.data?.accessToken;
      if (token) {
        localStorage.setItem('b_access_token', token);
      }
      message.success('登录成功');
      // B 端生产环境部署在 /b/，避免跳转到 C 端根路径。
      window.location.href = window.location.pathname.startsWith('/b/') ? '/b/' : '/';
    } catch (err: unknown) {
      message.error(getErrorMessage(err, '登录失败'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div
      style={{
        height: '100vh',
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        background: '#f0f2f5',
      }}
    >
      <Card style={{ width: 400 }}>
        <Title level={3} style={{ textAlign: 'center', marginBottom: 32 }}>
          SPHP 医院管理后台
        </Title>
        <Form onFinish={handleSubmit} size="large">
          <Form.Item
            name="username"
            rules={[{ required: true, message: '请输入账号' }]}
          >
            <Input prefix={<UserOutlined />} placeholder="登录账号" />
          </Form.Item>
          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="密码" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" block loading={loading}>
              登录
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}

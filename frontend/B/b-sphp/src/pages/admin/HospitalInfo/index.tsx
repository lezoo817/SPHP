/**
 * 医院信息页
 * - ADMIN 角色可查看并编辑医院信息
 * - 数据通过 React Query 拉取；编辑成功后失效缓存，useQuery 自动拉取最新数据（不闪骨架屏）
 */
import {
  Card,
  Descriptions,
  Tag,
  Button,
  Modal,
  message,
  Skeleton,
  Alert,
} from 'antd';
import { EditOutlined } from '@ant-design/icons';
import { ProForm, ProFormText, ProFormSelect } from '@ant-design/pro-components';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { getHospitalInfo, updateHospital } from '@/services/admin';
import { useHasRole } from '@/hooks/useCurrentUser';
import { getErrorMessage } from '@/utils/error';
import { QUERY_KEYS, STALE_TIME } from '@/constants/queryKeys';

/** 医院等级选项 */
const HOSPITAL_LEVELS = [
  { label: '三级甲等（三甲）', value: '三甲' },
  { label: '三级乙等（三乙）', value: '三乙' },
  { label: '三级丙等（三丙）', value: '三丙' },
  { label: '二级甲等（二甲）', value: '二甲' },
  { label: '二级乙等（二乙）', value: '二乙' },
  { label: '二级丙等（二丙）', value: '二丙' },
  { label: '一级甲等（一甲）', value: '一甲' },
  { label: '一级乙等（一乙）', value: '一乙' },
  { label: '一级丙等（一丙）', value: '一丙' },
  { label: '未评级', value: '未评级' },
];

export default function HospitalInfo() {
  const isAdmin = useHasRole('ADMIN');
  const queryClient = useQueryClient();
  const [editModalOpen, setEditModalOpen] = useState(false);

  /** 医院信息查询：首次加载显示骨架屏，缓存内重复访问/刷新不闪屏 */
  const {
    data: hospital,
    isPending: loading,
    error: loadError,
    refetch,
  } = useQuery({
    queryKey: QUERY_KEYS.hospital,
    queryFn: getHospitalInfo,
    staleTime: STALE_TIME.hospital,
  });

  /** 编辑提交：成功后失效缓存，useQuery 自动拉取最新数据 */
  const { mutate: submitEdit, isPending: submitting } = useMutation({
    mutationFn: (values: API.UpdateHospitalReq) => {
      if (!hospital) throw new Error('医院信息不存在');
      return updateHospital(hospital.id, values);
    },
    onSuccess: () => {
      message.success('医院信息更新成功');
      setEditModalOpen(false);
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.hospital });
    },
    onError: (err: unknown) => {
      message.error(getErrorMessage(err, '更新失败，请重试'));
    },
  });

  /** 状态标签 */
  const statusTag = (status?: string) => {
    if (status === 'ENABLED') return <Tag color="green">启用</Tag>;
    if (status === 'DISABLED') return <Tag color="red">停用</Tag>;
    return <Tag>未知</Tag>;
  };

  /** 加载失败信息（Error 对象转可读文本） */
  const errorMessage = loadError
    ? getErrorMessage(loadError, '加载医院信息失败')
    : null;

  if (loading) {
    return (
      <Card title="医院信息">
        <Skeleton active paragraph={{ rows: 6 }} />
      </Card>
    );
  }

  if (errorMessage) {
    return (
      <Card title="医院信息">
        <Alert
          message="加载失败"
          description={errorMessage}
          type="error"
          showIcon
          action={
            <Button size="small" onClick={() => refetch()}>
              重试
            </Button>
          }
        />
      </Card>
    );
  }

  if (!hospital) {
    return (
      <Card title="医院信息">
        <Alert message="暂无医院信息数据" type="info" showIcon />
      </Card>
    );
  }

  return (
    <>
      <Card
        title="医院信息"
        extra={
          isAdmin && (
            <Button
              type="primary"
              icon={<EditOutlined />}
              onClick={() => setEditModalOpen(true)}
            >
              编辑
            </Button>
          )
        }
      >
        <Descriptions bordered column={1} style={{ maxWidth: 720 }}>
          <Descriptions.Item label="医院名称">{hospital.name}</Descriptions.Item>
          <Descriptions.Item label="医院等级">{hospital.level}</Descriptions.Item>
          <Descriptions.Item label="医院地址">
            {hospital.address || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="联系方式">
            {hospital.contact || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="医院简介">
            {hospital.description || '-'}
          </Descriptions.Item>
          <Descriptions.Item label="状态">
            {statusTag(hospital.status)}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Modal
        title="编辑医院信息"
        open={editModalOpen}
        footer={null}
        destroyOnHidden
        onCancel={() => setEditModalOpen(false)}
        width={560}
      >
        <ProForm<API.UpdateHospitalReq>
          initialValues={{
            name: hospital.name,
            level: hospital.level,
            description: hospital.description,
            address: hospital.address,
            contact: hospital.contact,
          }}
          onFinish={(values) => submitEdit(values)}
          submitter={{
            submitButtonProps: { loading: submitting },
            resetButtonProps: { style: { display: 'none' } },
          }}
        >
          <ProFormText
            name="name"
            label="医院名称"
            rules={[
              { required: true, message: '请输入医院名称' },
              { max: 100, message: '最多 100 个字符' },
            ]}
          />
          <ProFormSelect
            name="level"
            label="医院等级"
            options={HOSPITAL_LEVELS}
            rules={[{ required: true, message: '请选择医院等级' }]}
          />
          <ProFormText
            name="description"
            label="医院简介"
            rules={[{ max: 500, message: '最多 500 个字符' }]}
          />
          <ProFormText
            name="address"
            label="医院地址"
            rules={[{ max: 200, message: '最多 200 个字符' }]}
          />
          <ProFormText
            name="contact"
            label="联系方式"
            rules={[
              { max: 100, message: '最多 100 个字符' },
              {
                pattern: /^(\d{3,4}-?\d{7,8}|\d{11})?$/,
                message: '请输入正确的电话格式（固话或手机号）',
              },
            ]}
          />
        </ProForm>
      </Modal>
    </>
  );
}

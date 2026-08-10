/**
 * 患者列表页
 * - ProTable 列表，支持姓名模糊搜索
 * - 所有角色可访问（数据权限由后端控制）
 * - 点击行跳转患者详情页
 */
import { Tag, message } from 'antd';
import { ProTable } from '@ant-design/pro-components';
import { useRef } from 'react';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { history } from '@umijs/max';
import { getPatientList } from '@/services/admin';
import { getErrorMessage } from '@/utils/error';
import { PAGE_SIZE_DEFAULT } from '@/constants/pageSize';

/** 性别映射 */
const genderMap: Record<API.PatientListItem['gender'], { text: string; color: string }> = {
  MALE: { text: '男', color: 'blue' },
  FEMALE: { text: '女', color: 'magenta' },
  UNKNOWN: { text: '未知', color: 'default' },
};

export default function PatientList() {
  const actionRef = useRef<ActionType>();

  const columns: ProColumns<API.PatientListItem>[] = [
    {
      title: '患者ID',
      dataIndex: 'id',
      width: 100,
      hideInSearch: true,
    },
    {
      title: '姓名',
      dataIndex: 'name',
      width: 140,
      ellipsis: true,
    },
    {
      title: '性别',
      dataIndex: 'gender',
      width: 80,
      hideInSearch: true,
      render: (_, record) => {
        const g = genderMap[record.gender] ?? { text: record.gender, color: 'default' };
        return <Tag color={g.color}>{g.text}</Tag>;
      },
    },
    {
      title: '年龄',
      dataIndex: 'age',
      width: 80,
      hideInSearch: true,
    },
    {
      title: '最近就诊',
      dataIndex: 'lastVisitDate',
      width: 120,
      hideInSearch: true,
    },
  ];

  return (
    <ProTable<API.PatientListItem, API.PatientListParams>
      actionRef={actionRef}
      rowKey="id"
      columns={columns}
      request={async (params) => {
        const { current, pageSize, ...rest } = params;
        try {
          const res = await getPatientList({
            page: current,
            size: pageSize,
            name: rest.name,
          });
          return {
            data: res.list,
            total: res.total,
            success: true,
          };
        } catch (err: unknown) {
          message.error(getErrorMessage(err, '查询失败，请重试'));
          return { data: [], total: 0, success: true };
        }
      }}
      search={{
        labelWidth: 'auto',
        defaultCollapsed: true,
      }}
      pagination={{ pageSize: PAGE_SIZE_DEFAULT }}
      onRow={(record) => ({
        onClick: () => history.push(`/patient/detail/${record.id}`),
        style: { cursor: 'pointer' },
      })}
    />
  );
}
/**
 * 患者详情页
 * - 多 Tab 展示：基本信息、就诊记录、历史处方、当前用药
 * - 从路由参数获取患者ID；详情与用药数据经 React Query 拉取
 */
import { Card, Space, Tabs, Empty, message } from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ActionType } from '@ant-design/pro-components';
import { useParams, history } from '@umijs/max';
import { useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  getPatientInfo,
  getPatientVisits,
  getPatientPrescriptions,
} from '@/services/admin';
import { getErrorMessage } from '@/utils/error';
import { QUERY_KEYS, STALE_TIME } from '@/constants/queryKeys';
import { getVisitColumns, getPrescriptionColumns } from './columns';
import BasicInfoTab from './BasicInfoTab';
import MedicationsTab from './MedicationsTab';

export default function PatientDetail() {
  const { id } = useParams<{ id: string }>();
  const patientId = Number(id);
  const [activeTab, setActiveTab] = useState('info');
  const visitActionRef = useRef<ActionType>();
  const prescriptionActionRef = useRef<ActionType>();

  /** 患者详情（React Query 缓存 60s；无效 ID 时禁用请求） */
  const { data: detail, isLoading } = useQuery({
    queryKey: QUERY_KEYS.patientInfo(patientId),
    queryFn: () => getPatientInfo(patientId),
    enabled: Boolean(patientId),
    staleTime: STALE_TIME.patientInfo,
  });

  if (!patientId) {
    return <Empty description="缺少患者ID" />;
  }

  const tabItems = [
    {
      key: 'info',
      label: '基本信息',
      children: <BasicInfoTab loading={isLoading} detail={detail ?? null} />,
    },
    {
      key: 'visits',
      label: '就诊记录',
      children: (
        <ProTable<API.PatientVisitItem>
          actionRef={visitActionRef}
          rowKey="consultId"
          columns={getVisitColumns()}
          request={async (params) => {
            const { current, pageSize } = params;
            try {
              const res = await getPatientVisits(patientId, { page: current, size: pageSize });
              return { data: res.list, total: res.total, success: true };
            } catch (err: unknown) {
              await message.error(getErrorMessage(err, '查询就诊记录失败'));
              return { data: [], total: 0, success: true };
            }
          }}
          search={false}
          pagination={{ pageSize: 10 }}
          toolBarRender={false}
        />
      ),
    },
    {
      key: 'prescriptions',
      label: '历史处方',
      children: (
        <ProTable<API.PatientPrescriptionItem>
          actionRef={prescriptionActionRef}
          rowKey="id"
          columns={getPrescriptionColumns()}
          request={async (params) => {
            const { current, pageSize } = params;
            try {
              const res = await getPatientPrescriptions(patientId, {
                page: current,
                size: pageSize,
              });
              return { data: res.list, total: res.total, success: true };
            } catch (err: unknown) {
              await message.error(getErrorMessage(err, '查询历史处方失败'));
              return { data: [], total: 0, success: true };
            }
          }}
          search={false}
          pagination={{ pageSize: 10 }}
          toolBarRender={false}
        />
      ),
    },
    {
      key: 'medications',
      label: '当前用药',
      children: <MedicationsTab patientId={patientId} />,
    },
  ];

  return (
    <div>
      <Card
        size="small"
        style={{ marginBottom: 16 }}
        extra={<a onClick={() => history.push('/patient/list')}>返回列表</a>}
      >
        <Space>
          <strong>患者详情</strong>
          {detail && <span>— {detail.name}</span>}
        </Space>
      </Card>

      <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabItems} />
    </div>
  );
}

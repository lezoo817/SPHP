/**
 * 患者详情 - 当前用药 Tab。
 *
 * 仅展示当前在用（ACTIVE/PAUSED）用药计划，含天数/频次等用药说明；
 * 数据经 React Query 拉取（缓存 60s），加载 / 空 / 失败状态均为渲染分支，无副作用。
 */
import { Spin, Empty, Row, Col, Card, Descriptions, Tag } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { getPatientMedications } from '@/services/admin';
import { getErrorMessage } from '@/utils/error';
import { QUERY_KEYS, STALE_TIME } from '@/constants/queryKeys';
import { medicationStatusMap } from './constants';

interface Props {
  patientId: number;
}

export default function MedicationsTab({ patientId }: Props) {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: QUERY_KEYS.patientMedications(patientId),
    queryFn: () => getPatientMedications(patientId),
    staleTime: STALE_TIME.patientMedications,
  });

  if (isLoading) {
    return <Spin style={{ display: 'block', margin: '48px auto' }} />;
  }

  if (isError) {
    return <Empty description={getErrorMessage(error, '查询用药信息失败')} />;
  }

  if (!data || data.medicationPlans.length === 0) {
    return <Empty description="暂无当前用药" />;
  }

  return (
    <Row gutter={[12, 12]}>
      {data.medicationPlans.map((plan) => (
        <Col key={plan.id} xs={24} sm={12} lg={8}>
          <Card size="small" variant="outlined">
            <Descriptions column={1} size="small">
              <Descriptions.Item label="药品">{plan.drugName}</Descriptions.Item>
              <Descriptions.Item label="用量">{plan.dosage}</Descriptions.Item>
              <Descriptions.Item label="频次">{plan.frequency}</Descriptions.Item>
              <Descriptions.Item label="用法">{plan.usageMethod}</Descriptions.Item>
              <Descriptions.Item label="天数">
                {plan.durationDays ? `${plan.durationDays} 天` : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={medicationStatusMap[plan.status]?.color ?? 'default'}>
                  {medicationStatusMap[plan.status]?.text ?? plan.status}
                </Tag>
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
      ))}
    </Row>
  );
}

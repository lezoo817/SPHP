/**
 * 患者详情 - 当前用药与随访 Tab。
 *
 * 数据经 React Query 拉取（缓存 60s）；加载 / 空 / 失败状态均为渲染分支，无副作用。
 */
import { Spin, Empty, Row, Col, Card, Descriptions, Tag } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { getPatientMedications } from '@/services/admin';
import { getErrorMessage } from '@/utils/error';
import { QUERY_KEYS, STALE_TIME } from '@/constants/queryKeys';
import { medicationStatusMap, followUpStatusMap } from './constants';

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

  if (!data || (data.medicationPlans.length === 0 && data.followUpPlans.length === 0)) {
    return <Empty description="暂无用药与随访计划" />;
  }

  return (
    <Row gutter={[16, 16]}>
      <Col span={24}>
        <Card title="用药计划" size="small">
          {data.medicationPlans.length > 0 ? (
            <Row gutter={[12, 12]}>
              {data.medicationPlans.map((plan) => (
                <Col key={plan.id} xs={24} sm={12} lg={8}>
                  <Card size="small" variant="outlined">
                    <Descriptions column={1} size="small">
                      <Descriptions.Item label="药品">{plan.drugName}</Descriptions.Item>
                      <Descriptions.Item label="用量">{plan.dosage}</Descriptions.Item>
                      <Descriptions.Item label="频次">{plan.frequency}</Descriptions.Item>
                      <Descriptions.Item label="用法">{plan.usageMethod}</Descriptions.Item>
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
          ) : (
            <Empty description="暂无用药计划" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </Card>
      </Col>
      <Col span={24}>
        <Card title="随访计划" size="small">
          {data.followUpPlans.length > 0 ? (
            <Row gutter={[12, 12]}>
              {data.followUpPlans.map((plan) => (
                <Col key={plan.id} xs={24} sm={12} lg={8}>
                  <Card size="small" variant="outlined">
                    <Descriptions column={1} size="small">
                      <Descriptions.Item label="类型">{plan.followUpType || '-'}</Descriptions.Item>
                      <Descriptions.Item label="内容">{plan.content || '-'}</Descriptions.Item>
                      <Descriptions.Item label="到期时间">{plan.dueAt}</Descriptions.Item>
                      <Descriptions.Item label="状态">
                        <Tag color={followUpStatusMap[plan.status]?.color ?? 'default'}>
                          {followUpStatusMap[plan.status]?.text ?? plan.status}
                        </Tag>
                      </Descriptions.Item>
                    </Descriptions>
                  </Card>
                </Col>
              ))}
            </Row>
          ) : (
            <Empty description="暂无随访计划" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          )}
        </Card>
      </Col>
    </Row>
  );
}

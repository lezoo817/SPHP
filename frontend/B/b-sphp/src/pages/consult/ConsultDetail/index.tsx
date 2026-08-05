/**
 * 接诊台详情页（三栏布局）。
 *
 * 三栏布局：
 * - 左栏：患者基本信息 + 接诊操作（开始/结束接诊）
 * - 中栏：问诊消息区 + 病历记录编辑
 * - 右栏：AI 辅助面板（AiPanel embedded 模式）
 *
 * 当前为骨架实现，核心业务（接诊/开方/消息）待后续迭代补全；
 * 右栏 AiPanel 已可独立工作，按 consultationId 维护独立会话。
 */
import { useEffect, useState } from 'react';
import { useParams } from '@umijs/max';
import { Card, Col, Empty, Row, Typography } from 'antd';
import { AiPanel } from '@/components/agent/AiPanel';
import { buildAgentContext } from '@/models/agent';
import { useModel } from '@umijs/max';
import type { AgentChatContext } from '@/typings/agent';

const { Text, Title } = Typography;

export default function ConsultDetailPage() {
  const params = useParams();
  const consultId = Number(params.id);
  const { initialState } = useModel('@@initialState');
  const currentUser = initialState?.currentUser;
  const [context, setContext] = useState<AgentChatContext>({ page: 'consultation' });

  // 构造 AI 辅助面板上下文（携带 consultation_id / patient_id / hospital_id / doctor_id）
  useEffect(() => {
    setContext(
      buildAgentContext(`/consult/detail/${consultId}`, {
        consultationId: consultId,
        hospitalId: currentUser?.hospitalId,
        doctorId: currentUser?.doctorId,
      }),
    );
  }, [consultId, currentUser]);

  return (
    <Row gutter={16} style={{ height: 'calc(100vh - 140px)', margin: 0 }}>
      {/* 左栏：患者信息 */}
      <Col xs={24} md={6} style={{ height: '100%' }}>
        <Card
          title="患者信息"
          size="small"
          style={{ height: '100%', overflowY: 'auto' }}
        >
          <Empty description={`接诊 #${consultId}（开发中）`} />
        </Card>
      </Col>

      {/* 中栏：问诊消息 + 病历记录 */}
      <Col xs={24} md={10} style={{ height: '100%' }}>
        <Card
          title="问诊消息"
          size="small"
          style={{ height: '100%', overflowY: 'auto' }}
        >
          <Empty description="问诊消息区开发中" />
          <Title level={5} style={{ marginTop: 16 }}>
            病历记录
          </Title>
          <Text type="secondary">
            点击右侧 AI 辅助面板“生成病历草稿”可自动填充病历内容。
          </Text>
        </Card>
      </Col>

      {/* 右栏：AI 辅助面板 */}
      <Col xs={24} md={8} style={{ height: '100%' }}>
        <Card
          size="small"
          styles={{ body: { padding: 0, height: '100%' } }}
          style={{ height: '100%', overflow: 'hidden' }}
        >
          <AiPanel context={context} embedded consultationId={consultId} />
        </Card>
      </Col>
    </Row>
  );
}

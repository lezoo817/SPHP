/**
 * 患者详情 - 基本信息 Tab。
 *
 * 纯展示组件：个人信息 / 过敏史 / 既往史三块卡片；加载中与无数据状态由 props 驱动。
 */
import { Tag, Descriptions, Card, Spin, Table, Empty } from 'antd';
import { genderTextMap, genderColorMap, severityColorMap } from './constants';

interface Props {
  loading: boolean;
  detail: API.PatientDetailInfo | null;
}

/** 根据出生日期计算年龄 */
function calculateAge(dateOfBirth: string): number {
  const birth = new Date(dateOfBirth);
  const today = new Date();
  let age = today.getFullYear() - birth.getFullYear();
  const m = today.getMonth() - birth.getMonth();
  if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) {
    age--;
  }
  return age;
}

export default function BasicInfoTab({ loading, detail }: Props) {
  return (
    <Spin spinning={loading}>
      {detail ? (
        <>
          <Card title="个人信息" size="small" style={{ marginBottom: 16 }}>
            <Descriptions column={2} size="small">
              <Descriptions.Item label="姓名">{detail.name}</Descriptions.Item>
              <Descriptions.Item label="性别">
                <Tag color={genderColorMap[detail.gender] ?? 'default'}>
                  {genderTextMap[detail.gender] ?? detail.gender}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="出生日期">{detail.dateOfBirth || '-'}</Descriptions.Item>
              <Descriptions.Item label="年龄">
                {detail.dateOfBirth ? calculateAge(detail.dateOfBirth) : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="手机号">{detail.phone || '-'}</Descriptions.Item>
              <Descriptions.Item label="紧急联系人">{detail.emergencyContact || '-'}</Descriptions.Item>
            </Descriptions>
          </Card>

          <Card title="过敏史" size="small" style={{ marginBottom: 16 }}>
            {detail.allergies.length > 0 ? (
              <Table
                rowKey="id"
                dataSource={detail.allergies}
                columns={[
                  { title: '过敏原', dataIndex: 'allergen', width: 150 },
                  { title: '反应', dataIndex: 'reaction', ellipsis: true },
                  {
                    title: '严重程度',
                    dataIndex: 'severity',
                    width: 120,
                    render: (_: unknown, r: API.AllergyInfo) => (
                      <Tag color={severityColorMap[r.severity] ?? 'default'}>{r.severity}</Tag>
                    ),
                  },
                ]}
                pagination={false}
                size="small"
              />
            ) : (
              <Empty description="无过敏史记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            )}
          </Card>

          <Card title="既往史" size="small">
            {detail.medicalHistories.length > 0 ? (
              <Table
                rowKey="id"
                dataSource={detail.medicalHistories}
                columns={[
                  { title: '内容', dataIndex: 'content', ellipsis: true },
                  { title: '发生日期', dataIndex: 'occurredAt', width: 120 },
                ]}
                pagination={false}
                size="small"
              />
            ) : (
              <Empty description="无既往史记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            )}
          </Card>
        </>
      ) : (
        !loading && <Empty description="未找到患者信息" />
      )}
    </Spin>
  );
}

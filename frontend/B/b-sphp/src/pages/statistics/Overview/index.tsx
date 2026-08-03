/**
 * 运营总览页
 * - 日期范围筛选
 * - Statistic 卡片展示：总挂号量、完成率、总收入、处方量、平均等待时间
 */
import { Card, Row, Col, Statistic, message, DatePicker, Space } from 'antd';
import { AppstoreOutlined, CheckCircleOutlined, DollarOutlined, FileTextOutlined, ClockCircleOutlined } from '@ant-design/icons';
import { useState, useEffect, useCallback } from 'react';
import { getStatisticsOverview } from '@/services/admin';
import dayjs from 'dayjs';

const { RangePicker } = DatePicker;

/** 分转元 */
function formatYuan(cent: number): string {
  return (cent / 100).toFixed(2);
}

/** 百分比格式化 */
function formatPercent(rate: number): string {
  return `${(rate * 100).toFixed(1)}%`;
}

export default function Overview() {
  const [loading, setLoading] = useState(true);
  const [data, setData] = useState<API.StatisticsOverview | null>(null);
  const [dates, setDates] = useState<[dayjs.Dayjs, dayjs.Dayjs]>([
    dayjs().startOf('month'),
    dayjs(),
  ]);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getStatisticsOverview({
        startDate: dates[0].format('YYYY-MM-DD'),
        endDate: dates[1].format('YYYY-MM-DD'),
      });
      setData(res);
    } catch (err: any) {
      message.error(err?.message || '查询运营总览失败');
    } finally {
      setLoading(false);
    }
  }, [dates]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  return (
    <div>
      <Card size="small" style={{ marginBottom: 16 }}>
        <Space>
          <span style={{ fontWeight: 500 }}>统计周期：</span>
          <RangePicker
            value={dates}
            onChange={(v) => {
              if (v && v[0] && v[1]) setDates([v[0], v[1]]);
            }}
            allowClear={false}
          />
        </Space>
      </Card>

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={loading}>
            <Statistic
              title="总挂号量"
              value={data?.totalAppointments ?? '-'}
              prefix={<AppstoreOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={loading}>
            <Statistic
              title="完成率"
              value={data ? formatPercent(data.completedRate) : '-'}
              prefix={<CheckCircleOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={loading}>
            <Statistic
              title="总收入"
              value={data ? `¥${formatYuan(data.totalRevenueCent)}` : '-'}
              prefix={<DollarOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={loading}>
            <Statistic
              title="处方量"
              value={data?.totalPrescriptions ?? '-'}
              prefix={<FileTextOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card loading={loading}>
            <Statistic
              title="平均等待时间（分钟）"
              value={data?.avgWaitTime ?? '-'}
              suffix="min"
              prefix={<ClockCircleOutlined />}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
}
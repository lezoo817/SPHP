/**
 * 库存预警页
 * - 展示库存状态为 ALERT(告警) / LOW(偏低) 的药品库存列表
 * - 支持按药房筛选
 * - 只读视图，不可编辑
 */
import { Tag, message, Progress, Space, Select } from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import { useRef, useState, useEffect } from 'react';
import type { ActionType } from '@ant-design/pro-components';
import { getInventoryAlerts, getPharmacies } from '@/services/admin';

/** 库存状态映射 */
const STATUS_MAP: Record<string, { color: string; label: string }> = {
  ALERT: { color: '#ff4d4f', label: '告警' },
  LOW: { color: '#faad14', label: '偏低' },
};

/** 分转元显示 */
function formatPrice(cent: number): string {
  return (cent / 100).toFixed(2);
}

export default function StockAlerts() {
  const actionRef = useRef<ActionType>();
  const [pharmacyId, setPharmacyId] = useState<number | undefined>(undefined);
  const [pharmacies, setPharmacies] = useState<API.PharmacyItem[]>([]);

  useEffect(() => {
    getPharmacies().then(setPharmacies).catch(() => {});
  }, []);

  const columns: ProColumns<API.InventoryItem>[] = [
    {
      title: '药房',
      dataIndex: 'pharmacyName',
      width: 120,
      ellipsis: true,
    },
    {
      title: '药品名称',
      dataIndex: 'drugName',
      width: 180,
      ellipsis: true,
    },
    {
      title: '规格',
      dataIndex: 'specification',
      width: 130,
      ellipsis: true,
    },
    {
      title: '当前库存',
      dataIndex: 'availableCount',
      width: 100,
      sorter: true,
    },
    {
      title: '安全库存',
      dataIndex: 'safetyStock',
      width: 100,
    },
    {
      title: '库存状态',
      width: 200,
      render: (_, record) => {
        const status = STATUS_MAP[record.status] || { color: '#52c41a', label: '正常' };
        const ratio = record.safetyStock > 0
          ? Math.min(100, (record.availableCount / record.safetyStock) * 100)
          : 0;
        return (
          <Space>
            <Progress
              percent={Math.round(ratio)}
              size="small"
              strokeColor={status.color}
              style={{ width: 100 }}
            />
            <Tag color={status.color}>{status.label}</Tag>
          </Space>
        );
      },
    },
    {
      title: '单价（元）',
      dataIndex: 'unitPriceCent',
      width: 100,
      render: (_, record) => formatPrice(record.unitPriceCent),
    },
    {
      title: '短缺数量',
      width: 100,
      render: (_, record) => {
        const shortage = Math.max(0, record.safetyStock - record.availableCount);
        return (
          <span style={{ color: shortage > 0 ? '#ff4d4f' : undefined, fontWeight: 'bold' }}>
            {shortage}
          </span>
        );
      },
    },
  ];

  /** 手动请求，不依赖 ProTable 的自动分页模式 */
  const fetchAlerts = async () => {
    try {
      const list = await getInventoryAlerts(pharmacyId !== undefined ? { pharmacyId } : undefined);
      return {
        data: list,
        total: list.length,
        success: true,
      };
    } catch (err: any) {
      message.error(err?.message || '查询预警失败');
      return { data: [], total: 0, success: true };
    }
  };

  return (
    <ProTable<API.InventoryItem>
      actionRef={actionRef}
      rowKey="id"
      columns={columns}
      request={fetchAlerts}
      search={false}
      params={{ pharmacyId }}
      pagination={{ pageSize: 10, showTotal: (total) => `共 ${total} 条预警` }}
      toolBarRender={() => [
        <Select
          key="pharmacy"
          allowClear
          placeholder="全部药房"
          style={{ width: 160 }}
          value={pharmacyId}
          onChange={(val) => setPharmacyId(val)}
          options={pharmacies.map((p) => ({ label: p.name, value: p.id }))}
        />,
      ]}
    />
  );
}
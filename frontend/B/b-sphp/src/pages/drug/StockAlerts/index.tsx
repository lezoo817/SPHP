/**
 * 库存预警页
 * - 展示库存状态为 ALERT(告警) / LOW(偏低) 的药品库存列表
 * - 支持按药房筛选
 * - 只读视图，不可编辑
 */
import { Tag, message, Progress, Space, Select } from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { getInventoryAlerts, getPharmacies } from '@/services/admin';
import { getErrorMessage } from '@/utils/error';
import { formatPrice } from '@/utils/price';
import { QUERY_KEYS, STALE_TIME } from '@/constants/queryKeys';
import { PAGE_SIZE_DEFAULT } from '@/constants/pageSize';

/** 预警状态展示配置（ALERT/LOW） */
const STATUS_MAP: Record<'ALERT' | 'LOW', { color: string; label: string }> = {
  ALERT: { color: '#ff4d4f', label: '告警' },
  LOW: { color: '#faad14', label: '偏低' },
};

/** 取库存状态的展示配置；NORMAL（正常）回退为绿色正常态 */
function getStatusConfig(
  status: API.InventoryItem['status'],
): { color: string; label: string } {
  return status === 'ALERT' || status === 'LOW'
    ? STATUS_MAP[status]
    : { color: '#52c41a', label: '正常' };
}

export default function StockAlerts() {
  const actionRef = useRef<ActionType>();
  const [pharmacyId, setPharmacyId] = useState<number | undefined>(undefined);

  /** 药房下拉选项：React Query 缓存，切换页签/刷新不重复请求 */
  const { data: pharmacies = [] } = useQuery({
    queryKey: QUERY_KEYS.pharmacies,
    queryFn: getPharmacies,
    staleTime: STALE_TIME.pharmacies,
  });

  const columns: ProColumns<API.InventoryItem>[] = [
    { title: '药房', dataIndex: 'pharmacyName', width: 120, ellipsis: true },
    { title: '药品名称', dataIndex: 'drugName', width: 180, ellipsis: true },
    { title: '规格', dataIndex: 'specification', width: 130, ellipsis: true },
    { title: '当前库存', dataIndex: 'availableCount', width: 100, sorter: true },
    { title: '安全库存', dataIndex: 'safetyStock', width: 100 },
    {
      title: '库存状态',
      width: 200,
      render: (_, record) => {
        const status = getStatusConfig(record.status);
        const ratio =
          record.safetyStock > 0
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
          <span
            style={{
              color: shortage > 0 ? '#ff4d4f' : undefined,
              fontWeight: 'bold',
            }}
          >
            {shortage}
          </span>
        );
      },
    },
  ];

  /** 手动请求，不依赖 ProTable 的自动分页模式 */
  const fetchAlerts = async () => {
    try {
      const list = await getInventoryAlerts(
        pharmacyId !== undefined ? { pharmacyId } : undefined,
      );
      return { data: list, total: list.length, success: true };
    } catch (err: unknown) {
      message.error(getErrorMessage(err, '查询预警失败'));
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
      pagination={{ pageSize: PAGE_SIZE_DEFAULT, showTotal: (total) => `共 ${total} 条预警` }}
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

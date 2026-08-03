/**
 * 库存预警页
 * - 展示 availableCount < safetyStock 的低库存药品列表
 * - 只读视图，不可编辑
 */
import { Tag, message, Progress, Space } from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import { useRef } from 'react';
import type { ActionType } from '@ant-design/pro-components';
import { getInventoryAlerts } from '@/services/admin';

/** 计算库存状态（仅告警级别） */
function calcAlert(available: number, safety: number) {
  if (safety <= 0) {
    return { percent: 0, color: '#ff4d4f', label: '告警', severity: 'error' as const };
  }
  const ratio = available / safety;
  const percent = Math.min(100, (available / safety) * 100);
  if (ratio < 0.5) {
    return { percent, color: '#ff4d4f', label: '严重告警', severity: 'error' as const };
  }
  return { percent, color: '#faad14', label: '偏低告警', severity: 'warning' as const };
}

/** 分转元显示 */
function formatPrice(cent: number): string {
  return (cent / 100).toFixed(2);
}

export default function StockAlerts() {
  const actionRef = useRef<ActionType>();

  const columns: ProColumns<API.InventoryItem>[] = [
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
      title: '锁定数量',
      dataIndex: 'lockedCount',
      width: 100,
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
        const { percent, color, label, severity } = calcAlert(
          record.availableCount,
          record.safetyStock,
        );
        return (
          <Space>
            <Progress
              percent={Math.round(percent)}
              size="small"
              strokeColor={color}
              style={{ width: 100 }}
            />
            <Tag color={color}>{label}</Tag>
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
      const list = await getInventoryAlerts();
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
      pagination={{ pageSize: 10, showTotal: (total) => `共 ${total} 条预警` }}
      toolBarRender={false}
    />
  );
}
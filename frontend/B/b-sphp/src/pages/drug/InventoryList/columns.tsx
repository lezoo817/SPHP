/**
 * 库存管理页列配置。
 *
 * 通过 getColumns(deps) 工厂生成：操作列依赖 ADMIN 角色与更新/释放锁定回调。
 * 药品搜索下拉的候选列表由 fetchDrugOptions 声明式拉取（ProTable request 机制）。
 */
import { Tag, Button, Space, Progress } from 'antd';
import { EditOutlined, UnlockOutlined } from '@ant-design/icons';
import { ProFormSelect } from '@ant-design/pro-components';
import type { ProColumns } from '@ant-design/pro-components';
import { getDrugs } from '@/services/admin';
import { formatPrice } from '@/utils/price';
import { calcStatus } from './constants';
import { PAGE_SIZE_200 } from '@/constants/pageSize';

interface ColumnsDeps {
  isAdmin: boolean;
  onUpdate: (record: API.InventoryItem) => void;
  onUnlock: (record: API.InventoryItem) => void;
}

/** 拉取药品选项（供搜索筛选下拉） */
async function fetchDrugOptions() {
  try {
    const res = await getDrugs({ page: 1, size: PAGE_SIZE_200 });
    return (res.list ?? []).map((d) => ({ label: d.name, value: d.id }));
  } catch {
    return [];
  }
}

export function getColumns(deps: ColumnsDeps): ProColumns<API.InventoryItem>[] {
  const { isAdmin, onUpdate, onUnlock } = deps;

  return [
    { title: '药房', dataIndex: 'pharmacyName', width: 120, ellipsis: true, hideInSearch: true },
    { title: '药品名称', dataIndex: 'drugName', width: 180, ellipsis: true, hideInSearch: true },
    {
      title: '药品',
      dataIndex: 'drugId',
      hideInTable: true,
      renderFormItem: () => (
        <ProFormSelect
          name="drugId"
          noStyle
          request={fetchDrugOptions}
          placeholder="请选择药品"
          allowClear
        />
      ),
    },
    { title: '规格', dataIndex: 'specification', width: 130, hideInSearch: true },
    {
      title: '库存数量',
      dataIndex: 'availableCount',
      width: 100,
      hideInSearch: true,
      sorter: true,
    },
    { title: '锁定数量', dataIndex: 'lockedCount', width: 100, hideInSearch: true },
    { title: '安全库存', dataIndex: 'safetyStock', width: 100, hideInSearch: true },
    {
      title: '库存状态',
      width: 200,
      hideInSearch: true,
      render: (_, record) => {
        const { percent, color, label } = calcStatus(
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
      hideInSearch: true,
      render: (_, record) => formatPrice(record.unitPriceCent),
    },
    {
      title: '操作',
      width: 180,
      hideInSearch: true,
      render: (_, record) => (
        <Space>
          {isAdmin && record.id && (
            <Button
              type="link"
              size="small"
              icon={<EditOutlined />}
              onClick={() => onUpdate(record)}
            >
              更新库存
            </Button>
          )}
          {isAdmin && record.id && record.lockedCount > 0 && (
            <Button
              type="link"
              size="small"
              icon={<UnlockOutlined />}
              onClick={() => onUnlock(record)}
            >
              释放锁定
            </Button>
          )}
        </Space>
      ),
    },
  ];
}

/**
 * 库存管理页
 * - ProTable 列表，展示药品库存信息
 * - 支持按药房筛选；不选药房时汇总全部药房库存
 * - 库存状态（NORMAL/LOW/ALERT）前端根据 availableCount 与 safetyStock 计算
 * - ADMIN 角色可更新库存、手动释放锁定库存
 */
import { Select, message } from 'antd';
import { ProTable } from '@ant-design/pro-components';
import { useQuery } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import type { ActionType } from '@ant-design/pro-components';
import {
  getInventoryList,
  updateInventory,
  unlockInventory,
  getPharmacies,
} from '@/services/admin';
import { useHasRole } from '@/hooks/useCurrentUser';
import { getErrorMessage } from '@/utils/error';
import { QUERY_KEYS, STALE_TIME } from '@/constants/queryKeys';
import { getColumns } from './columns';
import UpdateInventoryModal from './UpdateInventoryModal';
import UnlockInventoryModal from './UnlockInventoryModal';
import { PAGE_SIZE_DEFAULT } from '@/constants/pageSize';

export default function InventoryList() {
  const isAdmin = useHasRole('ADMIN');
  const actionRef = useRef<ActionType>();

  const [updateModalOpen, setUpdateModalOpen] = useState(false);
  const [unlockModalOpen, setUnlockModalOpen] = useState(false);
  const [selectedItem, setSelectedItem] = useState<API.InventoryItem | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [pharmacyId, setPharmacyId] = useState<number | undefined>(undefined);

  /** 药房下拉选项：React Query 缓存，切换页签/刷新不重复请求 */
  const { data: pharmacies = [] } = useQuery({
    queryKey: QUERY_KEYS.pharmacies,
    queryFn: getPharmacies,
    staleTime: STALE_TIME.pharmacies,
  });

  /** 打开更新库存弹窗 */
  const handleUpdate = (record: API.InventoryItem) => {
    setSelectedItem(record);
    setUpdateModalOpen(true);
  };

  /** 打开释放锁定弹窗 */
  const handleUnlock = (record: API.InventoryItem) => {
    setSelectedItem(record);
    setUnlockModalOpen(true);
  };

  /** 提交更新库存 */
  const handleUpdateSubmit = async (values: API.UpdateInventoryReq) => {
    if (!selectedItem) return;
    setSubmitting(true);
    try {
      await updateInventory(selectedItem.id!, values);
      message.success('库存更新成功');
      setUpdateModalOpen(false);
      actionRef.current?.reload();
    } catch (err: unknown) {
      message.error(getErrorMessage(err, '更新失败，请重试'));
    } finally {
      setSubmitting(false);
    }
  };

  /** 提交释放锁定 */
  const handleUnlockSubmit = async (values: API.UnlockInventoryReq) => {
    if (!selectedItem) return;
    setSubmitting(true);
    try {
      await unlockInventory(selectedItem.id!, values);
      message.success('锁定库存已释放');
      setUnlockModalOpen(false);
      actionRef.current?.reload();
    } catch (err: unknown) {
      message.error(getErrorMessage(err, '释放失败，请重试'));
    } finally {
      setSubmitting(false);
    }
  };

  const columns = getColumns({
    isAdmin,
    onUpdate: handleUpdate,
    onUnlock: handleUnlock,
  });

  return (
    <>
      <ProTable<API.InventoryItem, API.InventoryListParams>
        actionRef={actionRef}
        rowKey={(record) => record.id ?? `drug-${record.drugId}`}
        columns={columns}
        request={async (params) => {
          const { current, pageSize, ...rest } = params;
          try {
            const res = await getInventoryList({
              page: current,
              size: pageSize,
              drugId: rest.drugId,
              pharmacyId,
            });
            return { data: res.list, total: res.total, success: true };
          } catch (err: unknown) {
            // 查询失败时清空列表，避免残留上一次成功数据；success 置 true 以显示空表格
            message.error(getErrorMessage(err, '查询失败，请重试'));
            return { data: [], total: 0, success: true };
          }
        }}
        params={{ pharmacyId }}
        search={{
          labelWidth: 'auto',
          defaultCollapsed: true,
        }}
        pagination={{ pageSize: PAGE_SIZE_DEFAULT }}
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

      <UpdateInventoryModal
        open={updateModalOpen}
        item={selectedItem}
        submitting={submitting}
        onCancel={() => setUpdateModalOpen(false)}
        onSubmit={handleUpdateSubmit}
      />

      <UnlockInventoryModal
        open={unlockModalOpen}
        item={selectedItem}
        submitting={submitting}
        onCancel={() => setUnlockModalOpen(false)}
        onSubmit={handleUnlockSubmit}
      />
    </>
  );
}

/**
 * 库存管理页
 * - ProTable 列表，展示药品库存信息
 * - 支持按药房筛选；不选药房时汇总全部药房库存
 * - 库存状态（NORMAL/LOW/ALERT）前端根据 availableCount 与 safetyStock 计算
 * - ADMIN 角色可更新库存、手动释放锁定库存
 */
import { Tag, Button, Modal, message, Progress, Space, Descriptions, Select } from 'antd';
import { EditOutlined, UnlockOutlined } from '@ant-design/icons';
import { ProTable, ProForm, ProFormDigit, ProFormText, ProFormSelect } from '@ant-design/pro-components';
import { useModel } from '@umijs/max';
import { useRef, useState, useEffect } from 'react';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import {
  getInventoryList,
  updateInventory,
  unlockInventory,
  getDrugs,
  getPharmacies,
} from '@/services/admin';

/** 根据库存数量与安全库存计算状态 */
function calcStatus(available: number, safety: number): {
  status: 'NORMAL' | 'LOW' | 'ALERT';
  percent: number;
  color: string;
  label: string;
} {
  if (safety <= 0) {
    return { status: 'NORMAL', percent: 100, color: '#52c41a', label: '正常' };
  }
  const ratio = available / safety;
  if (ratio >= 2) {
    return { status: 'NORMAL', percent: Math.min(100, (available / (safety * 2)) * 100), color: '#52c41a', label: '正常' };
  }
  if (ratio >= 1) {
    return { status: 'LOW', percent: (available / safety) * 100, color: '#faad14', label: '偏低' };
  }
  return { status: 'ALERT', percent: (available / safety) * 100, color: '#ff4d4f', label: '告警' };
}

/** 分转元显示 */
function formatPrice(cent: number): string {
  return (cent / 100).toFixed(2);
}

export default function InventoryList() {
  const { initialState } = useModel('@@initialState');
  const isAdmin = initialState?.currentUser?.roles?.includes('ADMIN') ?? false;
  const actionRef = useRef<ActionType>();

  const [updateModalOpen, setUpdateModalOpen] = useState(false);
  const [unlockModalOpen, setUnlockModalOpen] = useState(false);
  const [selectedItem, setSelectedItem] = useState<API.InventoryItem | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [pharmacies, setPharmacies] = useState<API.PharmacyItem[]>([]);
  const [pharmacyId, setPharmacyId] = useState<number | undefined>(undefined);

  useEffect(() => {
    getPharmacies().then(setPharmacies).catch(() => {});
  }, []);

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
    } catch (err: any) {
      message.error(err?.message || '更新失败，请重试');
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
    } catch (err: any) {
      message.error(err?.message || '释放失败，请重试');
    } finally {
      setSubmitting(false);
    }
  };

  /** 获取药品列表（供搜索筛选） */
  const fetchDrugs = async () => {
    try {
      const res = await getDrugs({ page: 1, size: 200 });
      return (res.list ?? []).map((d) => ({
        label: d.name,
        value: d.id,
      }));
    } catch {
      return [];
    }
  };

  const columns: ProColumns<API.InventoryItem>[] = [
    {
      title: '药房',
      dataIndex: 'pharmacyName',
      width: 120,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '药品名称',
      dataIndex: 'drugName',
      width: 180,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '药品',
      dataIndex: 'drugId',
      hideInTable: true,
      renderFormItem: () => (
        <ProFormSelect
          name="drugId"
          noStyle
          request={fetchDrugs}
          placeholder="请选择药品"
          allowClear
        />
      ),
    },
    {
      title: '规格',
      dataIndex: 'specification',
      width: 130,
      hideInSearch: true,
    },
    {
      title: '库存数量',
      dataIndex: 'availableCount',
      width: 100,
      hideInSearch: true,
      sorter: true,
    },
    {
      title: '锁定数量',
      dataIndex: 'lockedCount',
      width: 100,
      hideInSearch: true,
    },
    {
      title: '安全库存',
      dataIndex: 'safetyStock',
      width: 100,
      hideInSearch: true,
    },
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
              onClick={() => handleUpdate(record)}
            >
              更新库存
            </Button>
          )}
          {isAdmin && record.id && record.lockedCount > 0 && (
            <Button
              type="link"
              size="small"
              icon={<UnlockOutlined />}
              onClick={() => handleUnlock(record)}
            >
              释放锁定
            </Button>
          )}
        </Space>
      ),
    },
  ];

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
              pharmacyId: pharmacyId,
            });
            return {
              data: res.list,
              total: res.total,
              success: true,
            };
          } catch (err: any) {
            message.error(err?.message || '查询失败，请重试');
            return { data: [], total: 0, success: true };
          }
        }}
        params={{ pharmacyId }}
        search={{
          labelWidth: 'auto',
          defaultCollapsed: true,
        }}
        pagination={{ pageSize: 10 }}
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

      {/* ====== 更新库存弹窗 ====== */}
      <Modal
        title="更新库存"
        open={updateModalOpen}
        footer={null}
        destroyOnClose
        onCancel={() => setUpdateModalOpen(false)}
        width={480}
      >
        {selectedItem && (
          <Descriptions size="small" column={1} style={{ marginBottom: 16 }}>
            <Descriptions.Item label="药房">{selectedItem.pharmacyName ?? '汇总'}</Descriptions.Item>
            <Descriptions.Item label="药品">{selectedItem.drugName}</Descriptions.Item>
            <Descriptions.Item label="规格">{selectedItem.specification}</Descriptions.Item>
            <Descriptions.Item label="当前库存">{selectedItem.availableCount}</Descriptions.Item>
            <Descriptions.Item label="锁定数量">{selectedItem.lockedCount}</Descriptions.Item>
          </Descriptions>
        )}
        <ProForm<API.UpdateInventoryReq>
          initialValues={
            selectedItem
              ? {
                  availableCount: selectedItem.availableCount,
                  safetyStock: selectedItem.safetyStock,
                  unitPriceCent: selectedItem.unitPriceCent,
                }
              : undefined
          }
          onFinish={handleUpdateSubmit}
          submitter={{
            submitButtonProps: { loading: submitting },
          }}
        >
          <ProFormDigit
            name="availableCount"
            label="库存数量"
            rules={[{ required: true, message: '请输入库存数量' }]}
            min={0}
            max={9999999}
          />
          <ProFormDigit
            name="safetyStock"
            label="安全库存"
            rules={[{ required: true, message: '请输入安全库存' }]}
            min={0}
            max={9999999}
          />
          <ProFormDigit
            name="unitPriceCent"
            label="单价（分）"
            rules={[{ required: true, message: '请输入单价' }]}
            min={0}
            max={999999999}
            fieldProps={{
              addonAfter: '分（如 1500 分 = 15 元）',
            }}
          />
        </ProForm>
      </Modal>

      {/* ====== 释放锁定库存弹窗 ====== */}
      <Modal
        title="释放锁定库存"
        open={unlockModalOpen}
        footer={null}
        destroyOnClose
        onCancel={() => setUnlockModalOpen(false)}
        width={480}
      >
        {selectedItem && (
          <Descriptions size="small" column={1} style={{ marginBottom: 16 }}>
            <Descriptions.Item label="药房">{selectedItem.pharmacyName ?? '汇总'}</Descriptions.Item>
            <Descriptions.Item label="药品">{selectedItem.drugName}</Descriptions.Item>
            <Descriptions.Item label="当前锁定数量">{selectedItem.lockedCount}</Descriptions.Item>
          </Descriptions>
        )}
        <ProForm<API.UnlockInventoryReq>
          onFinish={handleUnlockSubmit}
          submitter={{
            submitButtonProps: { loading: submitting },
          }}
        >
          <ProFormDigit
            name="drugOrderId"
            label="药品订单ID"
            rules={[{ required: true, message: '请输入药品订单ID' }]}
            min={1}
            placeholder="请输入关联的药品订单ID"
          />
          <ProFormText
            name="reason"
            label="释放原因"
            rules={[
              { required: true, message: '请输入释放原因' },
              { max: 200, message: '最多 200 个字符' },
            ]}
            placeholder="如：支付超时人工补偿"
          />
        </ProForm>
      </Modal>
    </>
  );
}
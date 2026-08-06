/**
 * 药品目录页
 * - ProTable 列表，支持名称模糊搜索、状态筛选
 * - ADMIN 角色可新增/编辑/启用停用
 */
import { Button, Modal, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import { useRef, useState } from 'react';
import type { ActionType, ProFormInstance } from '@ant-design/pro-components';
import { getDrugs, createDrug, updateDrug, updateDrugStatus } from '@/services/admin';
import { useHasRole } from '@/hooks/useCurrentUser';
import { getErrorMessage } from '@/utils/error';
import { getColumns } from './columns';
import DrugFormModal from './DrugFormModal';

export default function DrugCatalog() {
  const isAdmin = useHasRole('ADMIN');
  const actionRef = useRef<ActionType>();
  const formRef = useRef<ProFormInstance>();
  const searchParamsRef = useRef<Partial<API.DrugListParams>>({});

  const [modalOpen, setModalOpen] = useState(false);
  const [editingDrug, setEditingDrug] = useState<API.Drug | null>(null);
  const [submitting, setSubmitting] = useState(false);

  /** 打开新增弹窗 */
  const handleAdd = () => {
    setEditingDrug(null);
    setModalOpen(true);
  };

  /** 打开编辑弹窗 */
  const handleEdit = (record: API.Drug) => {
    setEditingDrug(record);
    setModalOpen(true);
  };

  /** 提交新增/编辑 */
  const handleSubmit = async (values: API.CreateDrugReq) => {
    setSubmitting(true);
    try {
      if (editingDrug) {
        await updateDrug(editingDrug.id, values);
        message.success('药品更新成功');
      } else {
        await createDrug(values);
        message.success('药品新增成功');
      }
      setModalOpen(false);
      actionRef.current?.reload();
    } catch (err: unknown) {
      message.error(getErrorMessage(err, '操作失败，请重试'));
    } finally {
      setSubmitting(false);
    }
  };

  /** 切换药品状态（启用/停用二次确认） */
  const handleToggleStatus = (record: API.Drug) => {
    const newStatus = record.status === 'ENABLED' ? 'DISABLED' : 'ENABLED';
    const actionLabel = newStatus === 'ENABLED' ? '启用' : '停用';

    Modal.confirm({
      title: `${actionLabel}药品`,
      content: `确定要${actionLabel}药品「${record.name}」吗？`,
      onOk: async () => {
        try {
          await updateDrugStatus(record.id, newStatus);
          message.success(`${actionLabel}成功`);
          actionRef.current?.reload();
        } catch (err: unknown) {
          message.error(getErrorMessage(err, `${actionLabel}失败`));
        }
      },
    });
  };

  const columns = getColumns({
    isAdmin,
    onEdit: handleEdit,
    onToggleStatus: handleToggleStatus,
  });

  return (
    <>
      <ProTable<API.Drug, API.DrugListParams>
        actionRef={actionRef}
        formRef={formRef}
        rowKey="id"
        columns={columns}
        request={async (params) => {
          const { current, pageSize } = params;
          const sp = searchParamsRef.current;
          try {
            const res = await getDrugs({
              page: current,
              size: pageSize,
              name: sp.name,
              status: sp.status || undefined,
            });
            return { data: res.list, total: res.total, success: true };
          } catch (err: unknown) {
            // 查询失败时清空列表，避免残留上一次成功数据；success 置 true 以显示空表格
            message.error(getErrorMessage(err, '查询失败，请重试'));
            return { data: [], total: 0, success: true };
          }
        }}
        search={{
          labelWidth: 'auto',
          defaultCollapsed: true,
        }}
        beforeSearchSubmit={(values) => {
          // 在 request 之前保存查询参数，供分页时使用
          const used: Partial<API.DrugListParams> = {};
          if (values.name) used.name = values.name;
          if (values.status) used.status = values.status;
          searchParamsRef.current = used;

          // 清空未使用的查询字段，避免旧值残留
          const cleared: Partial<API.DrugListParams> = {};
          if (!values.name) cleared.name = undefined;
          if (!values.status) cleared.status = undefined;
          formRef.current?.setFieldsValue(cleared);
          return values;
        }}
        toolBarRender={() =>
          isAdmin
            ? [
                <Button
                  key="add"
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={handleAdd}
                >
                  新增药品
                </Button>,
              ]
            : []
        }
        pagination={{ pageSize: 10 }}
      />

      <DrugFormModal
        open={modalOpen}
        editingDrug={editingDrug}
        submitting={submitting}
        onCancel={() => setModalOpen(false)}
        onSubmit={handleSubmit}
      />
    </>
  );
}

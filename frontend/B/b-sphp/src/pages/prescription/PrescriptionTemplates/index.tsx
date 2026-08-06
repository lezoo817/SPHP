/**
 * 处方模板页
 * - ProTable 列表，支持模板名称模糊搜索、科室筛选
 * - 新建模板弹窗（填写名称、科室、药品明细）
 * - 查看详情弹窗
 * - 删除模板
 */
import { Button, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import type { ActionType } from '@ant-design/pro-components';
import { useMemo, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { getTemplates, deleteTemplate, getDepartments } from '@/services/admin';
import { useCurrentUser } from '@/hooks/useCurrentUser';
import { getErrorMessage } from '@/utils/error';
import { QUERY_KEYS, STALE_TIME } from '@/constants/queryKeys';
import { getColumns } from './columns';
import TemplateDetailModal from './TemplateDetailModal';
import TemplateCreateModal from './TemplateCreateModal';

export default function PrescriptionTemplates() {
  const currentUser = useCurrentUser();
  const actionRef = useRef<ActionType>();

  // 详情弹窗
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailData, setDetailData] = useState<API.PrescriptionTemplate | null>(null);

  // 新建弹窗
  const [createOpen, setCreateOpen] = useState(false);

  /** 科室选项（供筛选下拉与新建弹窗），由 React Query 拉取 */
  const { data: deptRes } = useQuery({
    queryKey: QUERY_KEYS.departments,
    queryFn: () => getDepartments({ page: 1, size: 200 }),
    staleTime: STALE_TIME.departments,
  });
  const deptOptions = useMemo(
    () => (deptRes?.list ?? []).map((dept) => ({ label: dept.name, value: dept.id })),
    [deptRes],
  );

  /** 查看详情 */
  const handleViewDetail = (record: API.PrescriptionTemplate) => {
    setDetailData(record);
    setDetailOpen(true);
  };

  /** 删除模板 */
  const handleDelete = async (id: number) => {
    try {
      await deleteTemplate(id);
      message.success('模板已删除');
      actionRef.current?.reload();
    } catch (err: unknown) {
      message.error(getErrorMessage(err, '删除失败'));
    }
  };

  const columns = getColumns({
    deptOptions,
    onViewDetail: handleViewDetail,
    onDelete: handleDelete,
  });

  return (
    <>
      <ProTable<API.PrescriptionTemplate, API.TemplateListParams>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={async (params) => {
          const { current, pageSize, ...rest } = params;
          try {
            const res = await getTemplates({
              page: current,
              size: pageSize,
              name: rest.name,
              deptId: rest.deptId,
            });
            return { data: res.list, total: res.total, success: true };
          } catch (err: unknown) {
            message.error(getErrorMessage(err, '查询失败'));
            return { data: [], total: 0, success: true };
          }
        }}
        search={{
          labelWidth: 'auto',
          span: 8,
          defaultFormItemsNumber: 2,
        }}
        toolBarRender={() => [
          <Button
            key="add"
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setCreateOpen(true)}
          >
            新建模板
          </Button>,
        ]}
        pagination={{ pageSize: 10, showSizeChanger: true }}
      />

      <TemplateDetailModal
        open={detailOpen}
        data={detailData}
        onCancel={() => setDetailOpen(false)}
      />

      <TemplateCreateModal
        open={createOpen}
        defaultDeptId={currentUser?.deptId}
        deptOptions={deptOptions}
        onCancel={() => setCreateOpen(false)}
        onSuccess={() => {
          setCreateOpen(false);
          actionRef.current?.reload();
        }}
      />
    </>
  );
}

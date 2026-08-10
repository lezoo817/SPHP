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
import TemplateEditModal from './TemplateEditModal';
import { PAGE_SIZE_200, PAGE_SIZE_DEFAULT } from '@/constants/pageSize';
import { ROLE_ADMIN, ROLE_DEPT_HEAD } from '@/constants/businessStatus';

export default function PrescriptionTemplates() {
  const currentUser = useCurrentUser();
  const actionRef = useRef<ActionType>();

  // 详情弹窗
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailData, setDetailData] = useState<API.PrescriptionTemplate | null>(null);

  // 新建弹窗
  const [createOpen, setCreateOpen] = useState(false);

  // 编辑弹窗
  const [editOpen, setEditOpen] = useState(false);
  const [editData, setEditData] = useState<API.PrescriptionTemplate | null>(null);

  /** 科室选项（供筛选下拉与新建弹窗），由 React Query 拉取 */
  const { data: deptRes } = useQuery({
    queryKey: QUERY_KEYS.departments,
    queryFn: () => getDepartments({ page: 1, size: PAGE_SIZE_200 }),
    staleTime: STALE_TIME.departments,
  });
  const deptOptions = useMemo(
    () => (deptRes?.list ?? []).map((dept) => ({ label: dept.name, value: dept.id })),
    [deptRes],
  );

  // 角色与数据权限：非 ADMIN 收敛到本人科室（后端同时强制，前端仅收敛可选项与筛选，避免误导）
  const isAdmin = useMemo(() => currentUser?.roles?.includes(ROLE_ADMIN) ?? false, [currentUser]);
  const isDeptHead = useMemo(
    () => currentUser?.roles?.includes(ROLE_DEPT_HEAD) ?? false,
    [currentUser],
  );
  const ownDeptId = currentUser?.deptId;
  // 可选科室下拉：ADMIN 全部；DEPT_HEAD/DOCTOR 仅本人科室（是否可选「全院通用」由 allowHospitalWide 控制）
  const manageableDeptOptions = useMemo(() => {
    if (isAdmin) return deptOptions;
    return deptOptions.filter((d) => d.value === ownDeptId);
  }, [deptOptions, isAdmin, ownDeptId]);
  // 可选「全院通用」（清空科室）：ADMIN / DEPT_HEAD 可；DOCTOR 固定本科室
  const allowHospitalWide = isAdmin || isDeptHead;

  /** 查看详情 */
  const handleViewDetail = (record: API.PrescriptionTemplate) => {
    setDetailData(record);
    setDetailOpen(true);
  };

  /** 编辑模板 */
  const handleEdit = (record: API.PrescriptionTemplate) => {
    setEditData(record);
    setEditOpen(true);
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
    showDeptFilter: isAdmin,
    canManageRecord: (record) => {
      if (isAdmin) return true;
      // DEPT_HEAD：本科室或全院通用（deptId 空即全院通用）
      if (isDeptHead) return !record.deptId || record.deptId === ownDeptId;
      // DOCTOR：仅本科室
      return record.deptId === ownDeptId;
    },
    onViewDetail: handleViewDetail,
    onEdit: handleEdit,
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
        pagination={{ pageSize: PAGE_SIZE_DEFAULT, showSizeChanger: true }}
      />

      <TemplateDetailModal
        open={detailOpen}
        data={detailData}
        onCancel={() => setDetailOpen(false)}
      />

      <TemplateCreateModal
        open={createOpen}
        defaultDeptId={currentUser?.deptId}
        deptOptions={manageableDeptOptions}
        allowHospitalWide={allowHospitalWide}
        onCancel={() => setCreateOpen(false)}
        onSuccess={() => {
          setCreateOpen(false);
          actionRef.current?.reload();
        }}
      />

      <TemplateEditModal
        open={editOpen}
        record={editData}
        deptOptions={manageableDeptOptions}
        allowHospitalWide={allowHospitalWide}
        onCancel={() => setEditOpen(false)}
        onSuccess={() => {
          setEditOpen(false);
          actionRef.current?.reload();
        }}
      />
    </>
  );
}

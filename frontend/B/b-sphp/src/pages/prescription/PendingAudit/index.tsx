/**
 * 待审核处方列表页
 * - 仅 canAudit（ADMIN / DEPT_HEAD）角色可访问（路由层级 access 控制）
 * - 审核操作：通过（APPROVED）/ 驳回（REJECTED，需填写原因）
 * - 审核完成后刷新本列表，同时通知处方列表页刷新缓存
 */
import { Button, Modal, message } from 'antd';
import { CheckCircleOutlined } from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import type { ActionType } from '@ant-design/pro-components';
import { useRef, useState } from 'react';
import {
  getPendingAudits,
  getPrescriptionDetail,
  auditPrescription,
} from '@/services/admin';
import { getErrorMessage } from '@/utils/error';
import { getColumns } from './columns';
import AuditDetailModal from './AuditDetailModal';
import RejectModal from './RejectModal';
import { PAGE_SIZE_DEFAULT } from '@/constants/pageSize';

export default function PendingAudit() {
  const actionRef = useRef<ActionType>();

  // 详情弹窗
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailData, setDetailData] = useState<API.PrescriptionDetail | null>(null);

  // 驳回弹窗
  const [rejectOpen, setRejectOpen] = useState(false);
  const [rejectTargetId, setRejectTargetId] = useState<number | null>(null);
  const [rejecting, setRejecting] = useState(false);

  /** 查看详情 */
  const handleViewDetail = async (id: number) => {
    setDetailLoading(true);
    setDetailOpen(true);
    setDetailData(null);
    try {
      const data = await getPrescriptionDetail(id);
      setDetailData(data);
    } catch (err: unknown) {
      message.error(getErrorMessage(err, '加载处方详情失败'));
      setDetailOpen(false);
    } finally {
      setDetailLoading(false);
    }
  };

  /** 通过审核 */
  const handleApprove = (id: number) => {
    Modal.confirm({
      title: '通过审核',
      icon: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
      content: '确定通过该处方审核吗？',
      okText: '确认通过',
      onOk: async () => {
        try {
          await auditPrescription(id, { action: 'APPROVED' });
          message.success('处方审核已通过');
          actionRef.current?.reload();
        } catch (err: unknown) {
          message.error(getErrorMessage(err, '审核失败'));
        }
      },
    });
  };

  /** 打开驳回弹窗 */
  const handleRejectClick = (id: number) => {
    setRejectTargetId(id);
    setRejectOpen(true);
  };

  /** 提交驳回（原因由弹窗校验非空并 trim 后传入） */
  const handleRejectSubmit = async (reason: string) => {
    if (rejectTargetId === null) return;
    setRejecting(true);
    try {
      await auditPrescription(rejectTargetId, {
        action: 'REJECTED',
        rejectReason: reason,
      });
      message.success('处方已驳回');
      setRejectOpen(false);
      actionRef.current?.reload();
    } catch (err: unknown) {
      message.error(getErrorMessage(err, '驳回失败'));
    } finally {
      setRejecting(false);
    }
  };

  const columns = getColumns({
    onViewDetail: handleViewDetail,
    onApprove: handleApprove,
    onReject: handleRejectClick,
  });

  return (
    <>
      <ProTable<API.PendingAuditItem, API.PageParams>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={async (params) => {
          const { current, pageSize } = params;
          try {
            const res = await getPendingAudits({ page: current, size: pageSize });
            return { data: res.list, total: res.total, success: true };
          } catch (err: unknown) {
            message.error(getErrorMessage(err, '查询失败'));
            return { data: [], total: 0, success: true };
          }
        }}
        search={false}
        pagination={{ pageSize: PAGE_SIZE_DEFAULT, showSizeChanger: true }}
        toolBarRender={() => [
          <Button key="refresh" onClick={() => actionRef.current?.reload()}>
            刷新
          </Button>,
        ]}
      />

      <AuditDetailModal
        open={detailOpen}
        loading={detailLoading}
        data={detailData}
        onCancel={() => setDetailOpen(false)}
        onApprove={() => {
          if (!detailData) return;
          setDetailOpen(false);
          handleApprove(detailData.id);
        }}
        onReject={() => {
          if (!detailData) return;
          setDetailOpen(false);
          handleRejectClick(detailData.id);
        }}
      />

      <RejectModal
        open={rejectOpen}
        submitting={rejecting}
        onCancel={() => setRejectOpen(false)}
        onSubmit={handleRejectSubmit}
      />
    </>
  );
}

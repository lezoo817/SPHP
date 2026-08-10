/**
 * 处方列表页
 * - ProTable 列表，支持问诊ID/患者ID/状态筛选
 * - DRAFT 状态可提交处方（调用 POST /api/b/prescriptions）
 * - 提交后处理红线拦截 / 待审核（含重复用药、高危风险）两种结果
 * - 查看详情弹窗
 */
import { Modal, message } from 'antd';
import { ProTable } from '@ant-design/pro-components';
import type { ActionType } from '@ant-design/pro-components';
import { useNavigate, useLocation } from '@umijs/max';
import { useMemo, useRef, useState } from 'react';
import {
  getPrescriptions,
  getPrescriptionDetail,
  submitPrescription,
} from '@/services/admin';
import { getErrorMessage } from '@/utils/error';
import { getColumns } from './columns';
import PrescriptionDetailModal from './PrescriptionDetailModal';
import { PAGE_SIZE_DEFAULT } from '@/constants/pageSize';

export default function PrescriptionList() {
  const navigate = useNavigate();
  const location = useLocation();
  const actionRef = useRef<ActionType>();

  // 详情弹窗
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailData, setDetailData] = useState<API.PrescriptionDetail | null>(null);

  /** 从 URL 参数派生状态筛选（如 /prescription/list?status=SUBMITTED 深链进入），URL 驱动、派生渲染 */
  const urlStatus = useMemo(
    () => new URLSearchParams(location.search).get('status') || undefined,
    [location.search],
  );
  /** 透传给 ProTable 的筛选参数（无 URL 状态时保持稳定引用，避免多余请求） */
  const proParams = useMemo(() => (urlStatus ? { status: urlStatus } : {}), [urlStatus]);

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

  /** 提交处方 */
  const handleSubmit = async (record: API.Prescription) => {
    Modal.confirm({
      title: '提交处方',
      content: `确定提交处方 #${record.id} 吗？提交后进入审核流程。`,
      okText: '确认提交',
      onOk: async () => {
        try {
          // 先获取详情拿到完整 items，再提交
          const detail = await getPrescriptionDetail(record.id);
          const result = await submitPrescription({
            consultId: detail.consultId,
            items: detail.items.map((item) => ({
              drugId: item.drugId,
              dosage: item.dosage,
              frequency: item.frequency,
              usageMethod: item.usageMethod,
              days: item.days,
              quantity: item.quantity,
            })),
          });

          // 红线拦截 — 理论上不会走到这里（后端返回 3004 抛异常），兜底处理
          if (result.riskWarnings?.some((w) => w.level === 'ERROR')) {
            const errMsg = result.riskWarnings.find((w) => w.level === 'ERROR')?.message;
            message.error(errMsg || '红线规则拦截，请修改后重提');
            return;
          }

          if (result.auditRequired) {
            // 命中风险规则（重复用药/高危）→ 进入待审核
            Modal.success({
              title: '提交成功',
              content: '处方已提交审核，请等待审核结果',
              okText: '前往待审核列表',
              onOk: () => navigate('/prescription/pending-audit'),
            });
          } else {
            message.success('处方提交成功');
          }

          actionRef.current?.reload();
        } catch (err: unknown) {
          // 处理 code 3004 红线拦截（后端拦截器抛 Error 携带 message）
          Modal.error({
            title: '提交被拦截',
            content: getErrorMessage(err, '提交失败'),
            okText: '修改后重提',
          });
        }
      },
    });
  };

  const columns = getColumns({
    onViewDetail: handleViewDetail,
    onSubmit: handleSubmit,
  });

  return (
    <>
      <ProTable<API.Prescription, API.PrescriptionListParams>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        params={proParams}
        request={async (params) => {
          const { current, pageSize, ...rest } = params;
          try {
            const res = await getPrescriptions({
              page: current,
              size: pageSize,
              consultId: rest.consultId,
              patientId: rest.patientId,
              status: rest.status,
            });
            return { data: res.list, total: res.total, success: true };
          } catch (err: unknown) {
            message.error(getErrorMessage(err, '查询失败'));
            return { data: [], total: 0, success: true };
          }
        }}
        search={{
          labelWidth: 'auto',
          span: 6,
          defaultFormItemsNumber: 4,
        }}
        pagination={{ pageSize: PAGE_SIZE_DEFAULT, showSizeChanger: true }}
      />

      <PrescriptionDetailModal
        open={detailOpen}
        loading={detailLoading}
        data={detailData}
        onCancel={() => setDetailOpen(false)}
      />
    </>
  );
}

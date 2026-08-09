/**
 * 排班列表页
 * - ProTable 列表，支持日期/科室/医生/状态筛选
 * - 写操作（新增/批量创建/批量发布/配置时段/发布/取消发布/作废）仅 ADMIN
 * - 业务铁律：PUBLISHED 状态下增/删/改类按钮置灰禁用并附 Tooltip「排班已发布，不可修改」
 */
import { Button, Checkbox, Form, Modal, message } from 'antd';
import { PlusOutlined, SendOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import type { ActionType } from '@ant-design/pro-components';
import { useNavigate } from '@umijs/max';
import { useRef, useState } from 'react';
import {
  createSchedule,
  getSchedules,
  publishSchedule,
  unpublishSchedule,
} from '@/services/admin';
import { useHasRole } from '@/hooks/useCurrentUser';
import { getErrorMessage } from '@/utils/error';
import { getShiftConfig } from '../constants';
import { getColumns } from './columns';
import ScheduleFormModal from './ScheduleFormModal';
import BatchScheduleModal from './BatchScheduleModal';
import BatchPublishModal from './BatchPublishModal';
import type { Dayjs } from 'dayjs';

export default function ScheduleList() {
  const isAdmin = useHasRole('ADMIN');
  const navigate = useNavigate();
  const actionRef = useRef<ActionType>();

  const [createOpen, setCreateOpen] = useState(false);
  const [batchOpen, setBatchOpen] = useState(false);
  const [batchPublishOpen, setBatchPublishOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  /** 跳转排班详情页（携带元信息，供详情页展示与状态判断，刷新后仍可恢复） */
  const goDetail = (record: API.Schedule) => {
    const query = new URLSearchParams({
      status: record.status,
      scheduleDate: record.scheduleDate,
      shift: record.shift,
      totalSlots: String(record.totalSlots),
      doctorName: record.doctorName ?? '',
    });
    navigate(`/schedule/detail/${record.id}?${query.toString()}`);
  };

  /** 发布排班 */
  const handlePublish = (record: API.Schedule) => {
    Modal.confirm({
      title: '发布排班',
      content: `确定发布「${record.doctorName}」${record.scheduleDate} ${
        getShiftConfig(record.shift)?.text ?? record.shift
      } 的排班吗？发布后号源将对患者开放预约。`,
      okText: '确认发布',
      onOk: async () => {
        try {
          await publishSchedule(record.id);
          message.success('排班发布成功');
          actionRef.current?.reload();
        } catch (err: unknown) {
          message.error(getErrorMessage(err, '发布失败'));
        }
      },
    });
  };

  /** 取消发布（PUBLISHED） */
  const handleUnpublish = (record: API.Schedule) => {
    Modal.confirm({
      title: '取消发布排班',
      content: `确定取消发布「${record.doctorName}」${record.scheduleDate} 的排班吗？取消后该排班将停止对外预约，已锁定的号源将被释放。`,
      okText: '确认取消发布',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await unpublishSchedule(record.id);
          message.success('已取消发布');
          actionRef.current?.reload();
        } catch (err: unknown) {
          message.error(getErrorMessage(err, '操作失败'));
        }
      },
    });
  };

  /** 作废排班（DRAFT，即删除） */
  const handleCancel = (record: API.Schedule) => {
    Modal.confirm({
      title: '作废排班',
      content: `作废后「${record.doctorName}」${record.scheduleDate} 的排班将不可恢复（终态）。确定作废吗？`,
      okText: '确认作废',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await unpublishSchedule(record.id);
          message.success('排班已作废');
          actionRef.current?.reload();
        } catch (err: unknown) {
          message.error(getErrorMessage(err, '作废失败'));
        }
      },
    });
  };

  /** 新增排班提交 */
  const handleCreateSubmit = async (payload: API.CreateScheduleReq) => {
    setSubmitting(true);
    try {
      await createSchedule(payload);
      message.success(payload.publishImmediately ? '排班创建成功并已发布' : '排班创建成功');
      setCreateOpen(false);
      actionRef.current?.reload();
    } catch (err: unknown) {
      message.error(getErrorMessage(err, '创建失败，请重试'));
    } finally {
      setSubmitting(false);
    }
  };

  /** 日期参数归一化：ProTable 可能传入 dayjs 或字符串 */
  const toDateParam = (v: unknown): string | undefined => {
    if (!v) return undefined;
    if (typeof v === 'string') return v;
    return (v as Dayjs).format('YYYY-MM-DD');
  };

  const columns = getColumns({
    isAdmin,
    onGoDetail: goDetail,
    onPublish: handlePublish,
    onUnpublish: handleUnpublish,
    onCancel: handleCancel,
  });

  return (
    <>
      <ProTable<API.Schedule, API.ScheduleListParams>
        actionRef={actionRef}
        rowKey="id"
        columns={columns}
        request={async (params) => {
          const { current, pageSize, ...rest } = params;
          try {
            const res = await getSchedules({
              page: current,
              size: pageSize,
              date: toDateParam(rest.date),
              deptId: rest.deptId,
              doctorId: rest.doctorId,
              status: rest.status,
              hideInvalid: rest.hideInvalid,
            });
            return { data: res.list, total: res.total, success: true };
          } catch (err: unknown) {
            message.error(getErrorMessage(err, '查询失败，请重试'));
            return { data: [], total: 0, success: true };
          }
        }}
        search={{
          labelWidth: 'auto',
          // span=6 → 一行放 4 个筛选项；defaultFormItemsNumber=4 使全部默认展示
          span: 6,
          defaultFormItemsNumber: 4,
          // 在"重置/查询"按钮左侧插入"过滤失效"勾选：与 status 筛 AND 组合
          optionRender: (searchConfig, formProps, dom) => [
            <Form.Item
              key="hideInvalid"
              name="hideInvalid"
              valuePropName="checked"
              noStyle
              style={{ marginRight: 12 }}
            >
              <Checkbox>过滤失效的排班信息</Checkbox>
            </Form.Item>,
            ...dom,
          ],
        }}
        toolBarRender={() =>
          isAdmin
            ? [
                <Button
                  key="add"
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => setCreateOpen(true)}
                >
                  新增排班
                </Button>,
                <Button
                  key="batch"
                  icon={<ThunderboltOutlined />}
                  onClick={() => setBatchOpen(true)}
                >
                  批量排班
                </Button>,
                <Button
                  key="batch-publish"
                  icon={<SendOutlined />}
                  onClick={() => setBatchPublishOpen(true)}
                >
                  批量发布
                </Button>,
              ]
            : []
        }
        pagination={{ pageSize: 5, showSizeChanger: false }}
      />

      <ScheduleFormModal
        open={createOpen}
        submitting={submitting}
        onCancel={() => setCreateOpen(false)}
        onSubmit={handleCreateSubmit}
      />

      <BatchScheduleModal
        open={batchOpen}
        onCancel={() => setBatchOpen(false)}
        onCreated={() => actionRef.current?.reload()}
      />

      <BatchPublishModal
        open={batchPublishOpen}
        onCancel={() => setBatchPublishOpen(false)}
        onPublished={() => actionRef.current?.reload()}
      />
    </>
  );
}

/**
 * 批量排班弹窗。
 *
 * 交互流程：
 * 1. 上半段 ProForm 录入（医生 / 日期范围 / 星期模式 / 班次 / 号源 / 拆分方式 / 是否立即发布）
 * 2. 表单值变化时 debounce 300ms 调 preview 接口，下半段实时展示预览表
 * 3. 提交按钮按预览结果动态禁用/启用，文案显示「将创建 N 条，跳过 M 条」
 * 4. 创建成功后弹结果详情（新建/复用/跳过分类）；勾选「立即发布」时串联 batchPublish（仅对新建项）
 *
 * 设计要点：
 * - 与 ScheduleFormModal 共享医生/科室联动逻辑，但弹窗结构与状态机完全独立
 * - 预览表用原生 AntD Table（小数据量：≤90 天 × 2 班次 = 180 行），避免 ProTable 体积过重
 * - submitter 渲染 false；底部自绘「取消/确认」按钮以便控制禁用态
 */
import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Form, Modal, Space, Table, Tag, message } from 'antd';
import {
  ProForm,
  ProFormSelect,
  ProFormDigit,
  ProFormCheckbox,
  ProFormDateRangePicker,
} from '@ant-design/pro-components';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import {
  getDepartments,
  getDoctors,
  previewBatchSchedule,
  createBatchSchedule,
  batchPublishSchedules,
} from '@/services/admin';
import { getErrorMessage } from '@/utils/error';
import {
  getShiftConfig,
  getBatchActionConfig,
  SLOT_SPLIT_OPTIONS,
  WEEKDAY_OPTIONS,
} from '../constants';

interface BatchFormValues {
  deptId?: number;
  doctorId?: number;
  dateRange?: [Dayjs, Dayjs];
  weekdays: number[];
  shifts: ('MORNING' | 'AFTERNOON')[];
  totalSlots: number;
  slotSplitMode: 'HOURLY' | 'HALF_HOUR' | 'FULL';
  /** 创建成功后立即发布新创建的草稿（复用 CANCELLED 的不在发布范围内，避免打扰历史数据） */
  publishImmediately: boolean;
}

interface Props {
  open: boolean;
  onCancel: () => void;
  onCreated?: () => void;
}

const DEBOUNCE_MS = 300;

export default function BatchScheduleModal({ open, onCancel, onCreated }: Props) {
  const [form] = Form.useForm<BatchFormValues>();
  const watched = Form.useWatch([], form);
  const deptId = Form.useWatch('deptId', form);

  const [preview, setPreview] = useState<API.BatchPreviewResp | null>(null);
  const [previewing, setPreviewing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  /** 打开弹窗时重置表单与预览状态 */
  useEffect(() => {
    if (open) {
      form.resetFields();
      setPreview(null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  /** 科室变化时清空已选医生，避免医生与所选科室不一致的脏数据 */
  useEffect(() => {
    form.setFieldsValue({ doctorId: undefined });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deptId]);

  /** 表单值变化时 debounce 触发预览 */
  useEffect(() => {
    if (!open) return;
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }
    debounceRef.current = setTimeout(() => {
      void runPreview();
    }, DEBOUNCE_MS);
    return () => {
      if (debounceRef.current) {
        clearTimeout(debounceRef.current);
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [JSON.stringify(watched), open]);

  /** 校验必填后调 preview 接口；缺字段时静默清空预览 */
  const runPreview = async () => {
    const v = form.getFieldsValue();
    if (
      !v.doctorId ||
      !v.dateRange ||
      !v.weekdays?.length ||
      !v.shifts?.length ||
      !v.totalSlots ||
      !v.slotSplitMode
    ) {
      setPreview(null);
      return;
    }
    setPreviewing(true);
    try {
      const resp = await previewBatchSchedule({
        doctorId: v.doctorId,
        startDate: v.dateRange[0].format('YYYY-MM-DD'),
        endDate: v.dateRange[1].format('YYYY-MM-DD'),
        weekdays: v.weekdays,
        shifts: v.shifts,
        totalSlots: v.totalSlots,
        slotSplitMode: v.slotSplitMode,
      });
      setPreview(resp);
    } catch (err: unknown) {
      setPreview(null);
      message.warning(getErrorMessage(err, '预览失败'));
    } finally {
      setPreviewing(false);
    }
  };

  /** 科室选项 */
  const fetchDepartments = async () => {
    try {
      const res = await getDepartments({ page: 1, size: 200 });
      return (res.list ?? []).map((d) => ({ label: d.name, value: d.id }));
    } catch {
      return [];
    }
  };

  /** 医生选项（按所选科室联动过滤；未传 deptId 时拉取全院启用医生） */
  const fetchDoctors = async (keyword: string, deptId?: number) => {
    try {
      const res = await getDoctors({
        deptId,
        name: keyword || undefined,
        status: 'ENABLED',
        page: 1,
        size: 100,
      });
      return (res.list ?? []).map((d) => ({
        label: `${d.name}（${d.title}）`,
        value: d.id,
      }));
    } catch {
      return [];
    }
  };

  /** 提交 */
  const handleSubmit = async () => {
    const v = await form.validateFields();
    // ProForm 校验规则在运行时保证必填，这里同步收窄可选类型并防止配置遗漏后提交空值。
    if (!v.doctorId || !v.dateRange) {
      message.warning('请选择医生和排班日期范围');
      return;
    }
    setSubmitting(true);
    try {
      const report = await createBatchSchedule({
        doctorId: v.doctorId,
        startDate: v.dateRange[0].format('YYYY-MM-DD'),
        endDate: v.dateRange[1].format('YYYY-MM-DD'),
        weekdays: v.weekdays,
        shifts: v.shifts,
        totalSlots: v.totalSlots,
        slotSplitMode: v.slotSplitMode,
      });
      // 立即发布：仅对本次新建的草稿（不含复用 CANCELLED）调用，避免在用户不知情下改动历史状态
      let publishSummary = '';
      if (v.publishImmediately && report.createdItems.length > 0) {
        const publishReport = await batchPublishSchedules({
          scheduleIds: report.createdItems.map((it) => it.scheduleId),
        });
        const publishParts: string[] = [];
        if (publishReport.publishedCount)
          publishParts.push(`发布 ${publishReport.publishedCount} 条`);
        if (publishReport.failedCount)
          publishParts.push(`发布失败 ${publishReport.failedCount} 条`);
        publishSummary = publishParts.length ? `；${publishParts.join('，')}` : '；全部发布成功';
      }
      const parts: string[] = [];
      if (report.createdCount) parts.push(`新建 ${report.createdCount} 条`);
      if (report.reusedCount) parts.push(`复用 ${report.reusedCount} 条`);
      if (report.skippedCount) parts.push(`跳过 ${report.skippedCount} 条`);
      message.success((parts.length ? parts.join('，') : '批量创建完成') + publishSummary);
      onCreated?.();
      onCancel();
    } catch (err: unknown) {
      message.error(getErrorMessage(err, '批量创建失败'));
    } finally {
      setSubmitting(false);
    }
  };

  /** 预览表列定义 */
  const columns: ColumnsType<API.BatchPreviewItem> = useMemo(
    () => [
      {
        title: '日期',
        dataIndex: 'scheduleDate',
        key: 'scheduleDate',
        width: 130,
        render: (v: string) => dayjs(v).format('YYYY-MM-DD ddd'),
      },
      {
        title: '班次',
        dataIndex: 'shift',
        key: 'shift',
        width: 90,
        render: (v: string) => getShiftConfig(v)?.text ?? v,
      },
      {
        title: '去向',
        dataIndex: 'action',
        key: 'action',
        width: 100,
        render: (v: 'CREATE' | 'REUSE' | 'SKIP') => {
          const cfg = getBatchActionConfig(v);
          return <Tag color={cfg?.color}>{cfg?.text ?? v}</Tag>;
        },
      },
      {
        title: '备注',
        dataIndex: 'skipReason',
        key: 'skipReason',
        render: (v?: string) => v ?? '—',
      },
    ],
    [],
  );

  const canCreate = !!preview && preview.toCreateCount > 0;
  const submitText = preview
    ? `确认创建 ${preview.toCreateCount} 条${preview.toSkipCount ? `（跳过 ${preview.toSkipCount} 条）` : ''}`
    : '请先完成表单';

  return (
    <Modal
      title="批量排班"
      open={open}
      onCancel={onCancel}
      width={760}
      destroyOnClose
      footer={[
        <button key="cancel" type="button" className="ant-btn" onClick={onCancel}>
          取消
        </button>,
        <button
          key="submit"
          type="button"
          className="ant-btn ant-btn-primary"
          disabled={!canCreate || submitting}
          onClick={handleSubmit}
        >
          {submitting ? '创建中…' : submitText}
        </button>,
      ]}
    >
      <ProForm<BatchFormValues>
        form={form}
        submitter={false}
        initialValues={{
          weekdays: [1, 2, 3, 4, 5],
          shifts: ['MORNING', 'AFTERNOON'],
          totalSlots: 20,
          slotSplitMode: 'HOURLY',
          publishImmediately: true,
        }}
        layout="vertical"
      >
        <Space size="middle" style={{ width: '100%' }} wrap>
          <ProFormSelect
            name="deptId"
            label="科室"
            placeholder="可选，用于筛选医生"
            showSearch
            allowClear
            request={() => fetchDepartments()}
            width={200}
          />
          <ProFormSelect
            name="doctorId"
            label="医生"
            rules={[{ required: true, message: '请选择医生' }]}
            showSearch
            request={async (input) => {
              const { keyWords, deptId } = (input ?? {}) as {
                keyWords?: string;
                deptId?: number;
              };
              return fetchDoctors(keyWords ?? '', deptId);
            }}
            params={{ deptId }}
            debounceTime={300}
            width={200}
            placeholder="请选择医生"
          />
        </Space>
        <ProFormDateRangePicker
          name="dateRange"
          label="排班日期范围"
          rules={[{ required: true, message: '请选择排班日期范围' }]}
          fieldProps={{
            disabledDate: (current: Dayjs) =>
              current && current.startOf('day') < dayjs().startOf('day'),
          }}
        />
        <ProFormCheckbox.Group
          name="weekdays"
          label="星期模式"
          rules={[{ required: true, message: '请选择至少一个星期' }]}
          options={WEEKDAY_OPTIONS}
        />
        <ProFormCheckbox.Group
          name="shifts"
          label="班次"
          rules={[{ required: true, message: '请选择至少一个班次' }]}
          options={[
            { label: '上午', value: 'MORNING' },
            { label: '下午', value: 'AFTERNOON' },
          ]}
        />
        <Space size="middle" style={{ width: '100%' }} wrap>
          <ProFormDigit
            name="totalSlots"
            label="号源总数"
            rules={[{ required: true, message: '请输入号源总数' }]}
            min={1}
            max={99}
            width={200}
            fieldProps={{ addonAfter: '个' }}
            placeholder="1~99"
          />
          <ProFormSelect
            name="slotSplitMode"
            label="默认时段拆分"
            rules={[{ required: true, message: '请选择时段拆分方式' }]}
            width={200}
            options={SLOT_SPLIT_OPTIONS.map((o) => ({ label: o.label, value: o.value }))}
          />
        </Space>
        <ProFormCheckbox name="publishImmediately">
          创建成功后立即发布（仅对本次新建的草稿生效）
        </ProFormCheckbox>
      </ProForm>

      {preview && (
        <Alert
          type={preview.toSkipCount > 0 ? 'warning' : 'info'}
          showIcon
          style={{ marginBottom: 12 }}
          message={
            <Space size="large" wrap>
              <span>
                候选 <b>{preview.items.length}</b> 条
              </span>
              <span>
                将创建 <b style={{ color: '#52c41a' }}>{preview.toCreateCount}</b> 条
              </span>
              {preview.toSkipCount > 0 && (
                <span>
                  将跳过 <b style={{ color: '#faad14' }}>{preview.toSkipCount}</b> 条
                </span>
              )}
              {preview.slotSplitPreview.length > 0 && (
                <span style={{ color: '#999' }}>
                  拆分：
                  {preview.slotSplitPreview
                    .map((s) => `${s.startTime}-${s.endTime}×${s.count}`)
                    .join(' / ')}
                </span>
              )}
            </Space>
          }
        />
      )}

      <Table<API.BatchPreviewItem>
        rowKey={(r) => `${r.scheduleDate}-${r.shift}`}
        size="small"
        loading={previewing}
        dataSource={preview?.items ?? []}
        columns={columns}
        pagination={{ pageSize: 10, showSizeChanger: false, size: 'small' }}
        scroll={{ y: 280 }}
        locale={{ emptyText: previewing ? '加载中…' : '请完成上方表单以查看预览' }}
      />
    </Modal>
  );
}

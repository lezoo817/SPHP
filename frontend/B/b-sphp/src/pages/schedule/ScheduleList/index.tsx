/**
 * 排班列表页
 * - ProTable 列表，支持日期/科室/医生/状态筛选
 * - 写操作（新增/配置时段/发布/取消发布/作废）仅 ADMIN
 * - 业务铁律：PUBLISHED 状态下增/删/改类按钮置灰禁用并附 Tooltip「排班已发布，不可修改」
 */
import {
  Tag,
  Button,
  Modal,
  message,
  Select,
  Tooltip,
  Space,
  Form,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import {
  ProTable,
  ProForm,
  ProFormSelect,
  ProFormDatePicker,
  ProFormDigit,
} from '@ant-design/pro-components';
import { useModel, useNavigate } from '@umijs/max';
import { useEffect, useRef, useState } from 'react';
import type { ActionType, ProColumns, ProFormInstance } from '@ant-design/pro-components';
import {
  getSchedules,
  createSchedule,
  publishSchedule,
  unpublishSchedule,
  getDepartments,
  getDoctors,
} from '@/services/admin';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';

/** 班次映射 */
const SHIFT_MAP: Record<string, { text: string; color: string }> = {
  MORNING: { text: '上午', color: 'blue' },
  AFTERNOON: { text: '下午', color: 'geekblue' },
};

/** 班次可排班时间窗口（窗口结束时刻已过的当天班次不可再选择） */
const SHIFT_WINDOWS: Record<'MORNING' | 'AFTERNOON', { start: number; end: number }> = {
  MORNING: { start: 8, end: 12 },
  AFTERNOON: { start: 14, end: 18 },
};

/** 排班状态映射 */
const STATUS_MAP: Record<string, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  PUBLISHED: { text: '已发布', color: 'green' },
  CANCELLED: { text: '已作废', color: 'red' },
};

/** 状态筛选选项 */
const STATUS_OPTIONS = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已作废', value: 'CANCELLED' },
];

/** 排班已发布时对增删改操作的统一禁用提示 */
const PUBLISHED_LOCK_TOOLTIP = '排班已发布，不可修改';

export default function ScheduleList() {
  const { initialState } = useModel('@@initialState');
  const isAdmin = initialState?.currentUser?.roles?.includes('ADMIN') ?? false;
  const navigate = useNavigate();
  const actionRef = useRef<ActionType>();
  const formRef = useRef<ProFormInstance>();

  const [createOpen, setCreateOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [createForm] = Form.useForm();
  /** 新增弹窗当前选择的排班日期（用于按班次时间窗动态禁用选项） */
  const createDate = Form.useWatch('scheduleDate', createForm);

  /** 班次是否已过时：仅当天生效（窗口结束时刻过后不可选），未来日期均可选 */
  const isShiftExpired = (shift: 'MORNING' | 'AFTERNOON', date?: Dayjs): boolean => {
    if (!date) return false;
    const win = SHIFT_WINDOWS[shift];
    if (!win) return false;
    if (!dayjs(date).isSame(dayjs(), 'day')) return false;
    return dayjs().hour() >= win.end;
  };

  /** 班次选项（含时间窗标注；当天已过窗口的班次置灰不可选） */
  const getShiftOptions = (date?: Dayjs) => [
    {
      label: '上午（08:00-12:00）',
      value: 'MORNING' as const,
      disabled: isShiftExpired('MORNING', date),
    },
    {
      label: '下午（14:00-18:00）',
      value: 'AFTERNOON' as const,
      disabled: isShiftExpired('AFTERNOON', date),
    },
  ];

  /** 打开新增弹窗时重置表单，避免上一次残留值 */
  useEffect(() => {
    if (createOpen) {
      createForm.resetFields();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [createOpen]);

  /** 排班日期变化时，若当前班次已过时则自动切换到首个可选班次（全部过时则清空，提交时必选校验拦截） */
  useEffect(() => {
    const current = createForm.getFieldValue('shift');
    if (!current) return;
    const options = getShiftOptions(createDate);
    const isDisabled = options.find((o) => o.value === current)?.disabled;
    if (isDisabled) {
      const firstEnabled = options.find((o) => !o.disabled);
      createForm.setFieldsValue({ shift: firstEnabled?.value });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [createDate]);

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
      content: `确定发布「${record.doctorName}」${record.scheduleDate} ${SHIFT_MAP[record.shift]?.text ?? record.shift} 的排班吗？发布后号源将对患者开放预约。`,
      okText: '确认发布',
      onOk: async () => {
        try {
          await publishSchedule(record.id);
          message.success('排班发布成功');
          actionRef.current?.reload();
        } catch (err: any) {
          message.error(err?.message || '发布失败');
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
        } catch (err: any) {
          message.error(err?.message || '操作失败');
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
        } catch (err: any) {
          message.error(err?.message || '作废失败');
        }
      },
    });
  };

  /** 新增排班提交 */
  const handleCreateSubmit = async (values: any) => {
    const payload: API.CreateScheduleReq = {
      doctorId: values.doctorId,
      // ProForm 默认 dateFormatter='string'，提交时日期已是 'yyyy-MM-dd' 字符串；
      // dayjs() 归一化兼容字符串与 Dayjs 两种形态，避免调用非函数 .format 抛错
      scheduleDate: dayjs(values.scheduleDate).format('YYYY-MM-DD'),
      shift: values.shift,
      totalSlots: values.totalSlots,
    };
    setSubmitting(true);
    try {
      await createSchedule(payload);
      message.success('排班创建成功');
      setCreateOpen(false);
      actionRef.current?.reload();
    } catch (err: any) {
      message.error(err?.message || '创建失败，请重试');
    } finally {
      setSubmitting(false);
    }
  };

  /** 医生选项（供筛选与新增，仅启用医生） */
  const fetchDoctors = async (keyword?: string) => {
    try {
      const res = await getDoctors({
        name: keyword || undefined,
        status: 'ENABLED',
        page: 1,
        size: 100,
      });
      return (res.list ?? []).map((doc) => ({
        label: `${doc.name}（${doc.title}）`,
        value: doc.id,
      }));
    } catch {
      return [];
    }
  };

  /** 科室选项（供筛选） */
  const fetchDepartments = async () => {
    try {
      const res = await getDepartments({ page: 1, size: 200 });
      return (res.list ?? []).map((dept) => ({
        label: dept.name,
        value: dept.id,
      }));
    } catch {
      return [];
    }
  };

  /** 日期参数归一化：ProTable 可能传入 dayjs 或字符串 */
  const toDateParam = (v: unknown): string | undefined => {
    if (!v) return undefined;
    if (typeof v === 'string') return v;
    return (v as Dayjs).format('YYYY-MM-DD');
  };

  const columns: ProColumns<API.Schedule>[] = [
    {
      title: '排班日期',
      dataIndex: 'scheduleDate',
      width: 110,
      hideInSearch: true,
    },
    // 隐藏的日期搜索项：dataIndex 需与后端参数一致为 date
    {
      title: '日期',
      dataIndex: 'date',
      valueType: 'date',
      hideInTable: true,
    },
    {
      title: '医生',
      dataIndex: 'doctorName',
      width: 110,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '科室',
      dataIndex: 'deptName',
      width: 120,
      ellipsis: true,
      hideInSearch: true,
    },
    {
      title: '班次',
      dataIndex: 'shift',
      width: 80,
      hideInSearch: true,
      render: (_, record) => (
        <Tag color={SHIFT_MAP[record.shift]?.color}>{SHIFT_MAP[record.shift]?.text ?? record.shift}</Tag>
      ),
    },
    {
      title: '号源',
      dataIndex: 'totalSlots',
      width: 70,
      align: 'right',
      hideInSearch: true,
    },
    {
      title: '已约',
      dataIndex: 'bookedCount',
      width: 70,
      align: 'right',
      hideInSearch: true,
    },
    {
      title: '剩余',
      dataIndex: 'remainCount',
      width: 70,
      align: 'right',
      hideInSearch: true,
      render: (_, record) =>
        record.remainCount <= 0 ? <span style={{ color: '#ff4d4f' }}>{record.remainCount}</span> : record.remainCount,
    },
    {
      title: '锁定',
      dataIndex: 'lockedCount',
      width: 70,
      align: 'right',
      hideInSearch: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      valueEnum: {
        DRAFT: { text: '草稿', status: 'Default' },
        PUBLISHED: { text: '已发布', status: 'Success' },
        CANCELLED: { text: '已作废', status: 'Error' },
      },
      renderFormItem: () => (
        <Select allowClear placeholder="全部" options={STATUS_OPTIONS} />
      ),
      render: (_, record) => {
        const s = STATUS_MAP[record.status];
        return <Tag color={s?.color}>{s?.text ?? record.status}</Tag>;
      },
    },
    {
      title: '操作',
      width: 280,
      hideInSearch: true,
      render: (_, record) => {
        const published = record.status === 'PUBLISHED';
        const draft = record.status === 'DRAFT';
        return (
          <Space size={0} wrap>
            <Button type="link" size="small" onClick={() => goDetail(record)}>
              详情
            </Button>
            {draft && isAdmin && (
              <Button type="link" size="small" onClick={() => goDetail(record)}>
                配置时段
              </Button>
            )}
            {published && isAdmin && (
              <Tooltip title={PUBLISHED_LOCK_TOOLTIP}>
                <Button type="link" size="small" disabled>
                  配置时段
                </Button>
              </Tooltip>
            )}
            {draft && isAdmin && (
              <Tooltip
                title={
                  record.remainCount !== record.totalSlots
                    ? '时段号源数之和须等于总号源数方可发布'
                    : undefined
                }
              >
                <span>
                  <Button
                    type="link"
                    size="small"
                    disabled={record.remainCount !== record.totalSlots}
                    onClick={() => handlePublish(record)}
                  >
                    发布
                  </Button>
                </span>
              </Tooltip>
            )}
            {draft && isAdmin && (
              <Button type="link" size="small" danger onClick={() => handleCancel(record)}>
                作废
              </Button>
            )}
            {published && isAdmin && (
              <Button type="link" size="small" danger onClick={() => handleUnpublish(record)}>
                取消发布
              </Button>
            )}
          </Space>
        );
      },
    },
  ];

  // 科室/医生筛选仅 ADMIN 生效（后端对非 ADMIN 忽略该过滤）
  if (isAdmin) {
    columns.splice(2, 0, {
      title: '科室',
      dataIndex: 'deptId',
      valueType: 'select',
      hideInTable: true,
      request: fetchDepartments,
      fieldProps: { showSearch: true, allowClear: true, placeholder: '请选择科室' },
    });
    columns.splice(3, 0, {
      title: '医生',
      dataIndex: 'doctorId',
      valueType: 'select',
      hideInTable: true,
      request: () => fetchDoctors(),
      fieldProps: { showSearch: true, allowClear: true, placeholder: '请选择医生' },
    });
  }

  return (
    <>
      <ProTable<API.Schedule, API.ScheduleListParams>
        actionRef={actionRef}
        formRef={formRef}
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
            });
            return { data: res.list, total: res.total, success: true };
          } catch (err: any) {
            message.error(err?.message || '查询失败，请重试');
            return { data: [], total: 0, success: true };
          }
        }}
        search={{
          labelWidth: 'auto',
          // span=6 → 一行放 4 个筛选项；defaultFormItemsNumber=4 使全部默认展示（QueryFilter 默认 -1 预留提交按钮，会导致第 4 个被折叠出现「展开」）
          span: 6,
          defaultFormItemsNumber: 4,
          onReset: () => formRef.current?.resetFields(),
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
              ]
            : []
        }
        pagination={{ pageSize: 5, showSizeChanger: false }}
      />

      {/* ====== 新增排班弹窗 ====== */}
      <Modal
        title="新增排班"
        open={createOpen}
        footer={null}
        destroyOnClose
        onCancel={() => setCreateOpen(false)}
        width={520}
      >
        <ProForm
          form={createForm}
          onFinish={handleCreateSubmit}
          submitter={{ submitButtonProps: { loading: submitting } }}
        >
          <ProFormSelect
            name="doctorId"
            label="医生"
            rules={[{ required: true, message: '请选择医生' }]}
            showSearch
            request={(input) => fetchDoctors(input?.key ?? '')}
            debounceTime={300}
            placeholder="请选择医生"
          />
          <ProFormDatePicker
            name="scheduleDate"
            label="排班日期"
            rules={[{ required: true, message: '请选择排班日期' }]}
            fieldProps={{
              style: { width: '100%' },
              disabledDate: (current: Dayjs) => current && current.startOf('day') < dayjs().startOf('day'),
            }}
            placeholder="请选择排班日期"
          />
          <ProFormSelect
            name="shift"
            label="班次"
            rules={[{ required: true, message: '请选择班次' }]}
            options={getShiftOptions(createDate)}
            initialValue="MORNING"
          />
          <ProFormDigit
            name="totalSlots"
            label="号源总数"
            rules={[{ required: true, message: '请输入号源总数' }]}
            min={1}
            max={99}
            fieldProps={{ addonAfter: '个（1~99）', style: { width: '100%' } }}
            placeholder="请输入号源总数"
          />
        </ProForm>
      </Modal>
    </>
  );
}

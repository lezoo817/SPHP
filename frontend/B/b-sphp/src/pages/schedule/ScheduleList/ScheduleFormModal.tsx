/**
 * 新增排班弹窗。
 *
 * - 科室→医生级联：先选科室（可选）再选医生，切换科室时清空已选医生，避免医生与所选科室不一致的脏数据；
 * - 医生/日期/班次/号源总数表单（医生按所选科室联动过滤，未选科室时拉取全院启用医生）；
 * - 班次按时间窗动态禁用：仅当天窗口已结束的班次不可选，未来日期均可选；
 * - 日期变化时若当前班次已过时，自动切换到首个可选班次（全部过时则清空，提交时必选校验拦截）。
 */
import { useEffect } from 'react';
import { Form, Modal } from 'antd';
import {
  ProForm,
  ProFormSelect,
  ProFormDatePicker,
  ProFormDigit,
  ProFormCheckbox,
} from '@ant-design/pro-components';
import { getDepartments, getDoctors } from '@/services/admin';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import { getShiftWindow } from '../constants';
import { PAGE_SIZE_100, PAGE_SIZE_200 } from '@/constants/pageSize';

/** 新增排班表单值（scheduleDate 兼容 Dayjs 与字符串；deptId 仅 UI 联动状态，不参与提交） */
interface CreateScheduleFormValues {
  deptId?: number;
  doctorId: number;
  scheduleDate: Dayjs | string;
  shift: 'MORNING' | 'AFTERNOON';
  totalSlots: number;
  /** 创建成功后立即发布（后端自动按 1小时/段 配置号源时段并发布） */
  publishImmediately: boolean;
}

interface Props {
  open: boolean;
  submitting: boolean;
  onCancel: () => void;
  onSubmit: (payload: API.CreateScheduleReq) => void;
}

export default function ScheduleFormModal({
  open,
  submitting,
  onCancel,
  onSubmit,
}: Props) {
  const [createForm] = Form.useForm<CreateScheduleFormValues>();
  /** 当前选择的排班日期（用于按班次时间窗动态禁用选项） */
  const createDate = Form.useWatch('scheduleDate', createForm);
  /** 当前选择的科室（用于联动过滤医生下拉；undefined 表示未选，回退到全院启用医生） */
  const createDeptId = Form.useWatch('deptId', createForm);

  /** 班次是否已过时：仅当天生效（窗口结束时刻过后不可选），未来日期均可选 */
  const isShiftExpired = (
    shift: 'MORNING' | 'AFTERNOON',
    date?: Dayjs | string,
  ): boolean => {
    if (!date) return false;
    const win = getShiftWindow(shift);
    if (!win) return false;
    if (!dayjs(date).isSame(dayjs(), 'day')) return false;
    return dayjs().hour() >= win.end;
  };

  /** 班次选项（含时间窗标注；当天已过窗口的班次置灰不可选） */
  const getShiftOptions = (date?: Dayjs | string) => [
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

  /** 打开弹窗时重置表单，避免上一次残留值（destroyOnHidden 下仍保险） */
  useEffect(() => {
    if (open) {
      createForm.resetFields();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  /** 排班日期变化时，若当前班次已过时则自动切换到首个可选班次 */
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

  /** 科室变化时清空已选医生：避免出现"医生属 A 科室、却挂了 B 科室排班"的脏数据 */
  useEffect(() => {
    // 初次挂载时 createDeptId 为 undefined，setFieldsValue({ doctorId: undefined }) 为 no-op
    createForm.setFieldsValue({ doctorId: undefined });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [createDeptId]);

  /** 科室选项（取自当前管理员所属医院，全量拉取后转为 label/value 列表） */
  const fetchDepartments = async () => {
    try {
      const res = await getDepartments({ page: 1, size: PAGE_SIZE_200 });
      return (res.list ?? []).map((dept) => ({
        label: dept.name,
        value: dept.id,
      }));
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
        size: PAGE_SIZE_100,
      });
      return (res.list ?? []).map((doc) => ({
        label: `${doc.name}（${doc.title}）`,
        value: doc.id,
      }));
    } catch {
      return [];
    }
  };

  /** 提交：日期归一化为 yyyy-MM-dd 字符串后交给父组件 */
  const handleFinish = async (values: CreateScheduleFormValues) => {
    await onSubmit({
      doctorId: values.doctorId,
      scheduleDate: dayjs(values.scheduleDate).format('YYYY-MM-DD'),
      shift: values.shift,
      totalSlots: values.totalSlots,
      publishImmediately: values.publishImmediately,
    });
  };

  return (
    <Modal
      title="新增排班"
      open={open}
      footer={null}
      destroyOnHidden
      onCancel={onCancel}
      width={520}
    >
      <ProForm<CreateScheduleFormValues>
        form={createForm}
        onFinish={handleFinish}
        submitter={{ submitButtonProps: { loading: submitting } }}
        initialValues={{ publishImmediately: true }}
      >
        <ProFormSelect
          name="deptId"
          label="科室"
          placeholder="请选择科室（可选，用于筛选医生）"
          showSearch
          allowClear
          request={() => fetchDepartments()}
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
          // params 变化会触发 ProFormSelect 重新拉取：科室切换时强制重拉医生列表
          params={{ deptId: createDeptId }}
          debounceTime={300}
          placeholder="请选择医生"
        />
        <ProFormDatePicker
          name="scheduleDate"
          label="排班日期"
          rules={[{ required: true, message: '请选择排班日期' }]}
          fieldProps={{
            style: { width: '100%' },
            disabledDate: (current: Dayjs) =>
              current && current.startOf('day') < dayjs().startOf('day'),
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
        <ProFormCheckbox name="publishImmediately">
          创建成功后立即发布（自动按 1小时/段 配置号源时段）
        </ProFormCheckbox>
      </ProForm>
    </Modal>
  );
}

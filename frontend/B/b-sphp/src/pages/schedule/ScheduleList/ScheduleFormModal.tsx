/**
 * 新增排班弹窗。
 *
 * - 医生/日期/班次/号源总数表单；
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
} from '@ant-design/pro-components';
import { getDoctors } from '@/services/admin';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import { getShiftWindow } from '../constants';

/** 新增排班表单值（scheduleDate 兼容 Dayjs 与字符串） */
interface CreateScheduleFormValues {
  doctorId: number;
  scheduleDate: Dayjs | string;
  shift: 'MORNING' | 'AFTERNOON';
  totalSlots: number;
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

  /** 打开弹窗时重置表单，避免上一次残留值（destroyOnClose 下仍保险） */
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

  /** 医生选项（供搜索下拉，仅启用医生） */
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

  /** 提交：日期归一化为 yyyy-MM-dd 字符串后交给父组件 */
  const handleFinish = async (values: CreateScheduleFormValues) => {
    await onSubmit({
      doctorId: values.doctorId,
      scheduleDate: dayjs(values.scheduleDate).format('YYYY-MM-DD'),
      shift: values.shift,
      totalSlots: values.totalSlots,
    });
  };

  return (
    <Modal
      title="新增排班"
      open={open}
      footer={null}
      destroyOnClose
      onCancel={onCancel}
      width={520}
    >
      <ProForm<CreateScheduleFormValues>
        form={createForm}
        onFinish={handleFinish}
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
      </ProForm>
    </Modal>
  );
}

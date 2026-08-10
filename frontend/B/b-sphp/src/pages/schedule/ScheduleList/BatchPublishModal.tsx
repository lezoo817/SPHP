/**
 * 批量发布排班弹窗（列表入口）。
 *
 * 交互流程：
 * 1. 顶部筛选：日期范围 / 科室 / 医生 / 班次（均可选，组合过滤）
 * 2. 点击【查找 DRAFT 排班】调 getSchedules 强制 status=DRAFT
 * 3. 下方表格展示匹配项，行 checkbox 默认全选
 * 4. 【全选】 + 【批量发布 N 条】按钮：禁用当 N=0
 *
 * 设计要点：
 * - 不走行选择器（与列表 ProTable rowSelection 互斥，独立弹窗更聚焦"按条件批量"场景）
 * - 复用 getSchedules 接口；服务层 getCurrentDataScope 自动按医院/科室/医生权限过滤
 * - 后端 batchPublish 内部已校验 DRAFT 状态；前端预过滤 DRAFT 仅减少请求体积
 */
import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Checkbox,
  Form,
  Modal,
  Space,
  Table,
  Tag,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ProForm, ProFormSelect, ProFormDateRangePicker } from '@ant-design/pro-components';
import dayjs, { type Dayjs } from 'dayjs';
import {
  batchPublishSchedules,
  getDepartments,
  getDoctors,
  getSchedules,
} from '@/services/admin';
import { getErrorMessage } from '@/utils/error';
import { getShiftConfig, getStatusConfig } from '../constants';
import { PAGE_SIZE_100, PAGE_SIZE_200, PAGE_SIZE_DEFAULT } from '@/constants/pageSize';

interface FilterValues {
  dateRange?: [Dayjs, Dayjs];
  deptId?: number;
  doctorId?: number;
  shifts?: ('MORNING' | 'AFTERNOON')[];
}

interface Props {
  open: boolean;
  onCancel: () => void;
  onPublished?: () => void;
}

export default function BatchPublishModal({ open, onCancel, onPublished }: Props) {
  const [form] = Form.useForm<FilterValues>();
  const deptId = Form.useWatch('deptId', form);

  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [list, setList] = useState<API.Schedule[]>([]);
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());

  /** 打开弹窗时重置 */
  useEffect(() => {
    if (open) {
      form.resetFields();
      setList([]);
      setSelectedIds(new Set());
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  /** 科室变化时清空已选医生 */
  useEffect(() => {
    form.setFieldsValue({ doctorId: undefined });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [deptId]);

  /** 科室选项 */
  const fetchDepartments = async () => {
    try {
      const res = await getDepartments({ page: 1, size: PAGE_SIZE_200 });
      return (res.list ?? []).map((d) => ({ label: d.name, value: d.id }));
    } catch {
      return [];
    }
  };

  /** 医生选项（按所选科室联动过滤） */
  const fetchDoctors = async (keyword: string, deptId?: number) => {
    try {
      const res = await getDoctors({
        deptId,
        name: keyword || undefined,
        status: 'ENABLED',
        page: 1,
        size: PAGE_SIZE_100,
      });
      return (res.list ?? []).map((d) => ({
        label: `${d.name}（${d.title}）`,
        value: d.id,
      }));
    } catch {
      return [];
    }
  };

  /** 查找 DRAFT 排班 */
  const handleSearch = async () => {
    const v = form.getFieldsValue();
    setLoading(true);
    try {
      const res = await getSchedules({
        page: 1,
        size: PAGE_SIZE_100,
        // 后端 date 只支持单值；范围过滤在客户端按 [start, end] 二次过滤
        date: v.dateRange?.[0]?.format('YYYY-MM-DD'),
        deptId: v.deptId,
        doctorId: v.doctorId,
        status: 'DRAFT',
      });
      const inRange = v.dateRange
        ? res.list.filter((row) => {
            const d = row.scheduleDate;
            return d >= v.dateRange![0].format('YYYY-MM-DD')
                && d <= v.dateRange![1].format('YYYY-MM-DD');
          })
        : res.list;
      const finalList = v.shifts?.length
        ? inRange.filter((row) => v.shifts!.includes(row.shift))
        : inRange;
      setList(finalList);
      setSelectedIds(new Set(finalList.map((r) => r.id)));
      if (finalList.length === 0) {
        message.info('未找到匹配条件的 DRAFT 排班');
      }
    } catch (err: unknown) {
      message.error(getErrorMessage(err, '查询失败'));
    } finally {
      setLoading(false);
    }
  };

  /** 全选 */
  const handleToggleAll = (checked: boolean) => {
    setSelectedIds(checked ? new Set(list.map((r) => r.id)) : new Set());
  };

  const handleToggleOne = (id: number, checked: boolean) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (checked) next.add(id);
      else next.delete(id);
      return next;
    });
  };

  /** 批量发布 */
  const handlePublish = async () => {
    if (selectedIds.size === 0) return;
    setSubmitting(true);
    try {
      const report = await batchPublishSchedules({
        scheduleIds: Array.from(selectedIds),
      });
      const parts: string[] = [];
      if (report.publishedCount) parts.push(`发布 ${report.publishedCount} 条`);
      if (report.failedCount) parts.push(`失败 ${report.failedCount} 条`);
      message.success(parts.join('，'));
      onPublished?.();
      onCancel();
    } catch (err: unknown) {
      message.error(getErrorMessage(err, '批量发布失败'));
    } finally {
      setSubmitting(false);
    }
  };

  const allChecked = list.length > 0 && selectedIds.size === list.length;
  const indeterminate = selectedIds.size > 0 && selectedIds.size < list.length;

  const columns: ColumnsType<API.Schedule> = useMemo(
    () => [
      { title: '医生', dataIndex: 'doctorName', width: 120 },
      {
        title: '科室',
        dataIndex: 'deptName',
        width: 100,
        render: (v?: string) => v ?? '—',
      },
      {
        title: '日期',
        dataIndex: 'scheduleDate',
        width: 130,
        render: (v: string) => dayjs(v).format('YYYY-MM-DD ddd'),
      },
      {
        title: '班次',
        dataIndex: 'shift',
        width: 80,
        render: (v: string) => getShiftConfig(v)?.text ?? v,
      },
      {
        title: '号源',
        dataIndex: 'totalSlots',
        width: 80,
        align: 'right',
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 90,
        render: (v: string) => {
          const cfg = getStatusConfig(v);
          return <Tag color={cfg?.color}>{cfg?.text ?? v}</Tag>;
        },
      },
    ],
    [],
  );

  return (
    <Modal
      title="批量发布排班"
      open={open}
      onCancel={onCancel}
      width={840}
      destroyOnHidden
      footer={[
        <Button key="cancel" onClick={onCancel}>取消</Button>,
        <Button
          key="submit"
          type="primary"
          loading={submitting}
          disabled={selectedIds.size === 0}
          onClick={handlePublish}
        >
          批量发布 {selectedIds.size} 条
        </Button>,
      ]}
    >
      <ProForm<FilterValues> form={form} submitter={false} layout="inline">
        <ProFormDateRangePicker
          name="dateRange"
          label="日期范围"
          fieldProps={{
            disabledDate: (current: Dayjs) =>
              current && current.startOf('day') < dayjs().startOf('day'),
          }}
        />
        <ProFormSelect
          name="deptId"
          label="科室"
          showSearch
          allowClear
          request={() => fetchDepartments()}
          width={150}
        />
        <ProFormSelect
          name="doctorId"
          label="医生"
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
          width={150}
        />
        <ProFormSelect
          name="shifts"
          label="班次"
          mode="multiple"
          width={180}
          options={[
            { label: '上午', value: 'MORNING' },
            { label: '下午', value: 'AFTERNOON' },
          ]}
        />
        <Form.Item>
          <Space>
            <Button type="primary" onClick={handleSearch} loading={loading}>
              查找 DRAFT 排班
            </Button>
            <Button onClick={() => form.resetFields()}>重置</Button>
          </Space>
        </Form.Item>
      </ProForm>

      {list.length > 0 && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message={
            <Space>
              <Checkbox
                checked={allChecked}
                indeterminate={indeterminate}
                onChange={(e) => handleToggleAll(e.target.checked)}
              >
                全选
              </Checkbox>
              <span>
                匹配 <b>{list.length}</b> 条 DRAFT，已选 <b>{selectedIds.size}</b> 条
              </span>
            </Space>
          }
        />
      )}

      <Table<API.Schedule>
        rowKey="id"
        size="small"
        loading={loading}
        dataSource={list}
        columns={[
          {
            title: '选择',
            key: 'select',
            width: 60,
            render: (_, row) => (
              <Checkbox
                checked={selectedIds.has(row.id)}
                onChange={(e) => handleToggleOne(row.id, e.target.checked)}
              />
            ),
          },
          ...columns,
        ]}
        pagination={{ pageSize: PAGE_SIZE_DEFAULT, showSizeChanger: false, size: 'small' }}
        scroll={{ y: 320 }}
        locale={{ emptyText: '请设置筛选条件后点击「查找 DRAFT 排班」' }}
      />
    </Modal>
  );
}

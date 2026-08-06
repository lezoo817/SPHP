/**
 * 患者详情页 ProTable 列配置。
 *
 * 就诊记录与历史处方两列集，状态字段统一经 constants 状态映射渲染为 Tag。
 */
import { Tag } from 'antd';
import type { ProColumns } from '@ant-design/pro-components';
import { visitStatusMap, prescriptionStatusMap } from './constants';

/** 就诊记录列 */
export function getVisitColumns(): ProColumns<API.PatientVisitItem>[] {
  return [
    { title: '就诊日期', dataIndex: 'visitDate', width: 120 },
    { title: '医生', dataIndex: 'doctorName', width: 120, ellipsis: true },
    { title: '科室', dataIndex: 'deptName', width: 120, ellipsis: true },
    {
      title: '诊断摘要',
      dataIndex: 'summary',
      ellipsis: true,
      render: (_: unknown, record: API.PatientVisitItem) => record.summary || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_: unknown, record: API.PatientVisitItem) => {
        const s = visitStatusMap[record.status] ?? { text: record.status, color: 'default' };
        return <Tag color={s.color}>{s.text}</Tag>;
      },
    },
  ];
}

/** 历史处方列 */
export function getPrescriptionColumns(): ProColumns<API.PatientPrescriptionItem>[] {
  return [
    { title: '处方ID', dataIndex: 'id', width: 100 },
    { title: '医生', dataIndex: 'doctorName', width: 120, ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (_: unknown, record: API.PatientPrescriptionItem) => {
        const s =
          prescriptionStatusMap[record.status] ?? { text: record.status, color: 'default' };
        return <Tag color={s.color}>{s.text}</Tag>;
      },
    },
    { title: '药品数量', dataIndex: 'itemCount', width: 100 },
    {
      title: '签发时间',
      dataIndex: 'issuedAt',
      width: 160,
      render: (_: unknown, record: API.PatientPrescriptionItem) => record.issuedAt || '-',
    },
    { title: '创建时间', dataIndex: 'createdAt', width: 160 },
  ];
}

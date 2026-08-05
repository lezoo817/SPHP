import type { PrescriptionDetail } from '../typings/api';
import { formatPrescriptionIssuedAt } from '../utils/prescription';

/** 电子处方笺展示组件的输入参数。 */
interface PrescriptionPaperProps {
  /** 已通过服务端可见性校验的处方详情。 */
  detail: PrescriptionDetail;
  /** 仅用于页面展示的处方编号。 */
  displayNumber: string;
  /** 详情接口缺失开具时间时，由列表入口透传的时间。 */
  issuedAt?: string;
}

/**
 * 按统一电子处方笺格式展示处方药品、状态与开方医生。
 * @param props 处方详情和前端展示编号
 * @returns 可被查看和购药入口复用的处方笺内容
 */
export function PrescriptionPaper({ detail, displayNumber, issuedAt }: PrescriptionPaperProps) {
  const displayIssuedAt = detail.issuedAt || issuedAt;
  return <section className="pharmacy-prescription-paper"><header className="pharmacy-prescription-paper__header"><div><b>电子处方笺</b><em>已批准</em></div><span>处方编号：{displayNumber} · 开具时间：{formatPrescriptionIssuedAt(displayIssuedAt)}</span></header><section className="pharmacy-prescription-paper__items"><div className="pharmacy-prescription-paper__columns"><span>药品名称</span><span>规格</span><span>用法用量</span></div>{detail.items.map((item) => <article className="pharmacy-prescription-paper__item" key={item.drugId}><b>{item.drugName}</b><span>{item.specification || '规格待确认'}</span><span>{item.dosage || '用量待确认'} · {item.frequency || '频次待确认'}<small>{item.usage || '用法待确认'}{item.durationDays ? ` · ${item.durationDays}天` : ''}</small></span></article>)}</section><footer className="pharmacy-prescription-paper__signature"><span>开方医生：</span><b>{detail.doctor.name || detail.doctorName || '医生待确认'}</b></footer></section>;
}

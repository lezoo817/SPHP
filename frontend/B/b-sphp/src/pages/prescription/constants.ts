/**
 * 处方模块共享常量。
 *
 * 状态映射/选项集中管理，供列表列、详情弹窗复用，避免各文件重复定义。
 */

/** 处方状态类型（取自全局 API 命名空间） */
type PrescriptionStatus = API.Prescription['status'];

/** 处方状态标签映射 */
export const STATUS_MAP: Record<PrescriptionStatus, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  SUBMITTED: { text: '待审核', color: 'orange' },
  APPROVED: { text: '已通过', color: 'green' },
  REJECTED: { text: '已驳回', color: 'red' },
};

/** 处方状态筛选选项（状态搜索框） */
export const STATUS_OPTIONS: { label: string; value: PrescriptionStatus }[] = [
  { label: '草稿', value: 'DRAFT' },
  { label: '待审核', value: 'SUBMITTED' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已驳回', value: 'REJECTED' },
];

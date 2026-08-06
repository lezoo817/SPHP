/**
 * 药品目录页常量与类型。
 */

/** 药品状态联合类型（对齐 API.Drug.status） */
export type DrugStatus = API.Drug['status'];

/** 状态展示配置（Tag 颜色与文案） */
export const STATUS_MAP: Record<DrugStatus, { text: string; color: string }> = {
  ENABLED: { text: '启用', color: 'green' },
  DISABLED: { text: '停用', color: 'red' },
};

/** 状态下拉选项（新增/编辑表单与搜索筛选共用） */
export const STATUS_OPTIONS: { label: string; value: DrugStatus }[] = [
  { label: '启用', value: 'ENABLED' },
  { label: '停用', value: 'DISABLED' },
];

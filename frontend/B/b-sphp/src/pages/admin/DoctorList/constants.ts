/**
 * 医生管理页的常量与选项配置（状态映射 / 职称选项 / 状态选项）。
 */
import { getDepartments } from '@/services/admin';
import { PAGE_SIZE_200 } from '@/constants/pageSize';
import { STATUS_DISABLED, STATUS_ENABLED, STATUS_SUSPENDED } from '@/constants/businessStatus';

/** 医生状态联合类型（与 API.Doctor.status 保持一致）。 */
export type DoctorStatus = API.Doctor['status'];

/** 医生状态 → 展示文案与颜色映射。 */
export const STATUS_MAP: Record<DoctorStatus, { text: string; color: string }> = {
  ENABLED: { text: '启用', color: 'green' },
  DISABLED: { text: '停用', color: 'red' },
  SUSPENDED: { text: '暂停', color: 'orange' },
};

/** 医生状态下拉选项（切换状态弹窗用）。 */
export const STATUS_OPTIONS: { label: string; value: DoctorStatus }[] = [
  { label: '启用', value: STATUS_ENABLED },
  { label: '停用', value: STATUS_DISABLED },
  { label: '暂停', value: STATUS_SUSPENDED },
];

/** 医生职称选项。 */
export const TITLE_OPTIONS = [
  { label: '主任医师', value: '主任医师' },
  { label: '副主任医师', value: '副主任医师' },
  { label: '主治医师', value: '主治医师' },
  { label: '住院医师', value: '住院医师' },
  { label: '医士', value: '医士' },
];

/** 获取科室下拉选项（供新增医生弹窗与列表搜索筛选）。 */
export async function fetchDepartmentOptions(): Promise<
  { label: string; value: number }[]
> {
  try {
    const res = await getDepartments({ page: 1, size: PAGE_SIZE_200 });
    return (res.list ?? []).map((dept) => ({
      label: dept.name,
      value: dept.id,
    }));
  } catch {
    return [];
  }
}

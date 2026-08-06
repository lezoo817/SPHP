/**
 * 库存管理页常量与工具。
 *
 * 库存状态（NORMAL/LOW/ALERT）不落库，前端根据 availableCount 与 safetyStock 实时计算。
 */

/** 库存状态联合类型（对齐 API.InventoryItem.status） */
export type InventoryStatus = API.InventoryItem['status'];

/** 根据库存数量与安全库存计算展示状态与进度 */
export function calcStatus(available: number, safety: number): {
  status: InventoryStatus;
  percent: number;
  color: string;
  label: string;
} {
  if (safety <= 0) {
    return { status: 'NORMAL', percent: 100, color: '#52c41a', label: '正常' };
  }
  const ratio = available / safety;
  if (ratio >= 2) {
    return {
      status: 'NORMAL',
      percent: Math.min(100, (available / (safety * 2)) * 100),
      color: '#52c41a',
      label: '正常',
    };
  }
  if (ratio >= 1) {
    return {
      status: 'LOW',
      percent: (available / safety) * 100,
      color: '#faad14',
      label: '偏低',
    };
  }
  return {
    status: 'ALERT',
    percent: (available / safety) * 100,
    color: '#ff4d4f',
    label: '告警',
  };
}

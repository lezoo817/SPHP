/**
 * 金额展示工具。
 *
 * 后端金额以「分」存储（整数），展示时统一转为「元」并保留两位小数。
 */

/** 分转元显示（保留两位小数）。 */
export function formatPrice(cent: number): string {
  return (cent / 100).toFixed(2);
}

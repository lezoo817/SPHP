/**
 * B 端通用分页大小常量。
 *
 * 各列表分页 / 下拉选项加载的每页大小集中在文件统一维护，避免散落魔法值导致口径不一致。
 * 后端各 Service 将每页大小钳制在 [1, MAX_PAGE_SIZE(100)]，超过按 100 处理。
 */

/** 默认每页条数（通用列表页默认分页） */
export const PAGE_SIZE_DEFAULT = 10;

/** 每页 5 条（科室等小数据量列表） */
export const PAGE_SIZE_5 = 5;

/** 每页 20 条 */
export const PAGE_SIZE_20 = 20;

/** 每页 50 条 */
export const PAGE_SIZE_50 = 50;

/** 每页 100 条（下拉选项 / 一次性加载，对齐后端 MAX_PAGE_SIZE） */
export const PAGE_SIZE_100 = 100;

/** 每页 200 条（大批量选项加载） */
export const PAGE_SIZE_200 = 200;

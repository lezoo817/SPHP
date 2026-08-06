import type { Prescription } from '../typings/api';
import type { AgentNavigationState } from '../typings/agent';

const PRESCRIPTION_DISPLAY_NUMBER_PREFIX = 'sphp:prescription-display-number:';
const fallbackDisplayNumberStorage = new Map<string, string>();

/**
 * 构建“AI 一键解读”跳转状态。
 * @param from 处方详情页的完整地址
 * @param prescriptionId 服务端真实处方 ID
 * @returns 跳转 AI 页面时使用的路由状态
 */
export function buildPrescriptionInterpretationAgentState(from: string, prescriptionId: number): AgentNavigationState {
  return {
    from,
    presetAction: { type: 'interpret_prescription', prescriptionId },
  };
}

/** 用于读取和写入浏览器会话缓存的最小存储接口。 */
export interface PrescriptionDisplayNumberStorage {
  /** 读取指定缓存键。 */
  getItem(key: string): string | null;
  /** 写入指定缓存键。 */
  setItem(key: string, value: string): void;
}

/** 处方列表的日期筛选范围。 */
export interface PrescriptionDateRange {
  /** 筛选开始日期，格式为 YYYY-MM-DD。 */
  startDate: string;
  /** 筛选结束日期，格式为 YYYY-MM-DD。 */
  endDate: string;
}

/**
 * 生成以指定日期为结束的最近处方查询范围。
 * @param days 最近天数
 * @param now 便于测试注入的当前时间
 * @returns 可直接回填日期输入框的起止日期
 */
export function getRecentPrescriptionRange(days: number, now = new Date()): PrescriptionDateRange {
  const end = new Date(now);
  const start = new Date(now);
  // 最近 N 天包含当天，开始日期向前回退 N - 1 天。
  start.setDate(start.getDate() - Math.max(0, days - 1));
  return { startDate: formatDateInput(start), endDate: formatDateInput(end) };
}

/**
 * 判断处方日期范围是否有效。
 * @param range 页面当前选择的开始和结束日期
 * @returns 开始日期未晚于结束日期时返回 true
 */
export function isPrescriptionDateRangeValid({ startDate, endDate }: PrescriptionDateRange): boolean {
  return !startDate || !endDate || startDate <= endDate;
}

/**
 * 按处方开具日期筛选并保证结果按开具时间倒序展示。
 * @param prescriptions 已从后端加载的处方记录
 * @param range 页面选择的日期范围
 * @returns 符合日期条件的处方副本
 */
export function filterPrescriptionsByDate(prescriptions: Prescription[], { startDate, endDate }: PrescriptionDateRange): Prescription[] {
  if (!isPrescriptionDateRangeValid({ startDate, endDate })) return [];
  return prescriptions
    .filter((prescription) => {
      // ISO-8601 前十位保留后端东八区开具日期，避免浏览器时区换算改变筛选日。
      const issuedDate = prescription.issuedAt.slice(0, 10);
      return (!startDate || issuedDate >= startDate) && (!endDate || issuedDate <= endDate);
    })
    .sort((left, right) => right.issuedAt.localeCompare(left.issuedAt) || right.id - left.id);
}

/**
 * 合并处方分页数据并按处方 ID 去重。
 * @param current 已加载的处方记录
 * @param next 新加载的一页处方记录
 * @returns 合并后的处方记录，重复项以新数据为准
 */
export function mergePrescriptionPages(current: Prescription[], next: Prescription[]): Prescription[] {
  return Array.from(new Map([...current, ...next].map((prescription) => [prescription.id, prescription])).values());
}

/**
 * 根据开具时间生成仅用于前端展示的处方编号。
 * @param issuedAt 后端返回的处方开具时间
 * @param randomValue 可选随机尾号，便于测试时固定结果
 * @returns 开具时间毫秒时间戳与四位随机数拼接的展示编号
 */
export function createPrescriptionDisplayNumber(issuedAt?: string, randomValue = Math.floor(Math.random() * 10_000)): string {
  const timestamp = issuedAt ? Date.parse(issuedAt) : Number.NaN;
  // 开具时间异常时不制造看似真实的编号，提示服务端资料待完善。
  if (!Number.isFinite(timestamp)) return '暂未提供';
  const suffix = Number.isFinite(randomValue) ? Math.max(0, Math.min(9999, Math.trunc(randomValue))) : 0;
  return `${timestamp}${String(suffix).padStart(4, '0')}`;
}

/**
 * 读取同一浏览器会话内稳定的处方展示编号。
 * @param prescriptionId 后端处方 ID，仅用于前端缓存键隔离
 * @param issuedAt 后端返回的处方开具时间
 * @param randomValue 首次生成时使用的随机尾号，便于测试注入
 * @param storage 可选的会话存储实现
 * @returns 当前会话内可复用的处方展示编号
 */
export function getPrescriptionDisplayNumber(prescriptionId: number, issuedAt?: string, randomValue = Math.floor(Math.random() * 10_000), storage = getPrescriptionDisplayNumberStorage()): string {
  const cacheKey = `${PRESCRIPTION_DISPLAY_NUMBER_PREFIX}${prescriptionId}`;
  const existing = storage.getItem(cacheKey);
  if (existing) return existing;
  const displayNumber = createPrescriptionDisplayNumber(issuedAt, randomValue);
  // 编号只用于视觉展示，按处方 ID 写入当前会话以保持列表和详情一致。
  storage.setItem(cacheKey, displayNumber);
  return displayNumber;
}

/**
 * 格式化处方开具时间，供列表和电子处方笺统一使用。
 * @param issuedAt 后端返回的开具时间
 * @returns 面向患者的年月日时间文案
 */
export function formatPrescriptionIssuedAt(issuedAt?: string): string {
  return issuedAt ? new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(issuedAt)) : '时间待确认';
}

/**
 * 构建“我的处方”进入详情时需要保留的页面上下文。
 * @param prescriptionId 处方编号
 * @param patientId 当前页面选择的就诊人编号
 * @param range 当前日期筛选范围
 * @param issuedAt 处方列表返回的开具时间
 * @returns 既有处方详情的完整跳转路径
 */
export function buildMinePrescriptionDetailPath(prescriptionId: number, patientId: number | undefined, range: PrescriptionDateRange, issuedAt?: string): string {
  const search = new URLSearchParams({ source: 'mine-prescriptions', startDate: range.startDate, endDate: range.endDate });
  if (patientId && patientId > 0) search.set('patientId', String(patientId));
  if (issuedAt) search.set('issuedAt', issuedAt);
  return `/assistant/prescription/${prescriptionId}?${search.toString()}`;
}

/**
 * 构建就诊助手进入处方详情时的就诊人上下文。
 * @param prescriptionId 处方编号
 * @param patientId 就诊助手页面本地就诊人编号
 * @param issuedAt 处方列表返回的开具时间
 * @returns 可恢复助手返回路径且允许购药的详情路径
 */
export function buildAssistantPrescriptionDetailPath(prescriptionId: number, patientId: number | undefined, issuedAt?: string): string {
  const search = new URLSearchParams({ source: 'assistant' });
  if (patientId && patientId > 0) search.set('patientId', String(patientId));
  if (issuedAt) search.set('issuedAt', issuedAt);
  return `/assistant/prescription/${prescriptionId}?${search.toString()}`;
}

/**
 * 从处方详情的查询参数恢复“我的处方”列表上下文。
 * @param search 详情页地址中的查询参数
 * @returns 处方列表返回路径
 */
export function buildMinePrescriptionListPath(search: URLSearchParams): string {
  const params = new URLSearchParams();
  ['patientId', 'startDate', 'endDate'].forEach((key) => {
    const value = search.get(key);
    if (value) params.set(key, value);
  });
  const query = params.toString();
  return `/mine/prescriptions${query ? `?${query}` : ''}`;
}

/** 将本地日期格式化为 HTML date 输入框要求的 YYYY-MM-DD。 */
function formatDateInput(value: Date): string {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, '0');
  const day = String(value.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/** 获取会话存储；隐私模式或测试环境不可用时使用内存回退。 */
function getPrescriptionDisplayNumberStorage(): PrescriptionDisplayNumberStorage {
  try {
    if (typeof window !== 'undefined' && window.sessionStorage) return window.sessionStorage;
  } catch {
    // 某些浏览器隐私策略会拒绝访问会话存储，使用页面内缓存继续保证当前视图一致。
  }
  return {
    getItem: (key) => fallbackDisplayNumberStorage.get(key) || null,
    setItem: (key, value) => { fallbackDisplayNumberStorage.set(key, value); },
  };
}

/**
 * B 端通用时间参数常量（毫秒）：轮询间隔、心跳、重连退避、防抖、超时。
 *
 * 集中维护与后端 / C 端约定的时间口径，避免各页面散落魔法值导致口径漂移。
 */

/** 接诊台 / 在线问诊列表轮询间隔（15s） */
export const POLL_INTERVAL_CONSULT = 15_000;

/** 倒计时 Hook 计时器步长（1s） */
export const COUNTDOWN_TICK_MS = 1000;

/** 全局轻量请求超时：token 解析等（5s） */
export const REQUEST_TIMEOUT_MS = 5000;

/** WebSocket 心跳间隔（10s，与 STOMP heart-beat 协商值一致） */
export const WS_HEARTBEAT_INTERVAL_MS = 10_000;

/** WebSocket 重连退避基数（首次 1s，之后指数增长 2^retry） */
export const WS_RECONNECT_BASE_MS = 1000;

/** WebSocket 重连退避上限（30s） */
export const WS_RECONNECT_MAX_MS = 30_000;

/** 处方明细变化预检防抖间隔（400ms） */
export const DEBOUNCE_PRECheck_MS = 400;

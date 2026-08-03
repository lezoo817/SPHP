"""进程内滑动窗口限流降级（系分 §10.4，P1-4）。

Redis 不可用 / 连接池耗尽时，限流中间件降级为本模块的进程内滑动窗口计数，
避免"Redis 故障即全放行"（攻击者耗尽连接池即可绕过限流，实测 300 并发全放行）。

实现：带时间戳的环形缓冲（deque），O(1) 追加 + 惰性剪枝过期时间戳。
仅单进程生效（每 worker 独立计数），Redis 恢复后自动切回精确的 Redis 限流；
本模块是降级兜底，精确性要求低于可用性要求。
"""

import logging
import threading
import time
from collections import deque
from typing import Any

logger = logging.getLogger(__name__)

# 全局锁（每 worker 独立），保护内存窗口并发访问
_lock = threading.Lock()
# key -> deque[timestamp, ...]，按时间升序
_WINDOWS: dict[str, deque[float]] = {}


def check_in_memory_rate_limit(user_id: str, limit: int, window: float = 60.0) -> bool:
    """进程内滑动窗口限流检查。

    Redis 不可用时降级调用（中间件内 ``try/except`` 捕获 Redis 异常后切到此）。
    与 Redis 版语义一致：拒绝请求不记录（不延长锁定窗口）。

    Args:
        user_id: 用户标识，窗口键。
        limit: 窗口内允许的最大请求数。
        window: 窗口大小（秒），默认 60。

    Returns:
        True = 放行（窗口内），False = 拒绝（超限）。
    """
    now = time.time()
    cutoff = now - window
    with _lock:
        buf = _WINDOWS.get(user_id)
        if buf is None:
            buf = _WINDOWS[user_id] = deque()
        # 惰性剪枝：弹出所有过期时间戳（队首最小，升序保证正确）
        while buf and buf[0] <= cutoff:
            buf.popleft()
        if len(buf) >= limit:
            # 拒绝：不记录本次请求（与 Redis 版拒绝不计数一致）
            return False
        buf.append(now)
        return True


def _reset_memory_rate_limit() -> None:
    """清空内存窗口（测试用，生产不调用）。"""
    with _lock:
        _WINDOWS.clear()


def _memory_rate_limit_stats() -> dict[str, Any]:
    """返回内存窗口统计（测试/诊断用）。"""
    with _lock:
        return {k: len(v) for k, v in _WINDOWS.items()}

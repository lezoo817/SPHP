"""结构化审计日志。

每次 Agent 工具调用记录：会话 ID、用户 ID、工具名、耗时、结果。
当前输出到 structlog → stdout，后续可切至 PG/ES。
"""

import structlog
import time
from typing import Any

logger = structlog.get_logger("audit")


def log_tool_call(
    session_id: str,
    user_id: str | None,
    tool_name: str,
    params_hash: str,
    result: str,
    duration_ms: float,
    confirm_method: str = "none",
    trigger: str = "agent",
) -> None:
    """记录一次工具调用审计。

    Args:
        session_id: 对话唯一标识。
        user_id: 操作发起人。
        tool_name: 被调用的工具名。
        params_hash: 参数摘要（脱敏 hash）。
        result: "success" / "failed"。
        duration_ms: 调用耗时（毫秒）。
        confirm_method: 确认方式（none / click / password）。
        trigger: 触发方式（agent / manual）。
    """
    logger.info(
        "tool_call",
        session_id=session_id,
        user_id=user_id,
        tool=tool_name,
        params_hash=params_hash,
        result=result,
        duration_ms=round(duration_ms, 2),
        confirm=confirm_method,
        trigger=trigger,
        timestamp=time.time(),
    )

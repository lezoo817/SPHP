"""JWT 鉴权节点（系分 §5.1，M6-B1 鉴权去重后）。

中间件已在 HTTP 层调 Java token/parse 一次并注入 request.state，
``_build_initial_state`` 将身份字段（user_id/roles/dept_id/...）写入
AgentState，本节点直接消费已解析身份，**每请求仅一次 Java 鉴权**。

仅当身份缺失但存在 ``jwt_token``（绕过中间件的直连图调用场景）时，
才兜底调一次 token/parse，保证非 HTTP 路径仍可鉴权。
"""

import logging
from typing import Any

from app.api.middleware.jwt_auth import parse_token
from app.orchestrator.state import AgentState

logger = logging.getLogger(__name__)


class AgentAuthError(Exception):
    """Agent 鉴权失败异常（系分 §5.3.2）。"""

    def __init__(self, message: str, code: str = "AUTH_FAILED"):
        self.message = message
        self.code = code
        super().__init__(self.message)


async def auth_node(state: AgentState) -> dict[str, Any]:
    """JWT 鉴权节点（系分 §5.1）。

    HTTP 主路径：中间件已解析 token 并注入身份到 request.state（再经
    ``_build_initial_state`` 写入 AgentState），本节点直接放行，不产生
    额外 Java 调用（每请求单次鉴权）。

    兜底路径：身份字段缺失但存在 jwt_token（直连图调用），解析一次。

    Args:
        state: 当前图状态，含 user_id / roles / scope / jwt_token。
            scope: c_end / b_end。
            jwt_token: 从 Header 提取的 JWT Token（不含 "Bearer " 前缀）。

    Returns:
        dict: 部分状态更新；主路径返回空 dict（身份已在初始状态中）。

    Raises:
        无：鉴权失败时降级为匿名用户，不阻塞流程。
    """
    scope = state.get("scope", "c_end")

    # 中间件已注入身份（user_id 或 roles 非空）-> 直接放行
    if state.get("user_id") is not None or state.get("roles") is not None:
        return {}

    jwt_token = state.get("jwt_token")

    # 无 token 且无注入身份 -> 匿名降级
    if not jwt_token:
        logger.warning("JWT token缺失，降级为匿名用户")
        return {"user_id": None}

    # 兜底：直连图调用（绕过中间件），解析一次 token
    try:
        user_info = await parse_token(jwt_token, scope)

        if not user_info:
            logger.warning("JWT validation failed for scope=%s，降级为匿名用户", scope)
            return {"user_id": None}

        result: dict[str, Any] = {"user_id": user_info.get("userId")}

        # B端场景：额外写入角色和科室信息
        if scope == "b_end":
            result.update(
                {
                    "roles": user_info.get("roles", []),
                    "dept_id": user_info.get("deptId"),
                    "doctor_id": user_info.get("doctorId"),
                    "hospital_id": user_info.get("hospitalId"),
                }
            )
            logger.info(
                "B端鉴权成功: userId=%s, roles=%s, deptId=%s",
                result["user_id"],
                result["roles"],
                result["dept_id"],
            )
        else:
            # C端场景：hospital_id 从 context 获取（已在 state 中）
            logger.info("C端鉴权成功: userId=%s", result["user_id"])

        return result

    except Exception as e:
        logger.error("鉴权节点异常: %s，降级为匿名用户", str(e))
        return {"user_id": None}

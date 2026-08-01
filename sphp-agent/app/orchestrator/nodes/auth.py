"""JWT 鉴权节点（系分 §5.1）。

从请求状态获取 JWT + scope，调用 Java token/parse 接口换取用户信息。
C端返回 userId/account/tokenExpiresAt，B端额外返回 roles/deptId/doctorId/hospitalId。
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

    从请求状态获取 JWT Token，调用 Java token/parse 接口校验并获取用户信息。
    B端场景同时写入 roles/dept_id/doctor_id/hospital_id。

    Args:
        state: 当前图状态，包含 scope 和 jwt_token 字段。
            scope: c_end / b_end，决定调用哪个 token/parse 接口。
            jwt_token: 从 Header 提取的 JWT Token（不含 "Bearer " 前缀）。

    Returns:
        dict: 部分状态更新，包含以下字段：
            - user_id: 用户唯一标识（C端/B端通用）
            - account: 用户账号（C端/B端通用）
            - roles: 用户角色列表（仅B端，如 ["ADMIN", "DOCTOR"]）
            - dept_id: 所属科室ID（仅B端）
            - doctor_id: 关联医生ID（仅B端医生角色）
            - hospital_id: 医院ID（B端从token解析，C端从context获取）

    Raises:
        无：鉴权失败时降级为匿名用户，不阻塞流程。
    """
    scope = state.get("scope", "c_end")
    jwt_token = state.get("jwt_token")

    # 无JWT token时，降级为匿名用户（测试场景）
    if not jwt_token:
        logger.warning("JWT token缺失，降级为匿名用户")
        return {
            "user_id": None,
            "account": "anonymous",
        }

    try:
        # 调用 Java token/parse 接口
        user_info = await parse_token(jwt_token, scope)

        if not user_info:
            logger.warning("JWT validation failed for scope=%s，降级为匿名用户", scope)
            return {
                "user_id": None,
                "account": "anonymous",
            }

        # 构造返回的状态更新
        result: dict[str, Any] = {
            "user_id": user_info.get("userId"),
            "account": user_info.get("account"),
        }

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
        return {
            "user_id": None,
            "account": "anonymous",
        }

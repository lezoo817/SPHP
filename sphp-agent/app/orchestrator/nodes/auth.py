"""JWT 鉴权节点（系分 §5.1）。

从 Header 取 JWT + 从请求体取 scope，调 Java token/parse 换取 userId。
"""

from app.orchestrator.state import AgentState


async def auth_node(state: AgentState) -> dict:
    """从 JWT 换取 userId，写入 state。scope 由请求体传入。

    失败抛 AgentAuthError。
    """
    # TODO: 调用 app.api.middleware.jwt_auth.parse_token
    # 从 state 中获取 scope 和 jwt_token
    # 调 Java token/parse → 返回 userId
    return {"user_id": None}  # 占位，实际由中间件/路由注入

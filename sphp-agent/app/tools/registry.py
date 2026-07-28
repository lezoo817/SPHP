"""工具注册表。

对应 PRD 7.2 工具调用矩阵：每个工具声明 security_level 和 requires_confirmation，
由 executor.py 在执行时做 L1-L4 拦截。

工具定义结构：
    {
        "name": "query_available_slots",
        "description": "查询号源余量",
        "scope": "c_end",              # c_end / b_end
        "security_level": "L1",        # L1(查询) / L2(业务) / L3(资金) / L4(禁止)
        "requires_confirmation": False,
        "api": {
            "method": "GET",
            "url": "{c_end_base_url}/api/registration/slots",
        },
    }
"""

from enum import Enum
from typing import Any


class SecurityLevel(str, Enum):
    """安全等级，对应 PRD 7.3。"""
    L1 = "L1"  # 查询级：只读，无需确认
    L2 = "L2"  # 业务级：创建/修改业务数据，需用户确认
    L3 = "L3"  # 资金级：仅限手动，Agent 不可调用
    L4 = "L4"  # 禁止级：仅限医生/管理员，Agent 不可调用


class ToolScope(str, Enum):
    """工具服务端。"""
    C_END = "c_end"
    B_END = "b_end"


class ToolDef:
    """单个工具的定义。"""

    def __init__(
        self,
        name: str,
        description: str,
        scope: ToolScope,
        security_level: SecurityLevel,
        requires_confirmation: bool,
        api_method: str,
        api_path: str,
        param_schema: dict[str, Any] | None = None,
    ):
        self.name = name
        self.description = description
        self.scope = scope
        self.security_level = security_level
        self.requires_confirmation = requires_confirmation
        self.api_method = api_method
        self.api_path = api_path
        self.param_schema = param_schema or {}

    def to_openai_schema(self) -> dict:
        """转换为 OpenAI Function Calling 格式，供 LLM 识别。"""
        return {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": {
                    "type": "object",
                    "properties": self.param_schema,
                },
            },
        }


# ---- 注册表 ----
_registry: dict[str, ToolDef] = {}


def register(tool: ToolDef) -> None:
    """注册一个工具。"""
    if tool.name in _registry:
        raise ValueError(f"工具已存在: {tool.name}")
    _registry[tool.name] = tool


def get_tool(name: str) -> ToolDef | None:
    return _registry.get(name)


def get_tools_by_scope(scope: ToolScope) -> list[ToolDef]:
    """获取某端的所有工具（排除 L3/L4，Agent 不可调用）。"""
    return [
        t for t in _registry.values()
        if t.scope == scope and t.security_level not in (SecurityLevel.L3, SecurityLevel.L4)
    ]


def get_openai_schemas(scope: ToolScope) -> list[dict]:
    """获取某端工具的 OpenAI schema 列表，传给 LLM。"""
    return [t.to_openai_schema() for t in get_tools_by_scope(scope)]

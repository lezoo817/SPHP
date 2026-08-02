"""Function Calling Schema 注册中心。

对应系分 §5.3 / §7.2：工具定义使用 ToolSchema dataclass，
注册到全局 ToolRegistry（单例），供 LLM 推理时查询。

工具分发逻辑（§5.3.1）：
    - executor="mcp"  → MCP Client → tools/call → MCP Server → httpx → Java
    - executor="local" → 本地执行（pgvector 检索），跳过 MCP 层
"""

from dataclasses import dataclass, field
from enum import StrEnum
from typing import Any


class SecurityLevel(StrEnum):
    """安全等级，对应系分 §5.4 L1-L4 体系。"""

    L1 = "L1"  # 查询级：只读，不产生业务变更
    L2 = "L2"  # 业务级：创建/修改业务数据，需用户确认
    L3 = "L3"  # 资金级：Agent 代码硬拦截，不注册
    L4 = "L4"  # 禁止级：Agent 代码硬拦截，不注册


class ToolScope(StrEnum):
    """工具服务端。"""

    C_END = "c_end"
    B_END = "b_end"


@dataclass
class ToolSchema:
    """单个工具的 Schema 定义（系分 §7.2）。

    Attributes:
        name: 工具唯一标识，与 MCP Server 注册表一致
        description: 自然语言描述，供 LLM 理解工具用途
        parameters: JSON Schema（OpenAI Function Calling 格式）
        scope: 服务对象 c_end / b_end
        security_level: 安全等级 L1 / L2 / L3（L3 不注册）
        executor: 执行器类型 "mcp"（经 MCP Server 调 Java）或 "local"（本地执行）
    """

    name: str
    description: str
    parameters: dict[str, Any] = field(default_factory=dict)
    scope: ToolScope = ToolScope.C_END
    security_level: SecurityLevel = SecurityLevel.L1
    executor: str = "mcp"

    def to_openai_schema(self) -> dict:
        """转换为 OpenAI Function Calling 格式，供 LLM 识别。"""
        return {
            "type": "function",
            "function": {
                "name": self.name,
                "description": self.description,
                "parameters": {
                    "type": "object",
                    "properties": self.parameters.get("properties", {}),
                    "required": self.parameters.get("required", []),
                },
            },
        }


class ToolRegistry:
    """全局工具注册表（单例，系分 §5.3.1）。

    启动时加载 c_schemas + b_schemas，运行时供 LLM 推理查询。
    """

    _tools: dict[str, ToolSchema] = {}

    @classmethod
    def register(cls, schema: ToolSchema) -> None:
        """注册一个工具。"""
        if schema.name in cls._tools:
            raise ValueError(f"工具已存在: {schema.name}")
        cls._tools[schema.name] = schema

    @classmethod
    def get_tool(cls, tool_name: str) -> ToolSchema | None:
        return cls._tools.get(tool_name)

    @classmethod
    def get_tools_by_scope(cls, scope: ToolScope) -> list[ToolSchema]:
        """获取某端的所有工具（排除 L3/L4，Agent 不可调用）。"""
        return [
            t
            for t in cls._tools.values()
            if t.scope == scope and t.security_level not in (SecurityLevel.L3, SecurityLevel.L4)
        ]

    @classmethod
    def get_openai_schemas(cls, scope: ToolScope) -> list[dict]:
        """获取某端工具的 OpenAI schema 列表，传给 LLM。"""
        return [t.to_openai_schema() for t in cls.get_tools_by_scope(scope)]

    @classmethod
    def get_executor(cls, tool_name: str) -> str:
        """返回 'mcp' 或 'local'（系分 §5.3.1）。"""
        tool = cls._tools.get(tool_name)
        if tool is None:
            raise KeyError(f"工具不存在: {tool_name}")
        return tool.executor

    @classmethod
    def is_local(cls, tool_name: str) -> bool:
        """判断工具是否为本地执行（不经 MCP Server）。"""
        return cls.get_executor(tool_name) == "local"

    @classmethod
    def clear(cls) -> None:
        """清空注册表（仅测试用）。"""
        cls._tools.clear()

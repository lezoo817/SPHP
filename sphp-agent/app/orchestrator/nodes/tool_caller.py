"""工具调用决策节点（系分 §5.1）。

LLM 决策选择哪个 Function Calling 工具及参数。
安全校验在此节点完成后：L3/L4 工具调用被拦截。
"""

from app.orchestrator.state import AgentState
from app.engine.llm.factory import build_llm
from app.engine.tools.schema_registry import ToolRegistry, ToolScope


async def tool_caller(state: AgentState) -> dict:
    """LLM 决定调用工具，返回 {"tool_calls": [...]}。

    安全校验在此完成：L3/L4 工具调用被拦截。
    """
    scope = ToolScope(state.get("scope", "c_end"))
    tools = ToolRegistry.get_openai_schemas(scope)

    # TODO: 构造 prompt → 调 LLM with tools → 解析 tool_calls
    return {"tool_calls": []}

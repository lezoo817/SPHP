"""MCP Server（系分 §4.4 / §4.5）。

stdio transport：在独立线程中启动，供外部 MCP Client 连接（当前无外部消费者，
保留以满足系分启动序列）；同时作为 MCP Client 的内存传输对端（M6-C1），
``tools/list`` / ``tools/call`` 处理器注册在 _build_server() 工厂中，
两处共用同一套处理器。

MCP 工具纯数据搬运：处理器只做协议适配，实际执行由 dispatcher 分发到
封装函数直连 Java REST，推理留在编排层。
"""

import json
import logging
import threading
from typing import Any

from app.engine.tools.schema_registry import ToolRegistry
from app.mcp_server.tools.dispatcher import _MCP_TOOL_FUNCS, dispatch_tool

logger = logging.getLogger(__name__)

# MCP Server 实例（全局单例）
_mcp_server = None
_mcp_thread = None


def _build_server() -> Any:
    """构建注册好工具处理器的 MCP Server 实例（stdio 线程与 MCP Client 共用）。

    mcp SDK 延迟导入：SDK 缺失时仅构建失败，不影响其他模块。
    """
    from mcp.server import Server

    return Server(
        "sphp-agent",
        on_list_tools=_on_list_tools,
        on_call_tool=_on_call_tool,
    )


async def _on_list_tools(ctx: Any, params: Any) -> Any:
    """tools/list 处理器：返回全部可调用的 MCP 工具（M6-C1）。

    以 _MCP_TOOL_FUNCS 为准与 ToolRegistry 求交，天然排除未注册的 L3/L4 工具。
    """
    from mcp.types import ListToolsResult, Tool

    tools = []
    for name in _MCP_TOOL_FUNCS:
        schema = ToolRegistry.get_tool(name)
        if schema is None:
            continue
        # ToolSchema.parameters 是 OpenAI 风格（无顶层 type），MCP Tool.input_schema
        # 要求 JSON Schema 对象，缺 type 时补上 "object"
        input_schema = schema.parameters or {}
        if "type" not in input_schema:
            input_schema = {"type": "object", **input_schema}
        tools.append(
            Tool(name=schema.name, description=schema.description, input_schema=input_schema)
        )
    return ListToolsResult(tools=tools)


async def _on_call_tool(ctx: Any, params: Any) -> Any:
    """tools/call 处理器：分发到封装函数，返回 JSON 文本结果（M6-C1）。

    从 arguments 中弹出 MCP Client 注入的 user_id，转交给 dispatch_tool 注入封装函数。
    工具执行异常直接上抛，由协议层转 JSON-RPC error，Client 侧据此区分
    "工具执行失败"与"连接层失败"，避免误触发直调回退。
    """
    from mcp.types import CallToolResult, TextContent

    tool_name = params.name
    args = dict(params.arguments or {})
    user_id = args.pop("user_id", None)
    result = await dispatch_tool(tool_name, args, user_id)
    return CallToolResult(
        content=[TextContent(type="text", text=json.dumps(result, ensure_ascii=False))]
    )


def start_mcp_server() -> None:
    """启动 MCP Server（stdio transport，独立线程）。

    系分 §4.5 启动序列步骤 5：
    - 在独立线程中启动 MCP Server
    - 监听 MCP Client 的 tools/list 和 tools/call 请求
    - 失败 -> fatal，进程退出
    """
    global _mcp_server, _mcp_thread

    try:
        import asyncio

        from mcp.server.stdio import stdio_server

        _mcp_server = _build_server()

        def _run_stdio() -> None:
            """在独立线程中运行 stdio server。"""

            async def _run() -> None:
                """运行 stdio server：连接输入/输出流后启动 MCP 协议循环。"""

                async with stdio_server() as (read_stream, write_stream):
                    await _mcp_server.run(
                        read_stream,
                        write_stream,
                        _mcp_server.create_initialization_options(),
                    )

            asyncio.run(_run())

        _mcp_thread = threading.Thread(target=_run_stdio, daemon=True, name="mcp-server")
        _mcp_thread.start()

        logger.info("MCP Server started (stdio transport)")

    except ImportError:
        logger.warning("mcp SDK not installed, MCP Server skipped. Install with: pip install mcp")
    except Exception as e:
        logger.error("MCP Server startup failed: %s", e)
        raise


def stop_mcp_server() -> None:
    """停止 MCP Server（系分 §4.5 优雅关闭）。"""
    global _mcp_server, _mcp_thread
    # stdio server 随主进程退出自动关闭
    _mcp_server = None
    _mcp_thread = None
    logger.info("MCP Server stopped")

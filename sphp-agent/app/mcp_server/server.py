"""MCP Server 启动入口（系分 §4.4 / §4.5）。

stdio transport：在独立线程中启动，监听 MCP Client 的 tools/list 和 tools/call 请求。
MCP Server 内部封装 Java REST API 调用。
"""

import logging
import threading

logger = logging.getLogger(__name__)

# MCP Server 实例（全局单例）
_mcp_server = None
_mcp_thread = None


def start_mcp_server() -> None:
    """启动 MCP Server（stdio transport，独立线程）。

    系分 §4.5 启动序列步骤 5：
    - 在独立线程中启动 MCP Server
    - 监听 MCP Client 的 tools/list 和 tools/call 请求
    - 失败 -> fatal，进程退出
    """
    global _mcp_server, _mcp_thread

    try:
        # 延迟导入，避免 mcp SDK 未安装时影响其他模块
        import asyncio

        from mcp.server import Server
        from mcp.server.stdio import stdio_server

        _mcp_server = Server("sphp-agent")

        # 注册工具处理器
        _register_tool_handlers(_mcp_server)

        def _run_stdio() -> None:
            """在独立线程中运行 stdio server。"""

            async def _run() -> None:
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


def _register_tool_handlers(server) -> None:
    """注册 MCP 工具处理器（系分 §4.5）。

    当前工具函数由编排层 tool_executor 直接调用 call_java_api，
    MCP Server 侧暂不注册工具处理器（待 MCP Client 接入后完善）。
    """
    logger.info("MCP 工具由编排层直接调用，Server 侧暂不注册")

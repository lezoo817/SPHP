"""MCP Client 封装（M6-C1）。

以同进程内存传输连接自身 MCP Server：``ClientSession.call_tool()`` 走真实
MCP ``tools/call`` 协议消息，Server 侧 ``on_call_tool`` 处理器分发到工具封装函数。

为什么用内存传输而非 stdio 子进程：
    - 同进程可复用已注册的工具处理器，无子进程生命周期 / 孤儿进程 / 启动延迟
    - E2E 对 call_java_api 的 patch 仍在同一进程生效，测试链路不变
    - 真正的跨进程 stdio 场景（外部 MCP Server 消费工具）留待真实环境联调

错误契约：
    - MCPClientError：Client 不可用（SDK 缺失 / 内存流或会话建立失败），
      调用方据此回退直调封装函数
    - 工具执行失败（Server 侧异常 -> JSON-RPC error / is_error=True）以普通异常
      向上抛，由 tool_executor._execute_mcp 捕获，不触发回退（避免同工具执行两次）

线程安全：连接为进程内单例，首次 call_tool 时惰性建立，lifespan 关闭时
close_mcp_client() 释放。
"""

import asyncio
import json
import logging
from typing import Any

from app.mcp_server.server import _build_server

logger = logging.getLogger(__name__)


class MCPClientError(RuntimeError):
    """MCP Client 不可用（连接层失败，可回退直调）。"""


class MCPClient:
    """MCP Client 单连接：内存传输连接自身 Server，封装 call_tool 的协议往返。"""

    def __init__(self) -> None:
        self._session: Any = None
        self._server: Any = None
        self._server_task: asyncio.Task | None = None
        self._streams_cm: Any = None
        # 连接锁：tool_executor 并发首调（asyncio.gather）时避免多个 call_tool
        # 同时看到 _session is None 并各自建连互相覆盖
        self._connect_lock = asyncio.Lock()

    async def _connect(self) -> None:
        """建立内存传输连接（独立子任务执行，M6-C1 关键修复）。

        ClientSession/Server 内部使用 anyio 任务组与 task_status.started()，
        若直接在调用方（常处于 Starlette 中间件 collapsing task group）内
        连接，会让这些任务组嵌套进中间件作用域，导致中间件退出时 anyio
        取消作用域错乱（"Attempted to exit a cancel scope..."）。把连接整体
        放进独立子任务，任务组归属子任务，中间件退出不再受影响。
        """
        task = asyncio.create_task(self._connect_inner())
        try:
            await task
        except asyncio.CancelledError:
            # 调用方被取消：取消子任务避免遗留半成品连接
            task.cancel()
            try:
                await task
            except BaseException:
                pass
            raise
        except Exception as e:
            raise MCPClientError(f"MCP Client 连接失败: {e}") from e

    async def _connect_inner(self) -> None:
        """实际连接逻辑：内存传输 + initialize 握手，失败时清理半成品资源。"""
        try:
            from mcp import ClientSession
            from mcp.shared.memory import create_client_server_memory_streams

            self._streams_cm = create_client_server_memory_streams()
            client_streams, server_streams = await self._streams_cm.__aenter__()
            client_read, client_write = client_streams
            server_read, server_write = server_streams

            self._server = _build_server()
            self._server_task = asyncio.create_task(
                self._server.run(
                    server_read, server_write, self._server.create_initialization_options()
                )
            )
            session = ClientSession(client_read, client_write)
            await session.__aenter__()
            await session.initialize()
            # 全部就绪后才暴露，避免并发首调读到未完成握手的会话
            self._session = session
            logger.info("MCP Client 已连接（内存传输，tools/call 走 MCP 协议）")
        except Exception:
            # 连接失败：清理半成品资源后向上抛，由 _connect 包装为 MCPClientError
            await self.close()
            raise

    async def connect(self) -> None:
        """确保连接已建立（幂等，供 lifespan 预连接与并发首调使用）。"""
        if self._session is None:
            async with self._connect_lock:
                if self._session is None:
                    await self._connect()

    async def call_tool(self, tool_name: str, arguments: dict, user_id: int | None = None) -> dict:
        """经 MCP 协议调用工具，返回封装函数结果 dict。

        Args:
            tool_name: 工具名。
            arguments: 工具参数（user_id 由本方法注入后随 MCP 消息传递）。
            user_id: 用户身份，Server 侧从 arguments 弹出并注入封装函数。

        Returns:
            dict: Server 侧 dispatch_tool 返回的 Java 信封 / 聚合 dict。

        Raises:
            MCPClientError: 连接层失败（可回退直调）。
            RuntimeError: 工具执行失败（is_error 或结果不可解析）。
        """
        await self.connect()
        args = dict(arguments or {})
        if user_id is not None:
            args["user_id"] = user_id
        result = await self._session.call_tool(tool_name, args)
        return _extract_result(tool_name, result)

    async def close(self) -> None:
        """关闭会话与内存流，取消 Server 任务（幂等）。"""
        session, self._session = self._session, None
        task, self._server_task = self._server_task, None
        cm, self._streams_cm = self._streams_cm, None
        self._server = None

        if session is not None:
            try:
                await session.__aexit__(None, None, None)
            except Exception as e:  # noqa: BLE001 - 关闭路径不掩盖异常
                logger.debug("MCP Client 会话关闭异常: %s", e)
        if task is not None:
            task.cancel()
            try:
                await task
            except (asyncio.CancelledError, Exception):  # noqa: BLE001
                pass
        if cm is not None:
            try:
                await cm.__aexit__(None, None, None)
            except Exception as e:  # noqa: BLE001
                logger.debug("MCP 内存流关闭异常: %s", e)
        logger.info("MCP Client 已关闭")


def _extract_result(tool_name: str, result: Any) -> dict:
    """从 CallToolResult 提取 JSON 结果（无有效内容时抛异常）。"""
    if getattr(result, "is_error", False):
        raise RuntimeError(f"MCP 工具 {tool_name} 执行返回错误")
    texts = [
        c.text for c in (getattr(result, "content", None) or []) if getattr(c, "type", "") == "text"
    ]
    text = "".join(texts)
    if not text:
        raise RuntimeError(f"MCP 工具 {tool_name} 无返回内容")
    return json.loads(text)


# ---- 进程内单例 ----
_client: MCPClient | None = None
_client_lock: asyncio.Lock | None = None


def _get_lock() -> asyncio.Lock:
    """惰性创建连接锁（避免模块导入时绑定事件循环）。"""
    global _client_lock
    if _client_lock is None:
        _client_lock = asyncio.Lock()
    return _client_lock


async def get_mcp_client() -> MCPClient:
    """返回进程内单例 MCPClient（连接在首次 call_tool 时惰性建立）。"""
    global _client
    if _client is None:
        async with _get_lock():
            if _client is None:
                _client = MCPClient()
    return _client


async def call_tool(tool_name: str, arguments: dict, user_id: int | None = None) -> dict:
    """便捷入口：经单例 Client 调用 MCP 工具。"""
    client = await get_mcp_client()
    return await client.call_tool(tool_name, arguments, user_id)


async def close_mcp_client() -> None:
    """关闭进程内单例 Client（幂等，供 lifespan 关闭与测试清理）。"""
    global _client
    if _client is not None:
        await _client.close()
        _client = None

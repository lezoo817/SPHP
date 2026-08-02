"""FastAPI 应用入口（系分 §4.4 / §4.5）。

FastAPI + MCP Server 启动 + lifespan 管理。
"""

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.infrastructure.config.settings import get_settings

logger = logging.getLogger(__name__)

# 应用版本号（统一引用，避免多处硬编码）
APP_VERSION = "2.0.0"


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理（系分 §4.5 启动序列）。

    1. 加载配置
    2. 初始化基础设施连接（PG、Redis）
    3. 初始化引擎层（LLM、Embedding）
    4. 注册 Function Calling Schema
    5. 启动 MCP Server
    6. 注册 FastAPI 路由
    """
    settings = get_settings()
    logging.basicConfig(level=getattr(logging, settings.log_level, logging.INFO))

    # 步骤 3.5：校验 Java 接口契约表（fail-fast，防止运行期静默降级）
    from app.infrastructure.java_api_map import JAVA_API_MAP, validate_contract

    validate_contract()
    logger.info("Java API contract validated (%d 条)", len(JAVA_API_MAP))

    # 步骤 4：注册工具 Schema
    from app.engine.tools.b_schemas import register_b_tools
    from app.engine.tools.c_schemas import register_c_tools

    register_c_tools()
    register_b_tools()
    logger.info("Tool schemas registered")

    # 步骤 5：启动 MCP Server
    from app.mcp_server.server import start_mcp_server, stop_mcp_server

    try:
        start_mcp_server()
        logger.info("MCP Server started (stdio transport)")
    except Exception as e:
        logger.error("MCP Server startup failed: %s", e)
        # MCP Server 启动失败为 fatal，始终终止进程
        raise

    yield

    # 优雅关闭
    stop_mcp_server()

    # 关闭基础设施连接
    from app.infrastructure.cache.redis_client import close_redis
    from app.infrastructure.java_client import close_client

    await close_client()
    await close_redis()
    logger.info("Agent shutdown complete")


async def health() -> dict:
    """健康检查端点（系分 §4.5）。检查 PG / Redis / LLM 连通性。"""
    checks = {
        "pg": await _check_pg(),
        "redis": await _check_redis(),
        "llm": _check_llm(),
    }
    overall = "healthy" if all(v == "ok" for v in checks.values()) else "unhealthy"
    return {"status": overall, "checks": checks, "version": APP_VERSION}


async def _check_pg() -> str:
    """检查 PostgreSQL（pgvector）连通性。"""
    try:
        from app.engine.rag.vectorstore import get_vectorstore

        return "ok" if get_vectorstore() else "error"
    except Exception:
        return "error"


async def _check_redis() -> str:
    """检查 Redis 连通性。"""
    try:
        from app.infrastructure.cache.redis_client import get_redis

        await get_redis().ping()
        return "ok"
    except Exception:
        return "error"


def _check_llm() -> str:
    """检查 LLM 配置有效性。"""
    try:
        from app.engine.llm.factory import build_llm

        return "ok" if build_llm() else "error"
    except Exception:
        return "error"


def create_app() -> FastAPI:
    """创建 FastAPI 应用实例。"""
    settings = get_settings()
    app = FastAPI(
        title=settings.app_name,
        version=APP_VERSION,
        debug=settings.debug,
        lifespan=lifespan,
    )

    # 中间件（Starlette: 后添加的先执行，即最后添加的最先执行）
    # 执行顺序: CORS(预检) -> Tracing -> JWT(注入 user_id) -> RateLimit -> 路由
    from app.api.middleware.jwt_auth import JWTAuthMiddleware
    from app.api.middleware.rate_limit import RateLimitMiddleware
    from app.api.middleware.tracing import TracingMiddleware

    app.add_middleware(RateLimitMiddleware)
    app.add_middleware(JWTAuthMiddleware)
    app.add_middleware(TracingMiddleware)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.cors_origins,
        allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE"],
        allow_headers=["*"],
        allow_credentials=True,
    )

    # ---- 路由注册 ----
    from app.api.routes.chat import router as chat_router
    from app.api.routes.knowledge import router as knowledge_router

    app.include_router(chat_router, prefix="/api", tags=["对话"])
    app.include_router(knowledge_router, tags=["知识库"])
    app.add_api_route("/health", health, methods=["GET"])

    return app


app = create_app()


def run() -> None:
    """从 .env 的 AGENT_HOST / AGENT_PORT 读取监听地址并启动。

    python -m app.main
    等价于 uvicorn app.main:app --host <AGENT_HOST> --port <AGENT_PORT>。
    """
    import uvicorn

    settings = get_settings()
    uvicorn.run(
        "app.main:app",
        host=settings.agent_host,
        port=settings.agent_port,
        reload=settings.debug,
    )


if __name__ == "__main__":
    run()

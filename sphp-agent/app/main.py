"""FastAPI 应用入口。"""

from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.infrastructure.config.settings import get_settings


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 启动：注册工具
    from app.engine.tools.c_tools import register_c_tools
    from app.engine.tools.b_tools import register_b_tools
    register_c_tools()
    register_b_tools()
    yield
    # 关闭钩子


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(
        title=settings.app_name,
        debug=settings.debug,
        lifespan=lifespan,
    )

    # TODO: 生产环境收敛 allow_origins 到 C 端 H5 / B 端域名
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # ---- 路由注册 ----
    from app.api.routes.chat import router as chat_router
    from app.api.routes.confirm import router as confirm_router
    from app.api.routes.knowledge import router as knowledge_router

    app.include_router(chat_router, prefix="/api/agent", tags=["对话"])
    app.include_router(confirm_router, prefix="/api/agent", tags=["确认"])
    app.include_router(knowledge_router)

    @app.get("/health")
    async def health() -> dict:
        return {"status": "ok", "service": settings.app_name}

    return app


app = create_app()

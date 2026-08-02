"""知识库管理接口：文档入库 + 检索测试。

⚠️ 入库为高危操作，需登录鉴权（后续应限制为 B 端 ADMIN 角色，
待中间件注入 roles 后补充）。
"""

import tempfile
from pathlib import Path

from fastapi import APIRouter, File, Form, HTTPException, Request, UploadFile

from app.engine.rag.ingest import ingest_directory, ingest_file
from app.engine.rag.search import format_context, search_knowledge
from app.infrastructure.config.settings import get_settings

router = APIRouter(prefix="/api/knowledge", tags=["知识库"])

# 上传文件大小上限（字节）
_MAX_UPLOAD_BYTES = 50 * 1024 * 1024  # 50 MB
# 检索 top_k 上限
_MAX_TOP_K = 50


def _require_auth(request: Request) -> None:
    """校验已登录（user_id 由 JWT 中间件注入）。

    debug 模式跳过鉴权便于本地测试；生产模式要求 user_id 非空。
    TODO: 后续应校验 B 端 ADMIN 角色（需中间件注入 roles）。
    """
    settings = get_settings()
    if settings.debug:
        return
    if getattr(request.state, "user_id", None) is None:
        raise HTTPException(status_code=401, detail="未授权：请先登录")


@router.post("/ingest/file")
async def ingest_single_file(request: Request, file: UploadFile = File(...)) -> dict:
    """上传单个文件入库（支持 .txt / .md / .pdf / .csv）。"""
    _require_auth(request)

    if not file.filename:
        raise HTTPException(status_code=400, detail="文件名缺失")

    suffix = Path(file.filename).suffix.lower()
    if suffix not in (".txt", ".md", ".pdf", ".csv"):
        raise HTTPException(status_code=400, detail=f"不支持的格式: {suffix}")

    content = await file.read()
    if len(content) > _MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="文件过大，上限 50MB")

    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        tmp.write(content)
        tmp_path = tmp.name

    try:
        # ingest_file 为 async（内部 await 向量化 + 写库），直接 await
        chunks = await ingest_file(tmp_path)
        return {"status": "success", "file": file.filename, "chunks": chunks}
    finally:
        Path(tmp_path).unlink(missing_ok=True)


@router.post("/ingest/directory")
async def ingest_dir(request: Request, path: str = Form(...)) -> dict:
    """指定服务器本地目录批量入库。

    路径须在 ``KB_INGEST_ROOT`` 配置的根目录内，防止路径遍历。
    """
    _require_auth(request)

    dir_path = Path(path).resolve()
    settings = get_settings()

    if not settings.kb_ingest_root:
        raise HTTPException(status_code=400, detail="未配置入库根目录（KB_INGEST_ROOT）")

    allowed_root = Path(settings.kb_ingest_root).resolve()
    if dir_path != allowed_root and allowed_root not in dir_path.parents:
        raise HTTPException(status_code=400, detail="目录不在允许的入库根目录内")

    if not dir_path.is_dir():
        raise HTTPException(status_code=404, detail=f"目录不存在: {path}")

    chunks = await ingest_directory(dir_path)
    return {"status": "success", "directory": path, "total_chunks": chunks}


@router.get("/search")
async def search(request: Request, q: str, top_k: int = 5) -> dict:
    """检索测试：输入问题，返回最相关的知识片段。"""
    _require_auth(request)

    top_k = max(1, min(top_k, _MAX_TOP_K))
    results = await search_knowledge(query=q, top_k=top_k)
    return {
        "query": q,
        "results": results,
        "context_preview": format_context(results),
    }

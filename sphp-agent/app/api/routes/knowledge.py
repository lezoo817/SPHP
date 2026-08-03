"""知识库管理接口（系分 §6.5）：文档入库 + 检索测试。

收敛到系分 §6.5 定义的单接口：
    - POST /api/knowledge/ingest   单文件入库（file + title + category + source）
    - GET  /api/knowledge/search   检索测试（q + top_k + category）
原 /ingest/file、/ingest/directory 端点已移除：/ingest/directory 为内部批量
工具（引擎层 ingest_directory），不暴露为对外 API。

⚠️ 入库为高危写操作，限制为 B 端 ADMIN 角色（M5-T3）；检索测试仅需登录。
错误响应统一为系分 §6.1 信封 {code, message, data, traceId}。
"""

import logging
import tempfile
import time
from pathlib import Path
from typing import Any

from fastapi import APIRouter, File, Form, Request, UploadFile
from fastapi.responses import JSONResponse

from app.api.schemas.envelope import error_response, success_response
from app.engine.rag.ingest import ingest_file
from app.engine.rag.search import search_knowledge
from app.infrastructure.config.settings import get_settings

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/knowledge", tags=["知识库"])

# 上传文件大小上限（字节，系分 §6.5.1：单文件 ≤ 10MB）
_MAX_UPLOAD_BYTES = 10 * 1024 * 1024
# 检索 top_k 上限（系分 §6.5.2：最大 20）
_MAX_TOP_K = 20
# 分类标签（系分 §6.5.1）：patient_edu（患者科普）/ clinical_ref（临床参考）
_CATEGORIES = {"patient_edu", "clinical_ref"}


def _require_auth(request: Request, *, admin_only: bool = False) -> JSONResponse | None:
    """校验登录；admin_only=True 时额外校验 B 端 ADMIN 角色（M5-T3）。

    debug 模式仅跳过登录校验（user_id）便于本地测试检索；写操作
    （admin_only=True）仍校验 ADMIN 角色，防止未授权投毒 RAG（NP-2）。
    生产模式要求 user_id 非空，入库还需 request.state.roles 含 "ADMIN"。

    Args:
        request: FastAPI 请求，roles 由 JWT 中间件从 B 端 token/parse 注入。
        admin_only: 是否要求 ADMIN 角色（入库等高危写操作）。

    Returns:
        JSONResponse | None: 鉴权失败返回统一信封错误响应；通过返回 None。
    """
    settings = get_settings()
    # debug 模式仅跳过登录校验（user_id）便于本地测试检索；
    # 写操作（admin_only）仍校验 ADMIN 角色，防止未授权投毒 RAG（NP-2）
    if not settings.debug:
        if getattr(request.state, "user_id", None) is None:
            return _error(request, 401, "AUTH_INVALID", "未授权：请先登录")
    if admin_only:
        roles = getattr(request.state, "roles", []) or []
        if "ADMIN" not in roles:
            return _error(request, 403, "FORBIDDEN", "无权限：需要管理员角色")
    return None


@router.post("/ingest")
async def ingest(
    request: Request,
    file: UploadFile = File(...),
    title: str = Form(...),
    category: str | None = Form(None),
    source: str | None = Form(None),
) -> JSONResponse:
    """单文件入库（系分 §6.5.1），需 B 端 ADMIN 角色。

    支持 .txt / .md / .pdf / .csv，单文件 ≤ 10MB。成功返回统一信封，
    data 含 document_id / title / chunk_count / status。

    Args:
        request: FastAPI 请求（鉴权 + traceId 取自 request.state）。
        file: 上传文档文件。
        title: 文档标题（必填），用于来源标注。
        category: 分类标签（patient_edu / clinical_ref），默认 patient_edu。
        source: 来源说明，如"《中国药典》2025版"。

    Returns:
        JSONResponse: 成功 code=00000；失败按错误码返回统一信封。
    """
    if (err := _require_auth(request, admin_only=True)) is not None:
        return err
    trace_id = getattr(request.state, "trace_id", "")

    # 参数校验（系分 §6.5.1 错误表：400 INVALID_REQUEST）
    if not file.filename:
        return _error(request, 400, "INVALID_REQUEST", "文件名缺失")
    suffix = Path(file.filename).suffix.lower()
    if suffix not in (".txt", ".md", ".pdf", ".csv"):
        return _error(request, 400, "INVALID_REQUEST", f"不支持的格式: {suffix}")

    title = title.strip()
    if not title:
        return _error(request, 400, "INVALID_REQUEST", "标题 title 不能为空")
    if category is not None and category not in _CATEGORIES:
        return _error(request, 400, "INVALID_REQUEST", f"不支持的分类: {category}")

    # P2：先校验声明大小再读取——原实现先 `await file.read()` 全量进内存再判
    # 上限，恶意大文件（如数 GB）会先打满内存再被拒。multipart 的
    # UploadFile.size 由 Starlette 按 Content-Length 填充，先判可零成本拒绝；
    # read 后仍保留实际大小兜底校验（声明与实测不符时）。
    if file.size is not None and file.size > _MAX_UPLOAD_BYTES:
        return _error(
            request,
            400,
            "INVALID_REQUEST",
            f"文件过大，上限 {_MAX_UPLOAD_BYTES // 1024 // 1024}MB",
        )

    content = await file.read()
    if len(content) > _MAX_UPLOAD_BYTES:
        return _error(
            request,
            400,
            "INVALID_REQUEST",
            f"文件过大，上限 {_MAX_UPLOAD_BYTES // 1024 // 1024}MB",
        )

    # 写临时文件供引擎层按后缀选择 Loader 读取
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        tmp.write(content)
        tmp_path = tmp.name

    try:
        # ingest_file 为 async（内部 await 向量化 + 写库），直接 await
        document_id, chunk_count = await ingest_file(
            tmp_path, title=title, category=category, source=source
        )
    except Exception as e:
        logger.error("文档入库失败: file=%s, err=%s", file.filename, e)
        return _error(request, 500, "INGEST_FAILED", "文档入库失败，请稍后重试")
    finally:
        Path(tmp_path).unlink(missing_ok=True)

    return _envelope(
        trace_id,
        {
            "document_id": document_id,
            "title": title,
            "chunk_count": chunk_count,
            "status": "indexed" if chunk_count else "failed",
        },
    )


@router.get("/search")
async def search(
    request: Request,
    q: str = "",
    top_k: int = 5,
    category: str | None = None,
) -> JSONResponse:
    """检索测试（系分 §6.5.2），模拟 C 端 search_medical_knowledge 检索流程。

    返回统一信封，data 含 query / results / total_hits / elapsed_ms；
    results 每项含 rank / score / content / source_doc / source_page / chunk_id。

    Args:
        request: FastAPI 请求（鉴权 + traceId 取自 request.state）。
        q: 检索查询词（必填）。
        top_k: 返回结果数，默认 5，最大 20。
        category: 分类过滤（patient_edu / clinical_ref），不传则不过滤。

    Returns:
        JSONResponse: 成功 code=00000；q 为空等参数错误返回 400 INVALID_REQUEST。
    """
    if (err := _require_auth(request)) is not None:
        return err
    trace_id = getattr(request.state, "trace_id", "")

    q = q.strip()
    if not q:
        return _error(request, 400, "INVALID_REQUEST", "检索词 q 不能为空")
    if category is not None and category not in _CATEGORIES:
        return _error(request, 400, "INVALID_REQUEST", f"不支持的分类: {category}")
    top_k = max(1, min(top_k, _MAX_TOP_K))

    start = time.perf_counter()
    results = await search_knowledge(query=q, top_k=top_k, category=category)
    elapsed_ms = int((time.perf_counter() - start) * 1000)

    return _envelope(
        trace_id,
        {
            "query": q,
            "results": results,
            "total_hits": len(results),
            "elapsed_ms": elapsed_ms,
        },
    )


def _envelope(trace_id: str, data: dict[str, Any]) -> JSONResponse:
    """构造成功统一信封（系分 §6.1），code=00000。

    Args:
        trace_id: 链路追踪号。
        data: 业务数据负载。

    Returns:
        JSONResponse: 200 + {code, message, data, traceId}。
    """
    return success_response(data, trace_id)


def _error(request: Request, status_code: int, code: str, message: str) -> JSONResponse:
    """构造错误统一信封（系分 §6.1）。

    Args:
        request: FastAPI 请求（从 request.state 取 trace_id 贯通链路）。
        status_code: HTTP 状态码。
        code: 错误码（INVALID_REQUEST / AUTH_INVALID / FORBIDDEN / INGEST_FAILED）。
        message: 用户可读错误提示。

    Returns:
        JSONResponse: 对应状态码 + {code, message, data, traceId}。
    """
    return error_response(code, message, getattr(request.state, "trace_id", ""), status_code)

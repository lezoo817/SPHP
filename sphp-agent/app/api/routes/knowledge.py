"""知识库管理接口：文档入库 + 检索测试。"""

import tempfile
from pathlib import Path

from fastapi import APIRouter, File, Form, UploadFile

from app.engine.rag.ingest import ingest_directory, ingest_file
from app.engine.rag.search import format_context, search_knowledge

router = APIRouter(prefix="/api/knowledge", tags=["知识库"])


@router.post("/ingest/file")
async def ingest_single_file(file: UploadFile = File(...)):
    """上传单个文件入库（支持 .txt / .md / .pdf / .csv）。"""
    suffix = Path(file.filename).suffix.lower()
    if suffix not in (".txt", ".md", ".pdf", ".csv"):
        return {"status": "error", "message": f"不支持的格式: {suffix}"}

    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        content = await file.read()
        tmp.write(content)
        tmp_path = tmp.name

    try:
        chunks = ingest_file(tmp_path)
        return {"status": "success", "file": file.filename, "chunks": chunks}
    finally:
        Path(tmp_path).unlink(missing_ok=True)


@router.post("/ingest/directory")
async def ingest_dir(path: str = Form(...)):
    """指定服务器本地目录，批量入库。"""
    dir_path = Path(path)
    if not dir_path.is_dir():
        return {"status": "error", "message": f"目录不存在: {path}"}

    chunks = ingest_directory(dir_path)
    return {"status": "success", "directory": path, "total_chunks": chunks}


@router.get("/search")
async def search(q: str, top_k: int = 5):
    """检索测试：输入问题，返回最相关的知识片段。"""
    results = await search_knowledge(query=q, top_k=top_k)
    return {
        "query": q,
        "results": results,
        "context_preview": format_context(results),
    }

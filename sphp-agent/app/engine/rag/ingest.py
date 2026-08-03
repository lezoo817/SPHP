"""文档入库管道：加载 → 切分 → 向量化 → 写入 pgvector。

支持的格式：.txt / .md / .pdf / .csv
后续可扩展 .docx / .html 等。
"""

import asyncio
import hashlib
import importlib
import logging
import uuid
from datetime import datetime
from pathlib import Path
from typing import cast

from langchain_core.documents import Document
from langchain_text_splitters import RecursiveCharacterTextSplitter

from app.engine.rag.vectorstore import get_vectorstore
from app.infrastructure.config.settings import get_settings

logger = logging.getLogger(__name__)

# 各格式对应的 LangChain Document Loader
_LOADER_MAP: dict[str, str] = {
    ".txt": "langchain_community.document_loaders.TextLoader",
    ".md": "langchain_community.document_loaders.TextLoader",
    ".pdf": "langchain_community.document_loaders.PyPDFLoader",
    ".csv": "langchain_community.document_loaders.CSVLoader",
}


def _load_file(file_path: Path) -> list[Document]:
    """根据后缀选择 Loader，返回 Document 列表。"""
    suffix = file_path.suffix.lower()
    loader_path = _LOADER_MAP.get(suffix)
    if loader_path is None:
        logger.warning("不支持的文件格式: %s，已跳过", file_path)
        return []

    # 动态导入 Loader 类（module_path 来自 _LOADER_MAP，未安装的 loader 仅在
    # 对应格式被请求时才 import，不影响模块导入本身）
    module_path, class_name = loader_path.rsplit(".", 1)
    loader_cls = getattr(importlib.import_module(module_path), class_name)

    # TextLoader 需要指定编码
    # loader_cls 动态导入为 Any，.load() 返回 Any，显式 cast 到 Document 列表
    if class_name == "TextLoader":
        return cast(list[Document], loader_cls(str(file_path), encoding="utf-8").load())
    return cast(list[Document], loader_cls(str(file_path)).load())


def _split_documents(docs: list[Document]) -> list[Document]:
    """用 RecursiveCharacterTextSplitter 切分文档。"""
    s = get_settings()
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=s.kb_chunk_size,
        chunk_overlap=s.kb_chunk_overlap,
        separators=["\n\n", "\n", "。", "！", "？", ".", "!", "?", " "],
    )
    return splitter.split_documents(docs)


def _load_and_split(file_path: Path) -> tuple[list[Document], list[Document]]:
    """读盘 + 打来源标记 + 切分（同步部分，供 to_thread 调用）。"""
    docs = _load_file(file_path)
    if not docs:
        return [], []
    for doc in docs:
        doc.metadata["source_file"] = file_path.name
    return docs, _split_documents(docs)


def _assign_deterministic_ids(chunks: list[Document]) -> None:
    """为每个 chunk 生成确定性 UUID（仅内容哈希）。

    PGVector 按 id 做 ``ON CONFLICT DO UPDATE`` upsert，因此同一内容重复入库时
    ID 相同会覆盖旧 chunk，而不是产生重复数据；内容变化则 ID 变化，旧 chunk
    不会残留。

    P1-6 修复：chunk ID 只用内容哈希（原实现拼 ``file_name:content``）。HTTP
    上传走 ``tempfile.NamedTemporaryFile``，文件名每次随机，同一文档重复提交
    时 ID 每次都不同，upsert 永不命中 → 同文档无限累积。去掉文件名后同内容
    必同 ID，重复提交自然去重；不同文档若切出相同 chunk 内容，本就应视为
    同一知识片段，合并合理。
    """
    for chunk in chunks:
        digest = hashlib.sha256(chunk.page_content.encode()).hexdigest()[:32]
        chunk.id = str(uuid.UUID(hex=digest))


async def ingest_file(
    file_path: str | Path,
    *,
    title: str | None = None,
    category: str | None = None,
    source: str | None = None,
) -> tuple[str, int]:
    """将单个文件入库，返回 (document_id, chunk_count)。

    入库时按 title/category/source 为每个 chunk 打 metadata 标签，
    供检索侧做分类过滤与来源标注（系分 §6.5.1）。缺省值：
    - title：文件主名（去后缀）
    - category：patient_edu（患者科普）
    - source：与 title 一致

    重复入库同一文件时按确定性 ID 覆盖（upsert），不产生重复数据。

    Args:
        file_path: 待入库文件路径。
        title: 文档标题，用于来源标注与检索 source_doc。
        category: 分类标签（patient_edu / clinical_ref）。
        source: 来源说明，如"《中国药典》2025版"。

    Returns:
        tuple[str, int]: (document_id, chunk_count)。document_id 为入库唯一 ID，
        无可用 chunk 时 document_id 为空串、chunk_count 为 0。
    """
    file_path = Path(file_path)
    if not file_path.exists():
        raise FileNotFoundError(f"文件不存在: {file_path}")

    title = title or file_path.stem
    category = category or "patient_edu"
    source = source or title

    # 读盘 + 切分均为同步 CPU/IO 操作，用 to_thread 避免阻塞事件循环
    docs, chunks = await asyncio.to_thread(_load_and_split, file_path)
    if not chunks:
        return "", 0

    for i, chunk in enumerate(chunks, 1):
        chunk.metadata["title"] = title
        chunk.metadata["category"] = category
        chunk.metadata["source"] = source
        # 来源页码：PDF 等 loader 自带 page，其余格式用段落序号兜底
        chunk.metadata["source_page"] = chunk.metadata.get("page", f"第{i}段")

    _assign_deterministic_ids(chunks)
    store = get_vectorstore()
    # vectorstore 为 async_mode（仅异步 engine），必须用异步入库
    await store.aadd_documents(chunks)

    document_id = _gen_document_id()
    logger.info(
        "已入库 %s（title=%s, category=%s）→ %d 个 chunk，document_id=%s",
        file_path.name,
        title,
        category,
        len(chunks),
        document_id,
    )
    return document_id, len(chunks)


def _gen_document_id() -> str:
    """生成文档唯一 ID（doc_日期_随机，系分 §6.5.1 示例 doc_20260730_001）。"""
    return f"doc_{datetime.now():%Y%m%d}_{uuid.uuid4().hex[:6]}"


async def ingest_directory(dir_path: str | Path, recursive: bool = True) -> int:
    """批量入库一个目录下的所有支持格式文件，返回总 chunk 数。

    ⚠️ 内部批量工具，不作为对外 HTTP API 暴露（系分 §6.5.1 仅单文件入库），
    供管理员脚本 / 初始化任务使用。
    """
    dir_path = Path(dir_path)
    if not dir_path.is_dir():
        raise NotADirectoryError(f"不是目录: {dir_path}")

    total = 0
    pattern = "**/*" if recursive else "*"
    for fp in sorted(dir_path.glob(pattern)):
        if fp.is_file() and fp.suffix.lower() in _LOADER_MAP:
            _, chunk_count = await ingest_file(fp)
            total += chunk_count

    logger.info("目录 %s 入库完成，共 %d 个 chunk", dir_path, total)
    return total

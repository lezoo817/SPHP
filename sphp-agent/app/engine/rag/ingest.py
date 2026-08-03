"""文档入库管道：加载 → 切分 → 向量化 → 写入 pgvector。

支持的格式：.txt / .md / .pdf / .csv
后续可扩展 .docx / .html 等。
"""

import asyncio
import hashlib
import logging
import uuid
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

    # 延迟导入，避免未安装的 loader 拖垮整个模块
    module_path, class_name = loader_path.rsplit(".", 1)
    import importlib

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


def _assign_deterministic_ids(chunks: list[Document], file_name: str) -> None:
    """为每个 chunk 生成确定性 UUID（文件名 + 内容哈希）。

    PGVector 按 id 做 ``ON CONFLICT DO UPDATE`` upsert，因此同一文件重复入库时
    ID 相同会覆盖旧 chunk，而不是产生重复数据。chunk 内容变化则 ID 变化，
    旧 chunk 不会残留。
    """
    for chunk in chunks:
        digest = hashlib.sha256(f"{file_name}:{chunk.page_content}".encode()).hexdigest()[:32]
        chunk.id = str(uuid.UUID(hex=digest))


async def ingest_file(file_path: str | Path) -> int:
    """将单个文件入库，返回写入的 chunk 数量。

    重复入库同一文件时按确定性 ID 覆盖（upsert），不产生重复数据。
    """
    file_path = Path(file_path)
    if not file_path.exists():
        raise FileNotFoundError(f"文件不存在: {file_path}")

    # 读盘 + 切分均为同步 CPU/IO 操作，用 to_thread 避免阻塞事件循环
    docs, chunks = await asyncio.to_thread(_load_and_split, file_path)
    if not chunks:
        return 0

    _assign_deterministic_ids(chunks, file_path.name)
    store = get_vectorstore()
    # vectorstore 为 async_mode（仅异步 engine），必须用异步入库
    await store.aadd_documents(chunks)

    logger.info("已入库 %s → %d 个 chunk（确定性 ID 去重）", file_path.name, len(chunks))
    return len(chunks)


async def ingest_directory(dir_path: str | Path, recursive: bool = True) -> int:
    """批量入库一个目录下的所有支持格式文件，返回总 chunk 数。"""
    dir_path = Path(dir_path)
    if not dir_path.is_dir():
        raise NotADirectoryError(f"不是目录: {dir_path}")

    total = 0
    pattern = "**/*" if recursive else "*"
    for fp in sorted(dir_path.glob(pattern)):
        if fp.is_file() and fp.suffix.lower() in _LOADER_MAP:
            total += await ingest_file(fp)

    logger.info("目录 %s 入库完成，共 %d 个 chunk", dir_path, total)
    return total

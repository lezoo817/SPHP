"""文档入库管道：加载 → 切分 → 向量化 → 写入 pgvector。

支持的格式：.txt / .md / .pdf / .csv
后续可扩展 .docx / .html 等。
"""

import logging
from pathlib import Path

from langchain_text_splitters import RecursiveCharacterTextSplitter

from app.infrastructure.config.settings import get_settings
from app.engine.rag.vectorstore import get_vectorstore

logger = logging.getLogger(__name__)

# 各格式对应的 LangChain Document Loader
_LOADER_MAP: dict[str, str] = {
    ".txt": "langchain_community.document_loaders.TextLoader",
    ".md": "langchain_community.document_loaders.TextLoader",
    ".pdf": "langchain_community.document_loaders.PyPDFLoader",
    ".csv": "langchain_community.document_loaders.CSVLoader",
}


def _load_file(file_path: Path):
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
    if class_name == "TextLoader":
        return loader_cls(str(file_path), encoding="utf-8").load()
    return loader_cls(str(file_path)).load()


def _split_documents(docs: list):
    """用 RecursiveCharacterTextSplitter 切分文档。"""
    s = get_settings()
    splitter = RecursiveCharacterTextSplitter(
        chunk_size=s.kb_chunk_size,
        chunk_overlap=s.kb_chunk_overlap,
        separators=["\n\n", "\n", "。", "！", "？", ".", "!", "?", " "],
    )
    return splitter.split_documents(docs)


def ingest_file(file_path: str | Path) -> int:
    """将单个文件入库，返回写入的 chunk 数量。"""
    file_path = Path(file_path)
    if not file_path.exists():
        raise FileNotFoundError(f"文件不存在: {file_path}")

    docs = _load_file(file_path)
    if not docs:
        return 0

    # 给每个 chunk 打上来源文件名，方便检索时溯源
    for doc in docs:
        doc.metadata["source_file"] = file_path.name

    chunks = _split_documents(docs)
    store = get_vectorstore()
    store.add_documents(chunks)

    logger.info("已入库 %s → %d 个 chunk", file_path.name, len(chunks))
    return len(chunks)


def ingest_directory(dir_path: str | Path, recursive: bool = True) -> int:
    """批量入库一个目录下的所有支持格式文件，返回总 chunk 数。"""
    dir_path = Path(dir_path)
    if not dir_path.is_dir():
        raise NotADirectoryError(f"不是目录: {dir_path}")

    total = 0
    pattern = "**/*" if recursive else "*"
    for fp in sorted(dir_path.glob(pattern)):
        if fp.is_file() and fp.suffix.lower() in _LOADER_MAP:
            total += ingest_file(fp)

    logger.info("目录 %s 入库完成，共 %d 个 chunk", dir_path, total)
    return total

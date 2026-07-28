"""知识库模块：基于 pgvector 的医疗知识 RAG 管道。

提供文档加载 → 切分 → 向量化入库 → 相似度检索的完整链路。
"""

from app.knowledge.vectorstore import get_vectorstore
from app.knowledge.ingest import ingest_file, ingest_directory
from app.knowledge.search import search_knowledge

__all__ = [
    "get_vectorstore",
    "ingest_file",
    "ingest_directory",
    "search_knowledge",
]

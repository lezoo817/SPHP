# SPHP 项目长期记忆

## 项目架构概览
- 智慧医疗平台(智愈先锋),monorepo: Java后端(8080) + Python Agent(8081) + B/C前端
- Agent 栈: FastAPI + LangGraph + MCP,Python >= 3.11
- 知识库: pgvector 向量存储,RAG 管道已完整搭建

## Agent 知识库(RAG)现状 — 已具备完整能力
- 入库: `app/engine/rag/ingest.py` 支持 .txt/.md/.pdf/.csv,内容哈希去重
- 向量化: `app/engine/rag/embedder.py` 多供应商(dashscope默认,1024维)
- 存储: `app/engine/rag/vectorstore.py` PGVector 单例,collection=medical_knowledge
- 检索: `app/engine/rag/search.py` 相似度+分数阈值(0.3)+分类过滤
- 节点: `app/orchestrator/nodes/rag.py` 注入 state.rag_context(不进messages防污染)
- 接口: `app/api/routes/knowledge.py` POST /ingest(需ADMIN) + GET /search(需登录)
- 配置: .env 中 KB_CHUNK_SIZE=500/KB_TOP_K=5/KB_MIN_SCORE=0.3

## 潜在扩展方向(用户曾咨询)
- 多知识库隔离(按 collection/hospital_id)
- 文档管理接口(列表/删除,当前缺失)
- 混合检索(向量+BM25,药品名精确匹配弱)
- rerank 精排(bge-reranker)
- 定时同步外部知识源(APScheduler)

## B 端前端知识库入库模块(已实现 2026-08-06)
- 前端直连 Agent(8081)调 /api/knowledge/ingest,与 chatStream 同模式(fetch + Bearer + X-Scope)
- 鉴权链路:jwt_auth.py 注入 request.state.roles → knowledge.py _require_auth(admin_only) 校验 ADMIN
- CORS: main.py allow_headers 含 Authorization/Content-Type/X-Scope,multipart 上传可通
- 文件: pages/knowledge/index.tsx(Upload.Dragger) + services/agent.ts(ingestKnowledge) + routes.ts + MainLayout菜单

## 编码红线(CLAUDE.md)
- 禁止私自改数据库(DDL/DML需先出脚本获批)
- 禁止私自编译构建(命令由用户手动执行)
- 禁止越界修改其他模块
- Agent 注释用中文

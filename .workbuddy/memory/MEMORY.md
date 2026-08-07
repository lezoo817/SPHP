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

## 暂存任务 01：挂号流程 select_doctor 卡片 bug（2026-08-07）

### 背景
C 端 agent 挂号服务出现"选医生卡片"bug：后端 M8-6/M8-7/M8-8 问诊选医生机制无条件应用到所有场景。

### 已完成（前端 only，用户明确要求不改后端）
- `frontend/C/user-h5/src/hooks/useAgentStream.ts` `appendSelectCard`：
  - 增加同类型卡片去重（已点选则忽略新卡、未点选则替换）
  - ~~`select_doctor` 类型卡片不渲染~~（**已撤销，用户要求保留渲染**）

### 未完成（需改后端，待用户确认后再继续）
1. **M8-8 硬约束修复**（`sphp-agent/app/orchestrator/nodes/tool_caller.py:650-651`）
   - 现状：`if selected_choice is not None:` → 只暴露 `save_pre_consultation`
   - 改为：`if selected_choice is not None and "save_pre_consultation" in {t.name for t in subgraph_tools}:`
   - 挂号白名单无 `save_pre_consultation` → 当前触发后工具列表空 → WARNING → 流程卡死
2. **`_parse_doctor_candidates` 无条件发卡**（`tool_caller.py:776-783`）
   - 加场景判定：仅问诊子图（白名单含 `save_pre_consultation`）才写 `pending_doctor_choices`
3. **`reply.py` M8-7 提示词**（行 200-215）
   - 加问诊场景判定，挂号场景不注入"请在卡片中选择"话术
4. **`registration_graph.py` 缺 scene_prompt**
   - 参照 `consult_graph.py` 的 `CONSULTATION_SCENE_PROMPT`，新增 `REGISTRATION_SCENE_PROMPT`
5. **`chat.py:434` options prompt 文案**
   - "请选择您想咨询的医生" 按场景区分（挂号→"请选择您要挂号的医生"）

### 根因链路
1. 挂号场景 LLM 调 `query_doctors` → `_parse_doctor_candidates` 无条件写 `pending_doctor_choices`
2. 用户文字提到医生名 → `_match_doctor_choice` 匹配 → `selected_choice` 非 None
3. M8-8 硬约束触发 → 工具列表过滤为只剩 `save_pre_consultation`
4. 挂号白名单无此工具 → 工具列表空 → `WARNING: scope=c_end 无可用 L1/L2 工具` → 流程结束
5. 不调 `create_appointment` → 无"确认挂号"卡片

### 继续指令
用户说"继续01任务"时，从"未完成"第 1 项开始（需先确认用户是否允许改后端）

## 编码红线(CLAUDE.md)
- 禁止私自改数据库(DDL/DML需先出脚本获批)
- 禁止私自编译构建(命令由用户手动执行)
- 禁止越界修改其他模块
- Agent 注释用中文

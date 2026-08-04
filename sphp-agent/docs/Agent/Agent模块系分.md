# 《智愈先锋》Agent 模块系统详细设计说明书
| 文档信息 | 内容 |
| --- | --- |
| 文档名称 | 智愈先锋 Agent 模块系统详细设计说明书 |
| 项目名称 | 智愈先锋 —— AI驱动的全链路医疗健康平台 |
| 编写日期 | 2026-07-31 |
| 文档版本 | V2.1 |
| 文档状态 | 更新中 |
| 编写人 | Agent 开发组 |
| 参考文档 | Agent-PRD 2.0、C 端后端系分 V1.3、B 端后端系分 V1.1 |


---

## 修订记录
| 版本 | 日期 | 修订内容 | 修订人 |
| --- | --- | --- | --- |
| V1.0 | 2026-07-28 | 初版：模块定位、功能需求、架构设计、接口设计、安全设计 | Agent 开发组 |
| V1.1 | 2026-07-29 | 重大更新：采用 BFF 统一架构；Agent 退居纯推理角色；工具执行、鉴权、上下文注入全部移交 BFF；新增 B 端 4 场景工具定义 | Agent 开发组 |
| V2.0 | 2026-07-30 | **架构重构**：废除 BFF 模式，改用 MCP 协议；Agent 担任 MCP Client，内嵌 MCP Server 封装 Java REST API；前端直连 Agent（:8081）；Java 后端退化为纯数据 API + 内部鉴权辅助；删除全部 SSE 自定义事件、BFF 工具回调接口、BFF 安全模型 | Agent 开发组 |
| V2.1 | 2026-07-31 | **前后端对齐**：SSE card 事件补 session_id；confirm 接口改为同步返回并统一 {code,message,data,traceId} 信封；B 端 token/parse 响应定稿（含 roles/deptId/doctorId/hospitalId）；C 端工具参数对齐后端 V1.3；B 端工具 API 路径统一 /admin/ 前缀（对齐后端 V1.1）；AgentState 补 B 端上下文字段；context 补 hospital_id；明确 B 端鉴权头策略（仅 X-User-Id）；confirm_token 统一 UUID4；支付超时定稿 15 分钟；ReAct 可视化（thought/action/observation 事件） | Agent 开发组 |


---

## 1. 文档概述

### 1.1 文档目的

本文档为《智愈先锋》项目 **Agent 模块（AI Python 服务）** 的详细设计说明书，面向 Agent 模块开发团队，作为编码实现的技术蓝本。文档覆盖：

+ 系统架构边界与 MCP 协议工具调用方式
+ 核心业务流程（对话模型、MCP 工具调用、L2 确认）
+ Function Calling Schema 定义（C 端 30 工具 + B 端 4 场景 9 工具）
+ 接口契约（前端→Agent 对话接口、Agent→Java 鉴权接口、MCP 工具协议）
+ 安全设计（纵深防御、JWT 鉴权方案 A、数据隔离）
+ 非功能性设计（质量属性、可维护性）

> **注：** 本模块系分不包含 C/B 端 Java 后端的业务 API 实现（详见各自系分），也不包含前端 UI 设计（详见前端系分）。本模块描述的 MCP Server 工具封装层以 Java REST API 为基础，Java 接口清单见 C 端后端系分 §5 和 B 端后端系分 §5.2。

### 1.2 术语表

| 术语 | 定义 |
| --- | --- |
| MCP | Model Context Protocol，Anthropic 提出的 AI 工具调用开放标准协议，采用 JSON-RPC 通信。Agent 通过 MCP `tools/list` 发现工具、`tools/call` 执行工具 |
| MCP Client | MCP 协议的客户端，在本项目中为 Agent 的编排层。向 MCP Server 发起工具发现和调用请求 |
| MCP Server | MCP 协议的服务端，在本项目中为 Agent 内嵌的 Python 服务。注册并暴露工具，接收 MCP Client 的调用请求，内部封装 Java REST API |
| Function Calling Schema | OpenAI 标准工具定义格式，Agent 据此向 LLM 注册可调用工具 |
| L1-L4 | 安全验证分级：L1 查询 / L2 业务 / L3 资金 / L4 禁止 |
| pgvector | PostgreSQL 向量扩展，Agent 用于知识库 RAG 检索 |
| SSE | Server-Sent Events，前端与 Agent 之间的流式文本推送协议（仅用于对话流，不用于工具调用） |
| JWT | JSON Web Token，前端携带的标准鉴权令牌 |
| 方案 A | 鉴权方案：Agent 收到前端 JWT 后，调 Java 已有的 token 解析接口（C 端 `GET /api/c/v1/auth/token/parse`、B 端 `GET /api/b/auth/token/parse`）换取 userId，JWT 密钥仅存 Java 端 |


---

## 2. 模块概述

### 2.1 定位与边界

Agent 模块是智愈先锋三大子系统（C 端小程序、B 端后台、AI Agent 服务）之一。它不是一个独立的聊天机器人，而是嵌入平台的双向 AI 服务——以对话为入口，通过 MCP 协议标准调用封装后的 Java REST API，将挂号、预约、订单、处方、随访等业务服务组织成完整闭环。

**三条核心边界**（PRD 1.3）：

| 原则 | 含义 |
|------|------|
| 能查，不能断 | 可查报告指标/药典，但不可确诊、不可开方 |
| 能建议，不能决定 | 可推荐医生/药品，但不可替人挂号、替人下单 |
| 能代办，不能代确认 | 可发起流程、创建草稿，但敏感操作必须人工确认 |

**四条治理原则**（PRD 7.1.1）：辅助而非替代、透明可追溯、最小权限、人工兜底。

### 2.2 MCP 架构说明

Agent 与后端业务 API 的交互采用 **MCP（Model Context Protocol）** 协议——Anthropic 提出的 AI 工具调用开放标准。本项目中，Agent 内嵌 MCP Server 作为工具封装层，Agent 编排层通过 MCP Client 调用 MCP Server 发现和执行业务工具。

**MCP 在本项目中的角色：**

| MCP 职责 | 说明 |
|----------|------|
| 工具发现 | Agent 编排层通过 `tools/list` 获取所有可用工具及其 Schema |
| 工具执行 | Agent 编排层通过 `tools/call` 请求执行工具，MCP Server 内部封装 Java REST API 调用 |
| 协议标准化 | 使用 JSON-RPC 标准通信，工具发现与执行遵循 MCP 规范 |

**Agent 与 Java 后端的职责边界：**

```
Java 后端（:8080）                       Agent（Python :8081）
├── 业务 Service + 数据库操作            ├── 模型推理（LLM）
├── JWT 签发与校验（token/parse）├── Function Calling Schema 定义
├── REST API（/api/c/v1/*, /api/b/*）     ├── 对话编排（LangGraph）
├── 数据权限过滤（DataScope）              ├── MCP Client（工具发现与调用）
├── 幂等与防超卖                          ├── MCP Server（封装 Java REST API）
├── 审计日志                              ├── 知识库 RAG（pgvector）
└── 内部鉴权辅助                          ├── 意图识别与路由
                                          ├── 安全校验（L3/L4 硬拦截）
                                          ├── L2 确认流程管理
                                          └── 结构化日志
```

**核心原则：**

+ **Agent 直连前端**，前端带 JWT 调 Agent 的对话端点，Agent 端口对外暴露。
+ **Agent 通过 MCP 协议调用工具**。Agent 内嵌 MCP Server 接收 `tools/call` 请求，内部向 Java 后端发 REST 请求获取数据。
+ **鉴权采用方案 A**：Agent 拿到前端 JWT 后调 Java 已有的 token 解析接口（C 端 `GET /api/c/v1/auth/token/parse`、B 端 `GET /api/b/auth/token/parse`）换取 userId，后续调 Java REST API 时在 Header 中携带 `X-User-Id` 做数据隔离。
+ **Java 后端仅提供 REST 业务数据 API 和 token 解析接口**，不参与 AI 推理、工具编排或对话管理。

调用链路详见 §4.1 整体部署视图。

### 2.3 双入口设计

平台中每个业务都支持两种操作路径：**手动操作**（用户通过页面按钮/表单自主完成）与 **Agent 代操作**（通过 AI 对话调用 API 代为完成）。两条路径共用同一套 Java 后端 REST API，防超卖等核心机制对两种入口均生效。

### 2.4 双向服务

- **C 端患者**：通过微信小程序内的 AI 导诊对话页，完成导诊、挂号、预问诊、处方解读、购药、健康管理等全流程
- **B 端医生**：嵌入医生工作台的 AI 辅助面板，提供病历检索、用药指南查询、药物相互作用检测、病历草稿生成等辅助能力


---

## 3. 功能需求

### 3.1 C 端——面向患者

C 端按业务场景聚合为 5 组工具，Agent 启动时预注册全部 C 端工具：

**场景一：智能导诊**

用户自然语言描述症状，Agent 多轮追问后推荐科室。核心流程：症状采集 → 知识库检索 → 科室推荐。

| 工具 | 说明 | 约束 |
|------|------|------|
| `create_triage_assessment` | 提交症状进行导诊评估，返回紧急程度与推荐科室 | 不可下诊断结论；急危重症识别后引导线下就医 |
| `search_medical_knowledge` | 医疗科普知识库检索 | 引用来源，标注"AI 建议仅供参考" |

**场景二：智能挂号**

按科室查询实时号源，卡片展示并分级（充足>5 / 紧张≤3 / 已约满 / 停诊），按号源充足度+职称排序。用户确认后预扣号源，进入防超卖流程。

| 工具 | 说明 | 约束 |
|------|------|------|
| `query_departments` | 查询科室列表 | L1 查询级，无业务变更 |
| `query_doctors` | 按科室查询医生及号源概览 | L1 查询级 |
| `query_schedule_slots` | 查询医生可预约时段与余量 | L1 查询级 |
| `create_appointment` | 创建挂号锁定订单（预扣号源） | L2 需用户确认；抢号失败提示"已被抢完"并推荐同科室医生 |
| `query_appointments` | 查询挂号订单列表或详情（不带 id 返回列表，带 id 返回详情） | L1 查询级 |
| `cancel_appointment` | 取消挂号锁定订单 | L2 需用户确认 |
| `join_waitlist` | 号源约满时登记候补 | L2 需用户确认；有名额释放自动通知 |
| `query_payment_status` | 查询支付单状态 | L1 查询级；Agent 不可代付，用户手动输密码 |

**场景三：在线问诊**

挂号成功后自动发起预问诊，采集主诉/现病史/既往史/过敏史/用药情况，整理成结构化摘要发给医生。医生开方后 Agent 以通俗语言解读药品。

| 工具 | 说明 | 约束 |
|------|------|------|
| `save_pre_consultation` | 提交预问诊摘要 | L2 需用户确认 |
| `query_consultations` | 查询问诊记录列表或详情（不带 id 返回列表，带 id 返回详情及消息记录） | L1 查询级 |
| `send_consultation_message` | 发送问诊文字消息 | L2 需用户确认 |
| `query_prescriptions` | 查询处方列表或详情（不带 id 返回列表，带 id 返回详情及药品明细） | L1 查询级 |
| `interpret_prescription` | 以通俗语言解读处方药品 | 不可修改处方内容；Agent 无权开方；标注"AI 建议仅供参考" |

**场景四：处方购药**

查附近有货药店（按距离+价格排序），生成草稿状态购药订单，付款后自动设置用药计划。Agent 不可代付。

| 工具 | 说明 | 约束 |
|------|------|------|
| `query_pharmacy_stock` | 查询附近药店库存与价格 | L1 查询级 |
| `create_drug_order` | 创建购药订单草稿（待付款） | L2 需用户确认 |
| `query_drug_orders` | 查询购药订单列表或详情（不带 id 返回列表，带 id 返回详情） | L1 查询级 |
| `cancel_drug_order` | 取消未支付购药订单 | L2 需用户确认 |
| `confirm_drug_receipt` | 确认购药收货 | L2 需用户确认 |

**场景五：健康档案**

查询并展示健康档案（基础资料、过敏史、既往史、就诊记录），录入检查报告并解读指标，管理用药计划和随访提醒。

| 工具 | 说明 | 约束 |
|------|------|------|
| `query_health_record` | 查询健康档案（含过敏史、既往史） | L1 查询级 |
| `manage_allergy` | 管理过敏史记录（不带 allergy_id 新增，带 allergy_id 修改） | L2 需用户确认 |
| `manage_medical_history` | 管理既往史记录（不带 history_id 新增，带 history_id 修改） | L2 需用户确认 |
| `query_reports` | 查询检查报告列表或详情（不带 id 返回列表，带 id 返回详情及指标解读） | L1 查询级；不可下诊断结论；标注"AI 建议仅供参考" |
| `create_report` | 录入检查报告 | L2 需用户确认 |
| `query_medication_plans` | 查询用药计划列表 | L1 查询级 |
| `update_medication_plan` | 暂停/恢复/完成用药计划 | L2 需用户确认 |
| `query_follow_ups` | 查询随访计划列表 | L1 查询级 |
| `confirm_follow_up` | 确认随访提醒时间 | L2 需用户确认 |
| `manage_notifications` | 管理通知（action=list 查列表，action=read 标已读） | L1 查询级 |

### 3.2 B 端——面向医生（AI 辅助面板）

B 端按场景聚合为 4 组工具，Agent 启动时预注册全部 B 端工具，由 scope 字段区分场景：

**场景一：接诊辅助**

| 工具 | 说明 | 约束 |
|------|------|------|
| `query_patient_history` | 聚合查询患者基本信息、过敏史、既往史、就诊记录、历史处方、当前用药 | 需医生授权；根据当前患者 ID 过滤 |
| `query_drug_guide` | 检索药品说明书和用药指南 | 不可编造不存在的研究证据 |
| `check_drug_interaction` | 查询药品相互作用数据（药品说明书 + 患者当前用药清单） | 仅返回数据，分析结论由编排层 LLM 生成 |
| `generate_draft_note` | 保存医生病历记录（编排层 LLM 生成草稿文本，工具负责持久化） | 仅供医生修改，不可签名写入正式病历 |

**场景二：导诊推荐**

| 工具 | 说明 | 约束 |
|------|------|------|
| `recommend_care` | 查询科室列表及对应医生号源 | 仅返回数据，推荐结论由编排层 LLM 生成 |

**场景三：处方审核**

| 工具 | 说明 | 约束 |
|------|------|------|
| `check_contraindication` | 查询药品禁忌信息 + 患者过敏史 | 仅返回数据，禁忌判断由编排层 LLM 生成 |
| `check_allergy_risk` | 查询患者过敏史 | 对应患者过敏记录 |
| `check_duplicate_medication` | 查询患者当前用药清单 | 仅返回数据，重复判断由编排层 LLM 生成 |

**场景四：报告解读**

| 工具 | 说明 | 约束 |
|------|------|------|
| `interpret_report` | 本地知识库 pgvector 检索参考范围与相关条目 | 仅返回数据，通俗化解读由编排层 LLM 生成 |

### 3.3 用例图

```mermaid
graph TB
    subgraph actors["角色"]
        P["患者/家属<br/>(C端用户)"]
        D["医生<br/>(B端用户)"]
    end

    subgraph c_use["C端用例 — AI导诊对话"]
        UC1["智能导诊<br/>症状→科室推荐"]
        UC2["智能挂号<br/>医生推荐→创建挂号单"]
        UC3["在线问诊<br/>预问诊→处方解读"]
        UC4["处方购药<br/>药店推荐→下单"]
        UC5["健康档案<br/>报告解读→用药提醒→随访"]
    end

    subgraph b_use["B端用例 — AI辅助面板"]
        UB1["接诊辅助<br/>病历检索+用药查询+草稿"]
        UB2["导诊推荐<br/>科室+医生推荐"]
        UB3["处方审核<br/>禁忌+过敏+重复用药检测"]
        UB4["报告解读<br/>指标解析+通俗化"]
    end

    P --> UC1
    P --> UC2
    P --> UC3
    P --> UC4
    P --> UC5

    D --> UB1
    D --> UB2
    D --> UB3
    D --> UB4

    UC2 -.->|"支付时转手动"| P
    UC4 -.->|"支付时转手动"| P
```


---

## 4. 系统架构

### 4.1 整体部署视图

系统由三个独立进程组成。Agent（:8081）直接对前端暴露——前端 C 端（:8001）和 B 端（:8000）分别访问 Agent 对话接口。Java 后端（:8080）仅提供 REST 业务数据 API 和内部鉴权辅助接口。

```mermaid
graph TB
    subgraph frontend["前端"]
        C_APP["C端 微信小程序 :8001<br/>· 手动操作页面<br/>· AI导诊对话页"]
        B_WEB["B端 Web后台 :8000<br/>· 医生工作台<br/>· AI辅助面板"]
    end

    subgraph agent["Agent :8081（Python · FastAPI + LangGraph）"]
        API["API接入层<br/>对话端点 + MCP协议适配"]
        ORCH["编排层<br/>意图路由 · 对话子图"]
        ENG["引擎层<br/>LLM · Function Calling · RAG · 记忆"]
        MCP_INNER["MCP Server（内嵌）<br/>工具封装 · Java REST 客户端"]
        API --> ORCH --> ENG
        ENG --> MCP_INNER
    end

    subgraph backend["Java 后端 :8080（Spring Boot）"]
        AUTH["Token 解析<br/>GET /api/c/v1/auth/token/parse"]
        C_API["C端业务 API<br/>认证 · 挂号 · 问诊 · 购药 · 健康"]
        B_API["B端业务 API<br/>医院管理 · 排班 · 处方 · 诊疗"]
    end

    subgraph storage["基础设施"]
        PG["PostgreSQL 16<br/>业务数据 + pgvector向量"]
        REDIS["Redis 7<br/>L2 confirm_token · 限流 · 对话缓存"]
        MQ["RabbitMQ<br/>超时释放 · 通知投递"]
    end

    C_APP -->|"POST /api/chat/stream<br/>Bearer JWT"| API
    B_WEB -->|"POST /api/chat/stream<br/>Bearer JWT"| API
    API -.->|"JWT校验"| AUTH
    MCP_INNER -->|"HTTP REST<br/>X-User-Id"| C_API
    MCP_INNER -->|"HTTP REST<br/>X-User-Id"| B_API
    C_API --> PG
    C_API --> REDIS
    C_API --> MQ
    B_API --> PG
    ENG --> PG
    ORCH --> REDIS
```

> **调用链路**：前端 → Agent :8081（JWT 鉴权 → LLM 推理）。Agent 需要业务数据时，编排层通过 MCP Client 调用内嵌 MCP Server（`tools/call`），MCP Server 向 Java :8080 发 REST 请求，Java 查数据库返回数据。Agent 不直连业务数据库——所有业务数据的读写都经过 Java REST API。

### 4.2 四层内部分层

Agent 内部仍为四层架构，分层原则不变：

```mermaid
graph TB
    subgraph api["① API接入层 — 协议适配"]
        AR["routes/<br/>chat.py · knowledge.py"]
        AM["middleware/<br/>jwt_auth.py · rate_limit.py"]
        AS["schemas/<br/>chat.py"]
    end

    subgraph orch["② 编排层 — 只做调度不做实现"]
        OG["graphs/<br/>main_graph · triage_graph<br/>consult_graph · pharmacy_graph"]
        ON["nodes/<br/>intent · rag · safety<br/>tool_caller · reply"]
        OS["state.py — AgentState"]
    end

    subgraph eng["③ 引擎层 — 可独立测试"]
        ELLM["llm/<br/>factory · prompts/"]
        ETOOL["tools/<br/>schema_registry · c_schemas · b_schemas"]
        ERAG["rag/<br/>vectorstore · search · ingest · embedder"]
        EMEM["memory/<br/>buffer · summary"]
    end

    subgraph mcp["MCP Server（内嵌）"]
        MSRV["server.py<br/>MCP Server 启动入口"]
        MTOOLS["tools/<br/>drug_query · appointment<br/>prescription · pharmacy · health"]
    end

    subgraph infra["④ 基础设施层 — 零业务依赖"]
        IAUDIT["audit/logger.py<br/>structlog 结构化日志"]
        ICACHE["cache/redis_client.py<br/>限流 · confirm_token"]
        IJAVA["java_client.py<br/>httpx Java REST 客户端"]
        ICFG["config/settings.py<br/>.env → Pydantic Settings"]
    end

    api --> orch
    api --> infra
    orch --> eng
    orch --> mcp
    orch --> infra
    eng --> infra
    mcp --> infra
```

### 4.3 分层规则

| 层 | 路径 | 能 import | 不能做的事 |
|---|------|-----------|------------|
| API 接入层 | `app/api/` | orchestrator, infrastructure, engine | 不直接调 httpx/psycopg |
| 编排层 | `app/orchestrator/` | engine, mcp_server, infrastructure | 不直接操作 I/O，只调 node/tool |
| 引擎层 | `app/engine/` | infrastructure | 不依赖 orchestrator（可独立测试） |
| MCP Server | `app/mcp_server/` | infrastructure | 不依赖 engine/orchestrator，可独立测试 |
| 基础设施层 | `app/infrastructure/` | 无 | 不依赖任何业务层 |

核心原则：**改动一个 graph 不应触及 LLM 工厂，换一个数据库驱动不应改动任何 node。MCP Server 作为独立层，换 Java API 路径只需改 mcp_server/tools/ 下的封装文件。**

### 4.4 项目结构

```
sphp-agent/                             # Agent 模块根目录
├── app/                                # 应用代码
│   ├── main.py                         # FastAPI + MCP Server 启动 + lifespan
│   ├── api/                            # ① API 接入层
│   │   ├── routes/
│   │   │   ├── chat.py                 # 前端直连 Agent 对话端点（SSE 流式）
│   │   │   └── knowledge.py            # 知识库管理（ingest + search）
│   │   ├── middleware/
│   │   │   ├── jwt_auth.py             # JWT 鉴权：调 Java token/parse 换取 userId
│   │   │   ├── rate_limit.py           # 按用户限流
│   │   │   └── tracing.py              # traceId 注入
│   │   └── schemas/
│   │       └── chat.py                 # 对话请求/响应模型
│   ├── orchestrator/                   # ② 编排层
│   │   ├── graphs/                     # LangGraph 子图（main/triage/consult/pharmacy）
│   │   ├── nodes/                      # 图节点函数（intent/rag/safety/tool_caller/reply）
│   │   └── state.py                    # AgentState 状态定义
│   ├── engine/                         # ③ 引擎层
│   │   ├── llm/                        # LLM 工厂 + prompts 模板
│   │   ├── tools/                      # 工具引擎
│   │   │   ├── schema_registry.py      # Function Calling Schema 注册中心
│   │   │   ├── c_schemas.py            # C 端工具 Schema（启动时加载）
│   │   │   └── b_schemas.py            # B 端工具 Schema（启动时加载）
│   │   ├── rag/                        # 知识库向量检索（pgvector）
│   │   └── memory/                     # 对话记忆（buffer + summary）
│   ├── mcp_server/                     # MCP Server（内嵌，stdio transport）
│   │   ├── server.py                   # MCP Server 启动入口（stdio transport）
│   │   └── tools/                      # MCP 工具封装（一个工具一个文件）
│   │       ├── triage.py               # 导诊工具（create_triage_assessment）
│   │       ├── appointment.py          # 挂号工具（query_departments/doctors/slots + create_appointment 等）
│   │       ├── consultation.py         # 问诊工具（save_pre_consultation/query_consultations 等）
│   │       ├── prescription.py         # 处方工具（query_prescriptions/interpret_prescription）
│   │       ├── pharmacy.py             # 购药工具（query_pharmacy_stock/create_drug_order 等）
│   │       ├── health.py               # 健康管理工具（health_record/allergy/history/report 等）
│   │       ├── notification.py         # 通知工具（query_notifications/mark_notification_read）
│   │       └── b_doctor.py             # B 端医生工具（接诊辅助/处方审核/报告解读）
│   └── infrastructure/                 # ④ 基础设施层
│       ├── audit/logger.py             # structlog 结构化日志
│       ├── cache/redis_client.py       # Redis 限流/confirm_token
│       ├── java_client.py              # Java REST API 客户端（httpx）
│       └── config/settings.py          # Pydantic Settings 配置
├── tests/
│   ├── unit/                           # 节点级单元测试
│   ├── integration/                    # 图级集成测试
│   └── eval/                           # 评测集
├── docs/Agent/                         # Agent 模块文档
│   ├── Agent模块系分.md                 # 本文档
│   ├── C端前后端系分/                   # C 端后端系分（参考）
│   └── B端系分/                         # B 端后端系分（参考）
├── data/                               # 知识库文档源文件
├── pyproject.toml
├── requirements.txt
└── .env                                # 私有环境变量（不纳入版本控制）
```

### 4.5 启动与初始化流程

Agent 通过 FastAPI `lifespan` 机制管理启动和关闭。以下为启动时的完整初始化序列：

```
main.py: lifespan()
    │
    ├── 1. 加载配置
    │       Settings() 读取 .env → 校验必填项（LLM_API_KEY、JAVA_BASE_URL 等）
    │       └── 失败 → fatal，进程退出，打印缺失变量名
    │
    ├── 2. 初始化基础设施连接
    │       ├── PostgreSQL: asyncpg 连接池，执行 `SELECT 1` 健康检查
    │       │   └── 失败 → fatal（RAG 和知识库依赖 PG）
    │       ├── pgvector: 验证扩展已安装（`SELECT extname FROM pg_extension WHERE extname='vector'`）
    │       │   └── 失败 → fatal
    │       └── Redis: 建立连接，执行 `PING`
    │           └── 失败 → warn（L2 操作不可用，其他功能正常）
    │
    ├── 3. 初始化引擎层
    │       ├── LLM 工厂: 根据 LLM_PROVIDER 创建客户端，执行一次空调用验证 API Key 有效
    │       │   └── 失败 → fatal
    │       └── Embedding 工厂: 根据 EMBEDDING_PROVIDER 创建客户端
    │           └── 失败 → fatal
    │
    ├── 4. 注册 Function Calling Schema
    │       ├── 加载 engine/tools/c_schemas.py → 注册 30 个 C 端工具
    │       └── 加载 engine/tools/b_schemas.py → 注册 9 个 B 端工具
    │           └── 注册到全局 ToolRegistry（单例），供 LLM 推理时查询
    │
    ├── 5. 启动 MCP Server
    │       └── stdio transport: 在独立线程中启动，监听 MCP Client 的 tools/list 和 tools/call 请求
    │           └── 失败 → fatal
    │
    ├── 6. 注册 FastAPI 路由
    │       ├── POST /api/chat/stream   (chat.py)
    │       ├── POST /api/chat/confirm  (chat.py)
    │       ├── POST /api/knowledge/ingest (knowledge.py)
    │       ├── GET  /api/knowledge/search (knowledge.py)
    │       └── GET  /health → 健康检查端点
    │
    └── 7. 开始接受请求
            └── uvicorn 日志: "Agent started on :8081"
```

**健康检查端点：**

```
GET /health
```

| 响应字段 | 类型 | 说明 |
|----------|------|------|
| status | string | `healthy` / `degraded` / `unhealthy` |
| checks.pg | string | PostgreSQL 连接状态 |
| checks.redis | string | Redis 连接状态 |
| checks.llm | string | LLM API 连通性 |
| version | string | Agent 版本号 |

```json
{
  "status": "healthy",
  "checks": {
    "pg": "ok",
    "redis": "ok",
    "llm": "ok"
  },
  "version": "2.0.0"
}
```

**优雅关闭：**

Agent 收到 SIGTERM/SIGINT 时执行：
1. 关闭 MCP Server（停止接受新工具调用，等待进行中的调用完成，最长 10s）
2. 关闭 PostgreSQL 连接池
3. 关闭 Redis 连接
4. uvicorn 退出

**启动失败分级：**

| 级别 | 含义 | 行为 |
|------|------|------|
| fatal | 核心组件不可用，无法提供任何服务 | 进程退出，exit code 1，uvicorn 不启动 |
| warn | 非核心组件不可用，部分功能降级 | 进程继续启动，`/health` 返回 `degraded`，日志记录告警 |


---

## 5. 核心机制

### 5.1 对话处理模型

Agent 通过 HTTP/SSE 与前端通信。前端发起对话请求后，Agent 内部按以下流程处理：

```mermaid
graph TD
    START["前端发起对话<br/>content + scope + JWT Token"] --> AUTH["JWT 鉴权节点<br/>调 Java token/parse<br/>换取 userId"]
    AUTH --> INTENT["意图识别节点"]
    INTENT -->|"医疗咨询"| RAG["RAG检索节点"]
    INTENT -->|"业务操作"| TOOL["工具调用决策节点<br/>LLM 选择工具+参数"]
    RAG --> REPLY["回复生成节点"]
    TOOL --> CHECK{"安全校验节点"}
    CHECK -->|"L1/L2 通过"| MCP_CALL["MCP tools/call<br/>MCP Server 执行工具"]
    CHECK -->|"L3/L4"| REJECT["拒绝回复<br/>不下发 tool_call"]
    MCP_CALL -->|"L1"| RESULT["MCP Server 同步返回<br/>Java REST API 结果"]
    MCP_CALL -->|"L2"| L2_FLOW["L2 确认流程<br/>生成 confirm_token<br/>推送确认卡片给前端<br/>等待用户确认后执行"]
    RESULT --> REPLY
    L2_FLOW --> REPLY
    REJECT --> REPLY
    REPLY --> END["SSE: message + done"]
```

七个标准节点：

| 节点 | 职责 | 输入 | 输出 |
|------|------|------|------|
| JWT 鉴权 | 调用 Java token/parse 接口校验 JWT，获取 userId | 前端 JWT Token + scope | userId（scope 由请求体传入） |
| 意图识别 | 判断用户意图类型，决定后续路由 | 用户消息 + 历史对话 | 意图标签 + 置信度 |
| RAG 检索 | 从医疗知识库检索相关内容 | 用户问题 | 相关知识片段列表 |
| 工具调用决策 | LLM 决策选择哪个 Function Calling 工具及参数 | 用户意图 + 已注册工具 Schema | tool_call（工具名 + 参数） |
| 安全校验 | 检查工具安全等级，L3/L4 直接拒绝 | tool_call + 工具注册表 | 放行 / 拒绝 |
| MCP 工具调用 | 通过 MCP `tools/call` 调用 MCP Server 执行工具，同步等待结果 | tool_call + userId | 业务数据结果 |
| 回复生成 | 融合工具返回结果，生成自然语言回复 | 工具结果 + 上下文 | 流式 message + done |

### 5.2 意图路由

| 意图 | 说明 | 处理子图 |
|------|------|----------|
| 导诊 (triage) | 用户描述症状，期望获取就诊建议 | 追问 → 科室推荐 → 医生推荐 |
| 挂号 (registration) | 用户期望完成挂号 | 号源查询 → 创建挂号单 → 支付引导 |
| 问诊 (consultation) | 挂号后的预问诊或处方解读 | 信息采集 → 摘要提交 → 解读 |
| 购药 (pharmacy) | 处方后购药 | 库存查询 → 药店推荐 → 下单 |
| 健康档案 (health) | 管理过敏史/既往史/检查报告/用药计划/随访/通知（M8-2） | 健康档案子图 10 工具白名单 |
| 咨询 (qa) | 医疗知识问答 | RAG 检索 → 回复生成 |
| 闲聊 (chitchat) | 非业务对话 | 直接 LLM 回复 |

**B 端意图定义（M8-3）：**

C 端按上表 7 类意图做 LLM 分类；**B 端（scope=b_end）不做意图分类**——
auth_node 后直接进入 B 端全量工具子图（绑定全部 9 个 B 端 L1/L2 工具）。

| 场景 | 处理方式 |
|------|----------|
| 查患者/查药/处方审核/报告解读等问诊类提问 | 直达 B 端工具子图，LLM 基于 9 工具决策（跳过意图分类，省一次 LLM 调用） |
| 纯会话/非工具需求 | 工具子图内 LLM 不选工具 → 空 tool_calls → 结束子图 → reply_node 直接回复 |

> **设计依据**：C 端 7 类意图（triage/registration/consultation/pharmacy/health/qa/chitchat）
> 均为患者端语义，与 B 端医生流程无关。B 端若走意图分类，医生提问大概率误归
> `qa` 分支（rag_node 只检索知识库不调工具），9 个 B 端工具全不可达。直达同时
> 与 M5 定案一致——B 端全量绑定 9 工具、无白名单约束。

#### 5.2.1 图编排设计

LangGraph 以有向图形式串联 8 个标准节点。主图负责鉴权、按 scope 分流、意图路由
和安全校验，业务操作委托给 6 个子图处理（5 个 C 端业务子图 + 1 个 B 端直达工具子图）。

**主图结构：**

```mermaid
graph TD
    START["START"] --> AUTH["① auth_node<br/>JWT鉴权"]
    AUTH -->|"scope=b_end"| BTOOL["② b_end_tool_graph<br/>B端直达工具子图"]
    AUTH -->|"scope=c_end"| INTENT["② intent_node<br/>意图识别"]
    INTENT -->|"triage"| TRIAGE["③ triage_graph<br/>导诊子图"]
    INTENT -->|"registration"| REG["④ registration_graph<br/>挂号子图"]
    INTENT -->|"consultation"| CONSULT["⑤ consultation_graph<br/>问诊子图"]
    INTENT -->|"pharmacy"| PHARM["⑥ pharmacy_graph<br/>购药子图"]
    INTENT -->|"health"| HEALTH["⑦ health_graph<br/>健康档案子图"]
    INTENT -->|"qa"| QA["⑧ qa_node<br/>RAG检索→LLM回复"]
    INTENT -->|"chitchat"| CHAT["⑨ chitchat_node<br/>直接LLM回复"]
    BTOOL --> REPLY["⑩ reply_node<br/>回复生成"]
    HEALTH --> REPLY
    TRIAGE --> REPLY
    REG --> REPLY
    CONSULT --> REPLY
    PHARM --> REPLY
    QA --> REPLY
    CHAT --> REPLY
    REPLY --> END["END"]
```

**节点职责：**

| 节点 | 类型 | 职责 |
|------|------|------|
| auth_node | 主图节点 | 从 Header 取 JWT + 从请求体取 scope，调 Java token/parse 换取 userId（B 端同时写入 roles/dept_id/doctor_id/hospital_id），写入 AgentState |
| b_end_tool_graph | 子图 | **B 端直达工具子图（M8-3）**：tool_caller 绑定 B 端全量 L1/L2 工具（9 个，无白名单）→ safety → executor 循环，跳过 C 端意图分类 |
| intent_node | 主图节点 | 基于用户消息 + 历史对话，LLM 判断意图类型。输出意图标签用于路由（仅 C 端执行） |
| triage_graph | 子图 | 追问症状 → RAG 检索 → 推荐科室 → 调 `query_doctors` 查医生 |
| registration_graph | 子图 | 调 `query_departments` → `query_schedule_slots` → `create_appointment`（含 L2 确认） |
| consultation_graph | 子图 | 调 `query_consultations` / `query_prescriptions` → `interpret_prescription` |
| pharmacy_graph | 子图 | 调 `query_pharmacy_stock` → `create_drug_order`（含 L2 确认） |
| health_graph | 子图 | **健康档案子图（M8-2，场景五）**：10 工具白名单——L1 查询（档案/报告/用药计划/随访/通知）+ L2 变更（过敏史/既往史/报告录入/用药计划更新/随访确认，含 L2 确认） |
| qa_node | 主图节点 | 调 RAG 检索 → 注入 LLM 上下文 → 生成回复 |
| chitchat_node | 主图节点 | 不做工具调用，直接 LLM 自由回复 |
| reply_node | 主图节点 | 生成最终回复（LLM 生成自然语言）。SSE 流式推送由路由级 handler 统一处理（见 §5.12） |

**子图内部模式：**

所有子图遵循统一的内部模式——查询 → 安全校验 → 执行 → 回复：

```
查询节点             安全校验节点           执行节点
(tool_caller)  →  (safety_check)  →  (tool_executor)
    │                  │                    │
    │ L1查询           │ L2操作             │
    │ 直接到执行        │ 进L2确认流程        │
    │                  │ (见 §5.5)          │
    ▼                  ▼                    ▼
 ┌─────────┐     ┌──────────┐        ┌──────────┐
 │ MCP调用  │     │ 发confirm │        │ 等确认后  │
 │ 直接返回 │     │ _token卡片│        │ MCP调用   │
 └─────────┘     └──────────┘        └──────────┘
```

**状态流转：**

AgentState 是图中唯一的共享状态对象，通过 LangGraph 的 `add_messages` reducer 自动累积对话历史。流程中：

- `messages` 全程追加，不做删除
- `intent` 由 intent_node 写入，后续节点只读
- `user_id` + `scope` 由 auth_node 写入，MCP 调用时取用；B 端场景同时写入 `roles` / `dept_id` / `doctor_id` / `hospital_id`
- `risk_flags` 由 safety_check 节点追加，reply_node 生成回复时注入警告

#### 5.2.2 意图识别实现

意图识别由 `intent_node` 完成，使用 LLM 分类（非规则匹配），通过构造分类 prompt 让 LLM 从预定义标签中选择。

**分类 prompt 模板：**

```
你是一个医疗对话意图分类器。根据用户的最新消息和对话历史，判断用户意图属于以下哪一类：

- triage: 用户描述症状，希望获得就诊建议或科室推荐
- registration: 用户希望挂号、预约、查看号源或候补
- consultation: 用户进行问诊相关操作（提交预问诊、查看处方、解读处方）
- pharmacy: 用户希望购药、查询药品库存或下单
- health: 用户管理健康档案（过敏史/既往史查询与更新、检查报告查询/录入、用药计划查询/更新、随访查询/确认、通知管理）
- qa: 用户询问医疗知识、健康科普问题
- chitchat: 问候、闲聊、感谢等非业务对话

对话历史：
{history}

用户最新消息：{user_message}

仅输出意图标签（一个单词），不要输出其他内容。
```

**分类规则补充：**

当 LLM 分类置信度低或返回非法标签时，回退规则：

| 条件 | 回退意图 |
|------|----------|
| LLM 返回非预定义标签 | 默认归类为 `qa`（RAG 兜底回答） |
| 用户消息包含"挂号/预约/号源/排班"等关键词但不属于其他意图 | 强制 `registration` |
| 用户消息包含"买药/购药/下单/配送"等关键词但不属于其他意图 | 强制 `pharmacy` |
| 用户消息包含"过敏史/检查报告/用药计划/随访/通知"等关键词但不属于其他意图 | 强制 `health`（M8-2 新增） |

> 关键词规则作为快速通道：消息 ≤10 字且命中关键词时跳过 LLM 调用，直接路由，减少首字延迟。

#### 5.2.3 LangGraph 图构建代码

以下为 LangGraph `StateGraph` 的核心构造代码（伪代码，展示节点注册、条件边定义和子图组合方式）：

```python
from langgraph.graph import StateGraph, END
from langgraph.checkpoint.memory import MemorySaver
from app.orchestrator.state import AgentState

# === 主图构造 ===
builder = StateGraph(AgentState)

# 注册节点
builder.add_node("auth_node", auth_node)
builder.add_node("intent_node", intent_node)
builder.add_node("qa_node", qa_node)
builder.add_node("chitchat_node", chitchat_node)
builder.add_node("reply_node", reply_node)

# 注册子图（编译后的 CompiledGraph）
builder.add_node("triage_graph", triage_graph.compile())
builder.add_node("registration_graph", registration_graph.compile())
builder.add_node("consultation_graph", consultation_graph.compile())
builder.add_node("pharmacy_graph", pharmacy_graph.compile())
builder.add_node("health_graph", health_graph.compile())  # M8-2 健康档案子图（场景五）

# 入口
builder.set_entry_point("auth_node")

# 条件边：意图路由
builder.add_conditional_edges(
    "intent_node",
    route_by_intent,  # 函数：读 state.intent → 返回目标节点名
    {
        "triage": "triage_graph",
        "registration": "registration_graph",
        "consultation": "consultation_graph",
        "pharmacy": "pharmacy_graph",
        "health": "health_graph",
        "qa": "qa_node",
        "chitchat": "chitchat_node",
    }
)

# 所有业务节点汇聚到 reply_node
builder.add_edge("triage_graph", "reply_node")
builder.add_edge("registration_graph", "reply_node")
builder.add_edge("consultation_graph", "reply_node")
builder.add_edge("pharmacy_graph", "reply_node")
builder.add_edge("health_graph", "reply_node")
builder.add_edge("qa_node", "reply_node")
builder.add_edge("chitchat_node", "reply_node")

builder.add_edge("reply_node", END)

# 编译（开发环境用 MemorySaver，生产换 PostgresSaver）
graph = builder.compile(checkpointer=MemorySaver())
```

**节点函数签名（统一规范）：**

所有节点函数接收 `state: AgentState`，返回 `dict`（部分状态更新，LangGraph 自动合并）：

```python
def auth_node(state: AgentState) -> dict:
    """从 JWT 换取 userId，写入 state。scope 由请求体传入。失败抛 AgentAuthError。"""
    ...

def intent_node(state: AgentState) -> dict:
    """分类用户意图，返回 {"intent": "triage"}。"""
    ...

def tool_caller(state: AgentState) -> dict:
    """LLM 决定调用工具，返回 {"tool_calls": [...]}。
       安全校验在此完成：L3/L4 工具调用被拦截。"""
    ...

def safety_check(state: AgentState) -> dict:
    """检查 tool_calls 中的工具等级，L1 直接放行，L2 生成 confirm_token。"""
    ...

def tool_executor(state: AgentState) -> dict:
    """执行已通过安全校验的工具调用（MCP tools/call 或本地工具）。"""
    ...
```

**子图内部结构（以 registration_graph 为例）：**

```python
reg_builder = StateGraph(AgentState)
reg_builder.add_node("tool_caller", tool_caller)
reg_builder.add_node("safety_check", safety_check)
reg_builder.add_node("tool_executor", tool_executor)

reg_builder.set_entry_point("tool_caller")
reg_builder.add_edge("tool_caller", "safety_check")
reg_builder.add_conditional_edges(
    "safety_check",
    route_safety,  # L1 → tool_executor, L2 → 等待确认（挂起）
    {"execute": "tool_executor", "pending_confirm": END}
)
reg_builder.add_edge("tool_executor", END)
```

### 5.3 MCP 工具调用机制

Agent 通过内嵌的 MCP Server 直接封装 Java REST API。Agent 编排层作为 MCP Client，在启动时通过 `tools/list` 获取全部已注册工具 Schema，运行时通过 `tools/call` 请求 MCP Server 执行业务操作。

**工具注册模型（Function Calling Schema）：**

Agent 端通过 Function Calling Schema 向 LLM 注册工具。每个工具的 Schema 定义与 MCP Server 中对应的工具封装文件一一对应：

| 属性 | 说明 |
|------|------|
| `name` | 工具唯一标识，与 MCP Server 注册的工具名一致 |
| `description` | 自然语言描述，供 LLM 理解工具用途 |
| `parameters` | 参数 JSON Schema（OpenAI Function Calling 格式） |
| `scope` | 服务对象：`c_end` / `b_end` |
| `security_level` | 安全等级：L1 / L2 / L3 / L4（L3/L4 不注册） |

**C 端工具 Schema（按业务域分组，共 30 个）：**

> 以下参数已与 C 端后端系分 V1.3（55 个 API）对齐。所有 API 路径均以 `/api/c/v1` 为前缀，下表省略前缀。

导诊：

| 工具 | 等级 | 参数 | 说明 | 对应 Java API |
|------|------|------|------|--------------|
| `create_triage_assessment` | L1 | `patient_id` (int, 选填), `hospital_id` (int, 必填), `symptom` (string, 必填), `duration` (string, 选填), `temperature` (float, 选填), `medical_history` (string, 选填) | 提交症状进行导诊评估，返回紧急程度与推荐科室。`patient_id` 不传时后端默认取本人 | `POST /triage/assessments` |

挂号管理：

| 工具 | 等级 | 参数 | 说明 | 对应 Java API |
|------|------|------|------|--------------|
| `query_departments` | L1 | `hospital_id` (int, 必填), `keyword` (string, 选填) | 查询科室列表 | `GET /departments` |
| `query_doctors` | L1 | `hospital_id` (int, 必填), `department_id` (int, 必填), `date` (string, 选填) | 按科室查询医生及号源概览 | `GET /doctors` |
| `query_schedule_slots` | L1 | `doctor_id` (int, 必填), `hospital_id` (int, 必填), `date` (string, 必填) | 查询医生可预约时段与余量。返回值含 `schedule_status`，仅 `schedule_status=PUBLISHED` 且 `available_count>0` 的时段可约；`CANCELLED`/`DRAFT` 时段不可向用户推荐 | `GET /doctors/{doctorId}/slots` |
| `create_appointment` | L2 | `hospital_id` (int, 必填), `slot_id` (int, 必填), `patient_id` (int, 选填) | 创建挂号锁定订单 | `POST /appointments` |
| `query_appointments` | L1 | `appointment_id` (int, 选填), `status` (string, 选填), `patient_id` (int, 选填) | 不带 id 返回列表（可按 status 筛选，patient_id 不传默认本人），带 id 返回详情 | `GET /appointments` + `GET /appointments/{appointmentId}` |
| `cancel_appointment` | L2 | `appointment_id` (int, 必填) | 取消挂号锁定订单 | `POST /appointments/{appointmentId}/cancel` |
| `join_waitlist` | L2 | `slot_id` (int, 必填), `patient_id` (int, 选填) | 号源约满时登记候补 | `POST /waitlists` |
| `query_payment_status` | L1 | `payment_id` (int, 必填) | 查询支付单状态 | `GET /payments/{paymentId}` |

问诊管理：

| 工具 | 等级 | 参数 | 说明 | 对应 Java API |
|------|------|------|------|--------------|
| `save_pre_consultation` | L2 | `appointment_id` (int, 必填), `chief_complaint` (string, 必填), `history_of_present_illness` (string, 选填), `attachments` (array, 选填), `patient_id` (int, 选填) | 创建或保存预问诊（后端接口为创建/保存合一，无独立提交标识；问诊状态由后端按挂号支付状态流转） | `POST /consultations/pre-consultations` |
| `query_consultations` | L1 | `consultation_id` (int, 选填), `status` (string, 选填), `patient_id` (int, 选填) | 不带 id 返回列表，带 id 返回详情及消息记录 | `GET /consultations` + `GET /consultations/{consultationId}` |
| `send_consultation_message` | L2 | `consultation_id` (int, 必填), `content` (string, 必填) | 发送问诊文字消息 | `POST /consultations/{consultationId}/messages` |

处方管理：

| 工具 | 等级 | 参数 | 说明 | 对应 Java API |
|------|------|------|------|--------------|
| `query_prescriptions` | L1 | `prescription_id` (int, 选填), `patient_id` (int, 选填) | 不带 id 返回当前用户处方列表，带 id 返回详情及药品明细 | `GET /prescriptions` + `GET /prescriptions/{prescriptionId}` |
| `interpret_prescription` | L1 | `prescription_id` (int, 必填) | 处方解读 | `GET /prescriptions/{prescriptionId}/interpretation` |

购药管理：

| 工具 | 等级 | 参数 | 说明 | 对应 Java API |
|------|------|------|------|--------------|
| `query_pharmacy_stock` | L1 | `prescription_id` (int, 必填), `patient_id` (int, 选填) | 按处方查询药店库存与价格 | `GET /pharmacies/inventory` |
| `create_drug_order` | L2 | `prescription_id` (int, 必填), `pharmacy_id` (int, 必填), `delivery_address` (string, 必填), `patient_id` (int, 选填) | 创建购药订单 | `POST /drug-orders` |
| `query_drug_orders` | L1 | `drug_order_id` (int, 选填), `status` (string, 选填), `logistics_status` (string, 选填), `patient_id` (int, 选填) | 不带 id 返回列表（可按 status 筛选），带 id 返回详情 | `GET /drug-orders` + `GET /drug-orders/{drugOrderId}` |
| `cancel_drug_order` | L2 | `drug_order_id` (int, 必填) | 取消未支付购药订单 | `POST /drug-orders/{drugOrderId}/cancel` |
| `confirm_drug_receipt` | L2 | `drug_order_id` (int, 必填) | 确认购药收货 | `POST /drug-orders/{drugOrderId}/confirm-receipt` |

健康管理：

| 工具 | 等级 | 参数 | 说明 | 对应 Java API |
|------|------|------|------|--------------|
| `query_health_record` | L1 | `patient_id` (int, 选填) | 查询健康档案（含过敏史、既往史） | `GET /health-record` |
| `manage_allergy` | L2 | `allergy_id` (int, 选填), `allergen` (string, 必填), `reaction` (string, 选填) | 管理过敏史（不带 allergy_id 新增，带 allergy_id 修改） | `POST /health-record/allergies` / `PUT /health-record/allergies/{allergyId}` |
| `manage_medical_history` | L2 | `history_id` (int, 选填), `content` (string, 必填), `occurred_at` (string, 选填) | 管理既往史（不带 history_id 新增，带 history_id 修改） | `POST /health-record/histories` / `PUT /health-record/histories/{historyId}` |
| `query_reports` | L1 | `report_id` (int, 选填), `patient_id` (int, 选填) | 查询检查报告列表或详情（带 id 时包含指标解读） | `GET /reports` + `GET /reports/{reportId}` + `GET /reports/{reportId}/interpretation` |
| `create_report` | L2 | `report_name` (string, 必填), `report_date` (string, 必填), `indicators` (array, 必填), `patient_id` (int, 选填) | 录入检查报告（indicators 含 name/value/unit/reference_range） | `POST /reports` |
| `query_medication_plans` | L1 | `status` (string, 选填), `patient_id` (int, 选填) | 查询用药计划列表 | `GET /medication-plans` |
| `update_medication_plan` | L2 | `plan_id` (int, 必填), `action` (string, 必填：`PAUSE` / `RESUME` / `COMPLETE`) | 暂停/恢复/完成用药计划 | `PATCH /medication-plans/{planId}` |
| `query_follow_ups` | L1 | `status` (string, 选填), `patient_id` (int, 选填) | 查询随访计划列表 | `GET /follow-ups` |
| `confirm_follow_up` | L2 | `follow_up_id` (int, 必填), `remind_at` (string, 选填) | 确认随访提醒时间 | `POST /follow-ups/{followUpId}/confirm` |
| `manage_notifications` | L1 | `action` (string, 必填：`list` / `read`), `notification_id` (int, action=read 时必填), `patient_id` (int, action=list 时选填) | 管理通知（action=list 查列表，action=read 标已读） | `GET /notifications` / `POST /notifications/{notificationId}/read` |

本地工具：

| 工具 | 等级 | 说明 |
|------|------|------|
| `search_medical_knowledge` | L1 | 医疗科普知识库检索（pgvector 本地执行，不经过 MCP Server） |

> **注**：L3（支付扣款）和 L4（开具处方、下诊断结论、删除病历）类工具不注册，Agent 无入口调用。如果 LLM 尝试生成此类 tool_call，代码层硬拦截，不下发 MCP `tools/call`。

**B 端工具 Schema（按场景分组，共 9 个）：**

> 以下 API 路径已与 B 端后端系分 V1.1 对齐。所有 API 路径均以 `/api/b` 为前缀，下表省略前缀（标注"本地"的工具除外）。B 端 patient/drug/department/doctor/schedule 类 API 统一在 `/api/b/admin/*` 路径下，doctor 专属操作（如病历保存）在 `/api/b/doctor/*` 路径下。

接诊辅助：

| 工具 | 等级 | 参数 | 说明 | 对应 Java API |
|------|------|------|------|--------------|
| `query_patient_history` | L2 | `patient_id` (int, 必填) | 聚合查询患者基本信息、过敏史、既往史、就诊记录、历史处方、当前用药 | `GET /admin/patients/{id}` + `/admin/patients/{id}/visits` + `/admin/patients/{id}/prescriptions` + `/admin/patients/{id}/medications` |
| `query_drug_guide` | L1 | `name` (string, 选填) | 查询药品说明书（适应症、禁忌、不良反应等），按药品名称模糊匹配 | `GET /admin/drugs` |
| `check_drug_interaction` | L1 | `drug_names` (array[string], 必填), `patient_id` (int, 必填) | 聚合返回药品说明书 + 患者当前用药清单，由编排层 LLM 判定相互作用 | `GET /admin/drugs` + `GET /admin/patients/{id}/medications` |
| `generate_draft_note` | L2 | `consultation_id` (int, 必填), `note_content` (string, 必填) | 保存医生病历记录（编排层 LLM 生成草稿文本，工具负责持久化） | `PUT /doctor/consult/{id}/note` |

导诊推荐：

| 工具 | 等级 | 参数 | 说明 | 对应 Java API |
|------|------|------|------|--------------|
| `recommend_care` | L1 | `department_id` (int, 选填) | 聚合返回科室列表、医生列表及排班号源，由编排层 LLM 根据患者症状匹配推荐科室与医生 | `GET /admin/departments` + `GET /admin/doctors` + `GET /admin/schedules` |

处方审核：

| 工具 | 等级 | 参数 | 说明 | 对应 Java API |
|------|------|------|------|--------------|
| `check_contraindication` | L1 | `drug_name` (string, 必填), `patient_id` (int, 必填) | 聚合返回药品禁忌信息 + 患者过敏史/既往史，由编排层 LLM 判定禁忌风险 | `GET /admin/drugs` + `GET /admin/patients/{id}` |
| `check_allergy_risk` | L1 | `drug_name` (string, 必填), `patient_id` (int, 必填) | 返回患者过敏史记录，由编排层 LLM 判定与处方的过敏风险 | `GET /admin/patients/{id}` |
| `check_duplicate_medication` | L1 | `drug_name` (string, 必填), `patient_id` (int, 必填) | 返回患者当前用药清单，由编排层 LLM 判定是否重复用药 | `GET /admin/patients/{id}/medications` |

报告解读（本地工具，不经过 MCP Server）：

| 工具 | 等级 | 参数 | 说明 | 执行方式 |
|------|------|------|------|----------|
| `interpret_report` | L1 | `report_content` (string, 必填), `report_type` (string, 选填) | 本地知识库 pgvector 检索参考范围与医学解读，由编排层 LLM 生成解读结论 | pgvector 本地执行 |

> **注**：B 端工具中的多 API 聚合工具（如 `check_drug_interaction`、`recommend_care` 等）不直接对应单一 Java API，而是由 MCP Server 调用多项 API 聚合数据后返回结构化结果。分析与推理统一在 LangGraph 编排层由 LLM 完成，MCP 工具本身不做 LLM 推理。这与 C 端工具行为一致：所有 MCP 工具只做数据搬运，推理层统一收敛到编排节点。`interpret_report` 为纯本地工具（pgvector），不走 MCP Server，与 C 端 `search_medical_knowledge` 同类。

#### 5.3.1 工具分发逻辑

LLM 输出的 `tool_calls` 进入 `tool_executor` 节点后，需要区分两类工具并走不同执行路径：

| 工具类型 | 特征 | 执行路径 | 示例 |
|----------|------|----------|------|
| MCP 工具 | 调用 Java REST API，需要 `X-User-Id` 做数据隔离 | MCP Client → `tools/call` → MCP Server → httpx → Java | `query_departments`、`create_appointment`、`query_patient_history` |
| 本地工具 | 不经过网络，直接调用本地资源（pgvector、LLM） | 直接执行，跳过 MCP 层 | `search_medical_knowledge`、`interpret_report` |

分发逻辑在 `ToolRegistry` 中实现，注册时标记 `executor` 属性：

```python
# engine/tools/registry.py
class ToolRegistry:
    _tools: dict[str, ToolSchema] = {}

    @classmethod
    def register(cls, schema: ToolSchema):
        cls._tools[schema.name] = schema

    @classmethod
    def get_executor(cls, tool_name: str) -> str:
        """返回 'mcp' 或 'local'"""
        return cls._tools[tool_name].executor

    @classmethod
    def is_local(cls, tool_name: str) -> bool:
        return cls.get_executor(tool_name) == "local"
```

`tool_executor` 节点的分发伪代码：

```python
async def tool_executor(state: AgentState) -> dict:
    results = []
    for tc in state.tool_calls:
        if ToolRegistry.is_local(tc.name):
            # 本地工具：直接执行
            result = await execute_local_tool(tc.name, tc.arguments)
        else:
            # MCP 工具：通过 MCP Client 调用
            result = await mcp_client.call_tool(tc.name, tc.arguments)
        results.append(result)
    return {"tool_results": results}
```

**MCP Server 工具实现模式：**

分发逻辑只负责路由到 MCP 或本地。MCP Server 内部的工具函数有两种常见实现模式：

**模式一：列表/详情二合一（单工具按可选 ID 分支调不同 API）**

`query_appointments`、`query_consultations`、`query_prescriptions`、`query_drug_orders`、`query_reports` 采用此模式。工具函数内根据可选 ID 参数是否存在，分支调用列表 API 或详情 API：

```python
# mcp_server/tools/query_appointments.py
async def query_appointments(args: dict, user_id: int) -> dict:
    appointment_id = args.get("appointment_id")
    if appointment_id is not None:
        # 详情：GET /appointments/{appointmentId}
        resp = await java_client.get(f"/appointments/{appointment_id}")
    else:
        # 列表：GET /appointments?status=xxx
        params = {k: v for k, v in args.items() if v is not None}
        resp = await java_client.get("/appointments", params=params)
    return resp.json()["data"]
```

**模式二：多 API 聚合（单工具并发调多个 API 合并返回）**

`check_drug_interaction`、`recommend_care`、`query_patient_history` 等聚合工具采用此模式。工具函数并发调用多个 Java API，合并结果后返回，推理交给编排层 LLM：

```python
# mcp_server/tools/check_drug_interaction.py
async def check_drug_interaction(args: dict, user_id: int) -> dict:
    drug_names = args["drug_names"]
    patient_id = args["patient_id"]
    # 并发查询药品说明书 + 患者用药清单
    drugs_resp, meds_resp = await asyncio.gather(
        java_client.get("/drugs", params={"names": drug_names}),
        java_client.get(f"/patients/{patient_id}/medications"),
    )
    return {
        "drug_info": drugs_resp.json()["data"],
        "current_medications": meds_resp.json()["data"],
    }
```

#### 5.3.2 MCP 错误传播格式

当 MCP Server 调用 Java REST API 失败时，错误信息需要经过三层传递，每层有明确的格式约定。

**第一层：MCP Server → MCP Client（JSON-RPC 响应）**

MCP Server 区分业务错误和系统错误，统一包装为结构化 JSON：

```json
{
  "success": false,
  "error": {
    "code": "SLOT_UNAVAILABLE",
    "message": "该时段已被约满",
    "detail": "医生 ID 42 在 2026-08-01 上午时段号源已耗尽，最早可约 2026-08-02 下午",
    "http_status": 409
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | bool | 始终为 `false` |
| `error.code` | string | 业务错误码（来自 Java 响应体）或 `JAVA_TIMEOUT` / `JAVA_5XX` |
| `error.message` | string | 面向用户的简短描述 |
| `error.detail` | string | 详细信息，供 LLM 生成建议时参考 |
| `error.http_status` | int | Java 返回的 HTTP 状态码 |

**第二层：MCP Client → LangGraph State**

MCP Client 收到错误响应后，将结构化错误写入 `AgentState.tool_results`，不做任何转换或脱敏：

```python
# state 中追加
{
    "tool_name": "create_appointment",
    "success": False,
    "error": {
        "code": "SLOT_UNAVAILABLE",
        "message": "该时段已被约满",
        "detail": "医生 ID 42...",
        "http_status": 409
    }
}
```

**第三层：LLM → 用户（自然语言）**

`tool_executor` 执行完毕后，包含错误信息的 `tool_results` 作为 Function Calling 结果注入 `messages`，由 LLM 在下一轮推理中解读并生成自然语言建议。例如上面的错误会被 LLM 转为：

> "抱歉，该时段已经被约满了。医生最早在 8 月 2 日下午还有一个空位，要帮您预约那个时段吗？"

LLM 不需要格式化指令——结构化错误信息（`detail` 字段）已经包含了足够上下文，LLM 天然具备将结构化数据转为自然语言的能力。

**Java 侧约定（待 Java 后端对齐）：**

Java REST API 在非 2xx 响应时，响应体需包含 `code` 和 `message` 字段，格式：

```json
{
  "code": "SLOT_UNAVAILABLE",
  "message": "该时段已被约满",
  "detail": "最早可约 2026-08-02 下午"
}
```

MCP Server 中的 `java_client.py` 在收到非 2xx 时，解析此 JSON 并包装为上述错误格式。如果 Java 5xx 无响应体，则 `code` 使用 `JAVA_5XX`，`message` 使用 "服务异常，请稍后重试"。

### 5.4 L1-L4 安全验证体系

| 等级 | 定义 | Agent 行为 | Java 后端行为 |
|------|------|------------|---------------|
| L1 查询级 | 只读，不产生业务变更 | 直接通过 MCP 执行工具 | 校验 X-User-Id → DataScope 过滤 → 返回数据 |
| L2 业务级 | 创建/修改业务数据 | 生成 confirm_token → SSE 推送确认卡片 → 等待用户确认 → MCP 执行 | 校验 X-User-Id → 执行业务逻辑 → 返回结果 |
| L3 资金级 | 涉及资金支付 | **Agent 代码硬拦截，不注册工具** | 不暴露工具入口 |
| L4 禁止级 | 涉及医疗安全/核心数据变更 | **Agent 代码硬拦截，不注册工具** | 不暴露工具入口 |

### 5.5 L2 确认流程（Agent 管理）

L2 确认由 Agent 管理，通过 Redis confirm_token + SSE 确认卡片实现。流程如下：

```mermaid
sequenceDiagram
    participant U as 用户
    participant F as 前端 :8001
    participant A as Agent :8081
    participant M as MCP Server
    participant J as Java :8080
    participant R as Redis

    Note over A: LLM 决定调用 L2 工具（如 create_appointment）

    A->>A: 识别工具等级为 L2
    A->>R: SET confirm_token (key=token_id, TTL=5min, 绑定 session_id + userId)
    A-->>F: SSE: card (card_type + confirm_token + session_id)

    F->>U: 展示确认卡片
    U->>F: 点击"确认"

    F->>A: POST /api/chat/confirm (confirm_token + session_id)
    A->>R: GET + DEL confirm_token (Lua 一次性消费，校验 session_id + userId)
    A->>A: 校验 token 有效

    A->>M: MCP tools/call (tool_name + arguments)
    M->>J: HTTP REST (Header: X-User-Id)
    J->>J: 执行业务逻辑 → 返回结果
    J-->>M: 业务数据
    M-->>A: MCP 工具结果

    A-->>F: 同步返回 {code, data:{action_result, message}, traceId}
    F->>U: 更新卡片为已完成，展示结果
```

关键设计：
- Agent 识别 L2 工具后，不立即调用 MCP——先生成 confirm_token 存入 Redis（5min TTL，绑定 session_id + userId），通过 SSE 推确认卡片（含 `confirm_token` + `session_id`）给前端
- 前端确认后回调 `/api/chat/confirm`（携带 `confirm_token` + `session_id`），Agent 校验 token（Lua 脚本一次性 get-and-delete + session_id/userId 绑定校验），通过后同步执行 MCP 工具
- **confirm 端点同步返回业务执行结果**（`data.action_result` + `data.message`），前端无需依赖原 SSE 流续推（与前端系分 V1.1 §9.5 一致）
- confirm_token 安全属性：一次性消费、5 分钟 TTL、绑定 userId + session_id
- 超时未确认：confirm_token 过期，Agent 返回 `CONFIRM_EXPIRED`，终止工具执行流程

**confirm_token 结构与生命周期：**

**生成算法：** 使用 Python `uuid4()` 生成 36 位随机字符串，不依赖 HMAC 或加密算法——token 的随机性来自 UUID4（122 位熵），安全性来自 Redis 一次性消费机制而非 token 本身的可猜测性。

**Redis 存储格式：**

| 属性 | 值 |
|------|-----|
| Key 模式 | `confirm:{session_id}:{tool_name}:{token_id}` |
| Value 类型 | JSON string |
| TTL | 300s（`CONFIRM_TOKEN_TTL` 环境变量配置） |
| 消费方式 | `GET` + `DEL`（原子操作，Lua 脚本保证一次性） |

**Value JSON 结构：**

```json
{
  "token_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "session_id": "sess_abc123",
  "user_id": "42",
  "tool_name": "create_appointment",
  "tool_arguments": {
    "hospital_id": 1,
    "slot_id": 301,
    "patient_id": 10001
  },
  "created_at": "2026-07-30T14:30:00Z",
  "card_type": "confirm_appointment"
}
```

| 字段 | 说明 |
|------|------|
| `token_id` | UUID4 随机字符串，前端持有的凭证 |
| `session_id` | 所属会话，用于校验——防止跨会话 token 盗用 |
| `user_id` | 操作发起人，校验时与当前 JWT 中的 userId 比对，不匹配则拒绝 |
| `tool_name` | 待执行的工具名，校验通过后直接作为 MCP `tools/call` 的参数 |
| `tool_arguments` | 工具参数（JSON object），确认前由 Agent 暂存，不传前端 |
| `created_at` | ISO-8601 时间戳，用于审计，不参与校验（TTL 已处理过期） |
| `card_type` | 确认卡片类型，与 §6.2.2 中的 `card_type` 枚举对应 |

**校验流程（Lua 脚本保证原子性）：**

```lua
-- Redis Lua: get_and_delete_confirm_token
local key = KEYS[1]
local expected_user_id = ARGV[1]
local value = redis.call('GET', key)
if value == false then
    return nil  -- token 不存在或已过期
end
local data = cjson.decode(value)
if data.user_id ~= expected_user_id then
    return nil  -- userId 不匹配
end
redis.call('DEL', key)
return value  -- 返回完整 JSON，由 Agent 解析后执行工具
```

> `GET` + `DEL` 在单条 Lua 脚本中完成，Redis 单线程模型天然保证原子性，无需分布式锁。

### 5.6 患者全流程时序（C 端核心链路）

```mermaid
sequenceDiagram
    participant U as 患者
    participant F as 前端 :8001
    participant A as Agent :8081
    participant M as MCP Server
    participant J as Java :8080
    participant P as PostgreSQL
    participant R as Redis

    U->>F: "我头疼发烧三天了"
    F->>A: POST /api/chat/stream<br/>Bearer Token + content + scope
    A->>J: GET /api/c/v1/auth/token/parse (JWT)
    J-->>A: userId + account + tokenExpiresAt
    A->>A: JWT 鉴权通过

    Note over A: 意图识别 → 导诊子图

    A-->>F: SSE: message "请问您发烧多少度？"
    U->>F: "38.5度"
    F->>A: POST /api/chat/stream

    Note over A: LLM 决定调用 query_departments
    A->>M: MCP tools/call (query_departments)
    M->>J: GET /departments (Header: X-User-Id)
    J->>P: 查询启用科室
    P-->>J: 科室列表
    J-->>M: 科室数据
    M-->>A: MCP 结果

    Note over A: LLM 决定调用 query_doctors
    A->>M: MCP tools/call (query_doctors)
    M->>J: GET /doctors?departmentId=X (Header: X-User-Id)
    J-->>M: 医生+号源
    M-->>A: MCP 结果

    A-->>F: SSE: message "建议呼吸内科，以下3位医生有号..."
    U->>F: 选择王医生

    Note over A: LLM 决定 create_appointment (L2)
    A->>A: 识别 L2 → 生成 confirm_token
    A->>R: SET confirm_token (5min TTL)
    A-->>F: SSE: 确认卡片

    U->>F: 点击确认
    F->>A: POST /api/chat/confirm (confirm_token)
    A->>R: GET+DEL confirm_token ✓

    A->>M: MCP tools/call (create_appointment)
    M->>J: POST /appointments (Header: X-User-Id)
    J->>P: Redis Lua 原子预扣
    P-->>J: appointmentId + paymentId
    J-->>M: 挂号成功
    M-->>A: MCP 结果

    A-->>F: SSE: message "挂号成功！请在15分钟内支付"
    F->>U: 展示结果/支付入口
```

### 5.7 知识库 RAG

**处理流水线：**

```
文档源（txt / md / pdf / csv）
    → 文档加载 → 递归切分 (chunk_size=500, overlap=50)
    → Embedding 模型（智谱 embedding-3，可扩展多供应商）
    → 向量存入 PostgreSQL (pgvector 扩展, IVFFlat/HNSW 索引)
    → 用户问题 → Embedding → 余弦相似度检索 (top_k=5)
    → 相关知识片段 + 来源 → 注入 LLM 上下文
```

**Embedding 选型：**

| 项目 | 选型 | 理由 |
|------|------|------|
| 默认模型 | 智谱 embedding-3 | 中文医疗文本效果较好，API 稳定 |
| 向量维度 | 1024 | 与 embedding-3 输出维度一致 |
| 多供应商 | 工厂模式抽象 | 通过 `EmbedderFactory` 接口切换（智谱 / 通义 / OpenAI），`.env` 中配置 `EMBEDDING_PROVIDER` |

**分块策略：**

| 参数 | 值 | 理由 |
|------|------|------|
| chunk_size | 500 字符 | 医疗科普内容篇幅适中，500 字符约一小段说明文字 |
| overlap | 50 字符 | 10% 重叠，避免关键信息被切在边界处 |
| 切分方式 | RecursiveCharacterTextSplitter | 按 `\n\n` → `\n` → `。` → 字符的优先级递归切分，保留自然段落结构 |

**索引方案：**

| 项目 | 选型 | 理由 |
|------|------|------|
| 索引类型 | HNSW | 检索速度优于 IVFFlat，适合知识库文档不频繁重建的场景 |
| 相似度度量 | 余弦相似度 (`<=>`) | pgvector 0.7+ 原生支持，与 embedding 模型训练时的度量一致 |
| 索引构建 | 入库后手动 `CREATE INDEX` | 避免每次插入时重建索引的性能开销 |

**检索流程：**

用户问题嵌入为向量后，pgvector 执行近似最近邻（ANN）检索：

1. Embedding 用户问题 → 1024 维向量
2. pgvector 余弦相似度检索，取 `top_k=5`
3. 过滤相似度 `< 0.6` 的低质量结果（低于阈值说明知识库没有相关内容，LLM 回复中如实告知）
4. 按相似度降序排列，附带 `source_doc` 和 `source_page` 引用
5. 注入 LLM 上下文——格式为 `【参考知识】\n---\n1. [来源: {doc}, {page}] {content}\n---`

**检索入口：**

C 端通过 `search_medical_knowledge` 工具面向患者提供科普内容检索。B 端部分工具（如 `interpret_report`）内部使用 pgvector 进行本地知识库查询。知识库检索在 Agent 本地执行，不经过 MCP Server 或 Java 后端。

### 5.8 审计追踪

审计分布在两层：
- **Java 后端层**：Java 后端通过 `@AuditLog` 注解记录每次 API 调用的完整信息（用户 ID、参数摘要、结果码、耗时），写入 `agent_tool_audit` 表
- **Agent 层**：记录自身的推理级审计日志（structlog JSON 格式），包括节点路由、LLM 调用、MCP `tools/call` 请求、本地 RAG 检索

Agent 层审计字段：

| 审计字段 | 说明 |
|----------|------|
| session_id | 对话会话唯一标识 |
| user_id | 从 JWT 鉴权获得的用户 ID |
| scope | 服务端：`c_end` / `b_end` |
| tool | 请求的工具名称 |
| tool_call_id | 工具调用唯一 ID |
| result | 执行结果（success / failed / rejected） |
| duration_ms | MCP 工具调用耗时（毫秒） |
| security_level | 工具安全等级（L1/L2/L3/L4） |
| trace_id | 全链路追踪 ID |
| timestamp | 调用时间戳 |


### 5.9 对话记忆

对话记忆用于在多轮对话中保持上下文连续性，避免用户每次都要重复描述症状或意图。

**存储策略：**

| 项目 | MVP 方案 | 后续方案 |
|------|----------|----------|
| 存储介质 | 内存（Python dict，key=session_id） | Redis（`session:{id}:memory`，TTL=30min） |
| 数据结构 | LangChain `ConversationBufferWindowMemory` | 同上，Redis 持久化 |
| 窗口大小 | 最近 10 轮对话（约 20 条消息） | 可配置，通过 `.env` 的 `MEMORY_WINDOW_SIZE` |

> MVP 阶段使用内存存储，会话量 < 200 并发、单进程部署时足够。后续切换到 Redis 后支持多进程共享和会话持久化。

**摘要压缩：**

当对话轮次超过窗口大小时，不直接丢弃旧消息，而是触发摘要压缩：

1. 触发条件：`len(messages) > window_size * 2`（即超过 20 条消息时）
2. 压缩策略：取最早的 5 轮对话，调用 LLM 生成 1-2 句摘要
3. 摘要格式：`[对话摘要] 用户描述了{症状}，已推荐{科室}，用户选择了{医生}...`
4. 替换方式：删除原始 5 轮消息，在消息列表头部插入摘要作为系统消息
5. 后续触发：每次新增 5 轮后再次压缩，新摘要合并旧摘要

**与 AgentState 的关系：**

记忆层负责管理 `AgentState.messages` 的实际持久化和压缩，但不在 AgentState 中新增字段。所有节点通过 LangGraph 内置的 `add_messages` reducer 读写 `messages` 列表，记忆层在每次对话请求前后执行：

- **请求前**（middleware 层）：根据 `session_id` 从存储中加载历史消息，注入 `AgentState.messages`
- **请求后**（reply_node 完成后）：将更新后的 `AgentState.messages` 写回存储，必要时触发摘要压缩

**记忆层不做什么：**

- 不做跨会话的用户画像聚合（那是用户系统的职责）
- 不做语义检索式的长期记忆（那是 RAG 的职责）
- 不存储完整的原始工具调用结果（工具结果已包含在 `messages` 中）

**会话生命周期：**

**session_id 生成：** 首次对话时（请求中 `session_id` 为空），Agent 在 `auth_node` 鉴权通过后使用 `uuid4()` 生成 36 位会话 ID（格式：`sess_` + UUID4 前 8 位，如 `sess_a1b2c3d4`），写入 `AgentState.session_id`，随首个 `event: done` 返回给前端。后续请求前端携带此 ID 维持会话连续性。

**会话过期策略：**

| 存储介质 | 过期机制 | 过期时间 | 行为 |
|----------|----------|----------|------|
| 内存（MVP） | LRU + TTL：`cachetools.TTLCache(maxsize=500, ttl=1800)` | 30 分钟无活动 | 自动驱逐，下次请求创建新会话 |
| Redis（后续） | `EXPIRE session:{id}:memory 1800` + 每次读写刷新 TTL | 30 分钟无活动 | key 被 Redis 删除，下次请求创建新会话 |

> 30 分钟的选择依据：覆盖一次典型挂号流程（导诊→选科室→选医生→确认挂号，约 5-15 分钟）并留有余量。

**会话过期时的行为：**

| 场景 | 检测方式 | 处理 |
|------|----------|------|
| 前端携带已过期的 `session_id` | 记忆层加载时 key 不存在 | 静默创建新会话（不报错），历史对话清空，首个回复告知用户"对话已超时，这是新的对话" |
| 会话过期时存在未消费的 confirm_token | confirm_token 的 Redis key 独立 TTL（5min），不受会话过期影响 | 前端仍可在 token 有效期内确认——confirm_token 绑定了 `session_id` + `user_id` 双重校验，过期会话的 token 在 Lua 脚本中会因 `session_id` 校验失败被拒绝 |

**会话清理：**

- 内存模式（MVP）：TTLCache 自动驱逐，无需手动清理。Agent 进程重启后所有会话丢失。
- Redis 模式（后续）：Redis TTL 自动过期。主动清理场景：用户登出时调用 `DEL session:{id}:memory`（前端登出时回调 Agent 的会话注销接口，当前 MVP 不做）。

**B 端会话管理（scope=b_end）：**

B 端采用"一次问诊一个会话"策略：医生每次接诊（startConsult）对应一个独立的 Agent 会话，结束问诊或切换患者时会话重置。

| 场景 | Agent 行为 |
|------|-----------|
| 医生开始接诊（前端不传 session_id） | 创建新会话，生成 session_id 随 done 事件返回 |
| 同一问诊内多轮对话 | 前端复用 session_id，Agent 维持上下文 |
| 医生切换患者（consultation_id 变化） | 前端不传 session_id，Agent 创建新会话；旧会话按 TTL 自然过期 |
| 医生结束问诊 | 前端丢弃 session_id，旧会话按 TTL 自然过期 |

> **设计依据**：AI 辅助面板按当前接诊患者加载内容（前端系分 §6），不同患者的对话上下文必须隔离——若复用 session，患者 A 的对话历史会泄漏到患者 B 的 LLM 上下文中，既是隐私问题也是相关性问题。30 分钟 TTL 对 B 端同样适用（一次问诊通常 10-20 分钟）。此策略不需要前后端修改接口契约——现有"session_id 为空时创建新会话"机制已天然支持。


### 5.10 错误处理与降级策略

Agent 依赖多个外部系统（LLM API、Java 后端、PostgreSQL、Redis），任一故障都可能导致对话中断。以下定义各故障场景的检测方式、降级策略和用户感知行为。

**LLM 层错误：**

| 故障场景 | 检测方式 | 降级策略 | 用户看到 |
|----------|----------|----------|----------|
| LLM API 超时（>30s） | httpx TimeoutException | 重试 1 次，仍失败则返回错误 | `error` 事件："服务暂时繁忙，请稍后重试" |
| LLM 返回非 JSON（Function Calling 解析失败） | JSONDecodeError | 丢弃本次 tool_call，将原始响应作为文本注入 `messages`，让 LLM 自行纠正 | 略有延迟，用户无感知（LLM 自愈） |
| LLM 返回 429（速率限制） | HTTP 429 | 等待 `Retry-After` 秒后重试，最多 3 次 | 首次无感知，3 次后提示超限 |
| LLM 返回 5xx | HTTP 5xx | 切换备用供应商（如果配置了 `LLM_FALLBACK_PROVIDER`），否则报错 | 同上 |

**Java 后端错误：**

| 故障场景 | 检测方式 | 降级策略 | 用户看到 |
|----------|----------|----------|----------|
| Java 连接超时（>10s） | httpx ConnectTimeout | 不重试，直接降级 | `error` 事件："系统繁忙，请稍后重试"，日志记录 trace_id |
| Java 返回 4xx（业务异常，如号源已满） | HTTP 4xx | 解析 Java 返回的错误消息，原样透传给 LLM | LLM 将错误转为自然语言建议，如"该时段已被约满，推荐您选择..." |
| Java 返回 5xx | HTTP 5xx | 不重试，降级 | `error` 事件："服务异常，请稍后重试" |
| token/parse 失败 | HTTP 4xx/5xx 或超时 | **fatal**——鉴权是整个对话入口，失败直接返回 401 给前端 | `error` 事件："登录已过期，请重新登录" |

**基础设施错误：**

| 故障场景 | 检测方式 | 降级策略 | 用户看到 |
|----------|----------|----------|----------|
| pgvector 不可用（RAG 检索失败） | psycopg 连接异常 | 跳过 RAG，LLM 仅凭自身知识回答，并在回复中标注"当前无法检索相关知识库" | 正常回复 + 额外提示 |
| Redis 不可用（L2 确认） | redis 连接异常 | **拒绝所有 L2 操作**——无法生成 confirm_token 时不允许执行任何 L2 工具 | `error` 事件："操作暂时不可用，请稍后重试" |
| Redis 不可用（限流） | redis 连接异常 | 放行——限流降级为不限制，避免误伤正常用户 | 无感知 |
| Redis 不可用（对话缓存） | redis 连接异常 | 回退到内存缓存，会话在 Agent 重启后丢失 | 无感知（当前 MVP 本就用内存） |

**降级模式汇总：**

| 模式 | 触发条件 | 影响范围 | 恢复方式 |
|------|----------|----------|----------|
| 正常运行 | 所有外部系统可用 | 全部功能正常 | — |
| RAG 降级 | pgvector 不可用 | 知识库检索跳过，LLM 自行回答 | pgvector 恢复后自动恢复 |
| L2 阻断 | Redis 不可用 | 所有 L2 操作拒绝（挂号、下单等） | Redis 恢复后自动恢复 |
| 完全不可用 | Java 后端或 LLM 全部不可用 | 所有对话功能中断 | 人工恢复 |

**通用处理原则：**
- 外部依赖故障不导致 Agent 进程崩溃——所有外部调用包裹 try/except
- 错误详情写入 structlog（含 trace_id），面向用户的消息脱敏（不暴露内部异常堆栈）
- 降级事件通过 `error` SSE 事件实时告知前端，前端据此调整 UI（如禁用挂号按钮、显示"部分功能不可用"横幅）


### 5.11 系统提示词设计

系统提示词是 LLM 行为的核心约束。Agent 采用"统一基础提示词 + 场景指令动态注入"的两层结构——基础提示词定义角色、边界和工具使用规则（每次对话固定加载），场景指令根据意图识别结果动态拼入（仅当次推理）。

**基础提示词（固定加载）：**

```
你是智愈先锋（SPHP）平台的 AI 健康助手，为用户提供智能导诊、预约挂号、在线问诊、
处方购药和健康管理服务。

## 核心原则
1. 医疗安全第一：你提供的所有建议仅供参考，不能替代专业医生诊断。当用户描述严重症状
   （胸痛、呼吸困难、意识模糊等），必须优先建议立即就医。
2. 信息准确：调用知识库工具（search_medical_knowledge）获取医学知识后再回答专业问题，
   不要凭记忆编造医学信息。
3. 渐进式引导：通过提问逐步缩小用户需求，不要一次性抛出过多选项。每次最多推荐 3 个选项。
4. 隐私保护：不要主动询问超出诊疗必要范围的个人信息。

## 工具使用策略
- 调用任何工具前，先用一句话说明你的意图和理由，例如"让我先查询您附近的科室信息"，
  然后再调用工具。不要直接调用工具而不解释。
- 用户描述症状 → 先追问关键信息（时长、部位、伴随症状），再调用 create_triage_assessment
- 用户想挂号 → 先调 query_departments 看科室，再调 query_doctors 看医生，最后调
  query_schedule_slots 看时段，逐步缩小范围
- 用户问医学知识 → 必须先调 search_medical_knowledge 检索知识库，基于检索结果回答
- 查询类工具（L1）可以连续调用，无需用户确认
- 操作类工具（L2）每次调用前会生成确认卡片，你必须等待用户确认后再继续

## 输出要求
- 回复简洁，每次 2-5 句
- 使用通俗语言解释医学术语
- 每次回复末尾必须包含免责声明："AI 建议仅供参考，不能替代医生诊断"
- 语气温暖、专业、不制造焦虑
```

**场景指令（按意图动态注入）：**

| 意图 | 追加指令 |
|------|----------|
| triage | 追问症状的持续时间、严重程度、是否有过敏史。生成导诊评估后列出推荐科室和理由。如用户描述症状符合急诊指征，优先建议立即就医。 |
| registration | 解释每个可选时段的特点（上午/下午，余量）。挂号确认前展示完整挂号信息（医院、科室、医生、时段、费用）供用户核对。 |
| consultation | 预问诊时按主诉→现病史→既往史→过敏史的顺序采集信息。解读处方时逐条解释药品名称、用法用量、注意事项。 |
| pharmacy | 推荐药店时综合考虑价格、距离、库存。下单前确认收货地址和配送方式。 |
| qa | 必须基于 search_medical_knowledge 的检索结果回答。如果检索无结果，明确告知"未找到相关资料"而非编造。 |

**提示词设计原则：**

- 基础提示词不超过 500 字——过长会稀释关键指令，降低 LLM 遵循度
- 场景指令 ≤100 字——仅追加当前场景最相关的行为约束
- 工具使用策略用"用户意图 → 调用顺序"的映射格式，不用抽象规则描述——LLM 对具体示例的遵循度远高于抽象指令
- 医疗安全声明在每个回复末尾强制注入（`reply_node` 中做后处理追加，不依赖 LLM 自觉）

**B 端 tool_caller 提示词（M8-4）：**

`tool_caller` 节点按 scope 分支系统提示词（C 端患者语义 / B 端医生工作台语义）：

| 维度 | C 端（`TOOL_CALLER_SYSTEM_PROMPT`） | B 端（`B_TOOL_CALLER_SYSTEM_PROMPT`） |
|------|----------------------------------|----------------------------------------|
| 身份 | 医疗平台工具调用助手，服务患者 | **医生工作台助手，服务医生本人** |
| 授权语义 | 用户请求即代表发起授权（患者自助操作） | 医生请求即代表发起授权（医生操作，仍走 L2 确认卡） |
| 患者数据 | 查询自身数据 | **必须携带当前接诊患者 patient_id**，缺失先索要、禁止编造；**脱敏展示，不泄露敏感信息** |
| 草稿边界 | 无 | **病历草稿（generate_draft_note）仅供医生修改确认，不可替代医生签名** |

> **设计依据**：免责声明（"AI 建议仅供参考"）由 `reply_node` 全场景强制注入（B 端也有），
> 故 B 端 tool_caller 提示词不重复免责声明，只补齐 C 端患者语义中缺失的医生身份、
> 患者隐私、草稿边界三类约束（M8-4 修正表述：真正缺口在 tool_caller 层而非 reply 层）。
> 与 M8-3（B 端直达工具子图）配套，构成 B 端编排双改项。

### 5.12 LangGraph 流式输出与 SSE 映射

Agent 使用 LangGraph 的 `astream_events` API 获取流式输出，在路由级 SSE handler 中将 LangGraph 事件映射为 SSE 事件推送给前端。映射关系如下：

**事件映射表：**

| LangGraph 事件 | SSE 事件 | 触发时机 | 前端行为 |
|----------------|----------|----------|----------|
| `on_chat_model_stream`（`reasoning_content`） | `event: thought` | LLM 推理 token（DeepSeek-R1 等推理模型独有） | 折叠面板，浅色显示 |
| `on_chat_model_stream`（`content`） | `event: message` | LLM 正常输出 token | 对话气泡逐字追加 |
| `on_tool_start` | `event: action` | 工具调用开始 | "调用工具"卡片，显示工具名 + 请求参数，loading 状态 |
| `on_tool_end` | `event: observation` | 工具返回结果（L1 或已确认的 L2） | 更新同一张卡片，显示响应结果（可展开），绿色勾选 |
| `safety_check` L2 | `event: card` | L2 操作需用户确认 | 确认卡片，含 `confirm_token` + `session_id`（前端确认时一并回传，见 §6.2.1） |
| 异常 | `event: error` | 鉴权失败、工具执行异常、超时等 | 展示错误提示 |
| `on_chain_end` | `event: done` | 本轮推理全部完成 | 停止 loading 动画，session_id 随首个 done 返回 |

> **ReAct 可见性**：`on_tool_start` 和 `on_tool_end` 以前不推送 SSE，现在分别映射为 `action` 和 `observation` 事件。用户可以看到"调了什么工具、传了什么参数、返回了什么结果"。`message` 事件出现在 `action` 之前的内容属于思考（LLM 说明意图），出现在最后一个 `observation` 之后的内容属于最终回复。推理模型（如 DeepSeek-R1）的 `reasoning_content` 额外通过 `thought` 事件推送。

**SSE 事件格式：**

各事件（message / thought / action / observation / card / error / done）的**字段定义、示例与 card_type 枚举以 §6.2.1 为准**（字段均已统一：`action.label`、`observation.status/result/summary/duration_ms`、`card.details`、`error.trace_id`、`done.usage`）。本节仅约定事件时序与前端行为：`message` 逐 token 流式、`thought` 折叠展示、`action`→`observation` 配对合并为一张"调用工具"卡片（loading → 结果）、`card` 触发确认、`done` 收尾。

**前端渲染示意：**

一次完整的 ReAct 对话在前端的呈现效果：

```
┌──────────────────────────────────────────┐
│ 💭 思考（折叠/展开）                       │  ← thought 事件（推理模型）
│ 患者提到头痛和发烧三天，我需要先...        │
├──────────────────────────────────────────┤
│ 让我先查询您附近的科室信息                │  ← message 事件（LLM 说明意图）
├──────────────────────────────────────────┤
│ 🔧 调用工具：查询科室列表                  │  ← action 事件
│   请求：{"hospital_id": 1, "keyword": "神经"} │
│   ✓ 查询完成 · 找到3个相关科室 · 120ms    │  ← observation 事件
│   [展开查看完整结果]                      │
├──────────────────────────────────────────┤
│ 根据您的症状，我为您推荐以下科室：        │  ← message 事件（最终回复）
│ 1. 神经内科 - 适合头痛诊断               │
│ 2. 急诊科 - 如有紧急症状                 │
│ AI 建议仅供参考，不能替代医生诊断         │
└──────────────────────────────────────────┘
```

前端处理逻辑：`action` 到来时创建工具卡片并显示 loading；`observation` 到来时更新同一张卡片的结果区并标记完成；`thought` 和 `observation` 默认折叠，用户可点击展开。

**SSE handler 实现伪代码：**

```python
# engine/api/routes/chat.py

# 工具中文标签映射（启动时从 ToolRegistry 加载）
TOOL_LABELS = {
    "query_departments": "查询科室列表",
    "query_doctors": "查询医生信息",
    "create_appointment": "创建挂号订单",
    "search_medical_knowledge": "检索医学知识库",
    # ... 其余 35 个工具
}

def summarize_result(tool_name: str, output: Any) -> str:
    """将工具返回的结构化数据压缩为一行摘要。"""
    if isinstance(output, Exception):
        return f"执行失败: {output}"
    if isinstance(output, dict) and "data" in output:
        data = output["data"]
        if isinstance(data, list):
            return f"返回 {len(data)} 条记录"
        if isinstance(data, dict):
            return f"返回详情: {data.get('name', data.get('id', 'OK'))}"
    return "完成"

async def stream_sse(graph, state: AgentState):
    """路由级 SSE 流，捕获整个图执行的事件并映射为 SSE。"""
    # 1. 如果有待确认的 L2 操作，推送 card 事件
    if state.get("pending_confirmation"):
        yield format_sse("card", state["pending_confirmation"])
        yield format_sse("done", {"session_id": state.get("session_id")})
        return

    # 2. 遍历 astream_events，映射为 SSE
    async for event in graph.astream_events(state, version="v2"):
        evt_type = event["event"]

        # ① 推理模型思考 token（DeepSeek-R1 等）
        if evt_type == "on_chat_model_stream":
            chunk = event["data"]["chunk"]
            reasoning = getattr(chunk, "reasoning_content", None)
            if reasoning:
                yield format_sse("thought", {"delta": reasoning})
            if chunk.content:
                yield format_sse("message", {"delta": chunk.content})

        # ② 工具调用开始
        elif evt_type == "on_tool_start":
            tool_name = event["name"]
            args = event["data"].get("input", {})
            yield format_sse("action", {
                "tool": tool_name,
                "label": TOOL_LABELS.get(tool_name, tool_name),
                "arguments": args,
            })

        # ③ 工具调用结束
        elif evt_type == "on_tool_end":
            tool_name = event["name"]
            output = event["data"].get("output", {})
            status = "success" if not isinstance(output, Exception) else "error"
            yield format_sse("observation", {
                "tool": tool_name,
                "status": status,
                "result": output,
                "summary": summarize_result(tool_name, output),
                "duration_ms": event["data"].get("metadata", {}).get("duration_ms"),
            })

    # 3. 推送 done 事件
    yield format_sse("done", {
        "session_id": state.get("session_id"),
        "usage": extract_usage(state),
    })
```

**异常路径：**

如果在 `astream_events` 过程中发生异常，SSE handler 的 `except` 块推送 `event: error` 后紧跟 `event: done`，保证前端正确结束本次 SSE 连接：

```python
try:
    async for sse in stream_sse(graph, state):
        yield sse
except AgentAuthError as e:
    yield format_sse("error", {"code": e.code, "message": e.message})
    yield format_sse("done", {})
except Exception:
    logger.exception("stream_sse failed")
    yield format_sse("error", {"code": "SERVER_ERROR", "message": "服务异常，请稍后重试"})
    yield format_sse("done", {})
```

> **设计约束**：`event: done` 必须在每条 SSE 流的最后发送（即便是异常路径），否则前端 SSE 连接无法正常关闭，导致 loading 动画持续显示。


---

## 6. 接口设计

### 6.1 全局约定

Agent 独立部署于 8081 端口，直接对前端暴露。Java 后端部署于 8080 端口，仅提供内部和业务接口。

| 项目 | 约定 |
|------|------|
| 前端 → Agent 鉴权 | 前端携带 JWT Bearer Token + scope 字段，Agent 中间件调 Java `GET /api/c/v1/auth/token/parse`（C 端）/ `GET /api/b/auth/token/parse`（B 端）校验 |
| Agent → Java 鉴权 | 方案 A：Agent 拿到前端 JWT 后调 Java token/parse 接口换取 userId；后续调业务 API 时 Header 携带 `X-User-Id` |
| MCP 通信 | Agent 编排层（MCP Client）通过 stdio transport 与内嵌 MCP Server 通信，使用 JSON-RPC 协议 |
| 内容类型 | `application/json`；前端→Agent 流式对话为 `text/event-stream`（SSE） |
| 时间格式 | ISO-8601，东八区，如 `2026-07-30T10:00:00+08:00` |
| 金额 | 整数分 `amountCent`，与后端一致 |
| 跟踪 | 响应中返回 `traceId`，全链路贯通 |
| 错误响应格式 | Agent 对外 HTTP 接口（`/api/chat/confirm`、`/api/knowledge/*`）统一采用 Java 风格信封 `{"code":"...","message":"...","data":null,"traceId":"..."}`，成功 `code="00000"`，失败 `code` 为字符串错误码；`/api/chat/stream` 为 SSE 流，错误通过 `event: error` 推送 `{"code":"...","message":"...","trace_id":"..."}` |

**通用错误码：**

| 错误码 | HTTP 状态码 | 说明 |
|--------|------------|------|
| AUTH_MISSING | 401 | 请求缺少 Authorization Header |
| AUTH_EXPIRED | 401 | JWT 已过期 |
| AUTH_INVALID | 401 | JWT 签名无效或格式错误 |
| RATE_LIMITED | 429 | 请求频率超过限制 |
| INVALID_REQUEST | 400 | 请求参数不合法（缺失必填项、格式错误、内容超长） |
| SESSION_NOT_FOUND | 404 | 会话 ID 不存在 |
| TOOL_DENIED | 403 | 请求的操作不被允许（L3/L4 级拦截） |
| TOOL_FAILED | 500 | MCP 工具执行失败（后端业务异常） |
| CONFIRM_EXPIRED | 400 | 确认令牌已过期 |
| CONFIRM_INVALID | 400 | 确认令牌格式无效 |
| CONFIRM_CONSUMED | 400 | 确认令牌已被消费 |
| SESSION_MISMATCH | 400 | 确认令牌与会话 ID 不匹配 |
| SERVER_ERROR | 500 | 服务器内部错误 |

### 6.2 前端 → Agent：对话接口

#### 6.2.1 发起对话

前端 C 端和 B 端统一通过此端点发起 AI 对话，Agent 以 SSE（Server-Sent Events）流式返回推理结果。

```
POST /api/chat/stream
Content-Type: application/json
Authorization: Bearer <JWT Token>
Accept: text/event-stream
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | string | 是 | 用户输入文本，1-2000 字 |
| scope | string | 是 | 服务端：`c_end`（患者端）/ `b_end`（医生端），Agent 据此选择鉴权接口和工具集 |
| session_id | string | 否 | 会话 ID；为空时 Agent 创建新会话并在首个 `done` 事件中返回 |
| context | object | 否 | 附加上下文，帮助 Agent 理解当前页面状态 |

**context 字段：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | string | 否 | 当前页面标识：`triage` / `appointment` / `consultation` / `pharmacy` / `health` / `doctor_workbench` |
| hospital_id | int | 否 | 当前选择的医院 ID（C 端工具如 `query_departments`、`query_schedule_slots` 需要此参数）。**待前端确认**：前端系分当前 context 不含此字段 |
| doctor_id | int | 否 | 当前页面选中的医生 ID |
| patient_id | int | 否 | 当前问诊患者 ID（B 端医生接诊时传入） |
| appointment_id | int | 否 | 当前查看的挂号单 ID |
| consultation_id | int | 否 | 当前查看的问诊记录 ID |

**请求示例：**

```json
{
  "content": "我头疼三天了，还有点发烧",
  "scope": "c_end",
  "session_id": "sess_abc123",
  "context": {
    "page": "triage"
  }
}
```

**HTTP 状态码（非 SSE 场景）：**

| 状态码 | 错误码 | 说明 |
|--------|--------|------|
| 200 | — | 成功，开始 SSE 流 |
| 400 | INVALID_REQUEST | content 为空或超长 |
| 401 | AUTH_EXPIRED / AUTH_INVALID | JWT 无效或过期 |
| 429 | RATE_LIMITED | 请求频率超限 |
| 500 | SERVER_ERROR | 服务器内部错误 |

> 非 200 响应为普通 JSON 格式（非 SSE），格式见 §6.1 通用错误码。

**SSE 事件：**

对话过程中 Agent 通过 SSE 推送以下事件，每个事件占一行，格式为 `event: <事件名>\ndata: <JSON>\n\n`。

**① message——LLM 文本增量**

Agent 推理过程中逐 token 推送文本内容，前端追加显示。

```
event: message
data: {"delta": "建议您挂号呼吸内科"}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| delta | string | 增量文本片段，前端累加拼接 |

**② thought——LLM 推理过程**

仅推理模型（如 DeepSeek-R1，`LLM_MODEL=deepseek-reasoner`）触发。逐 token 推送模型的内部推理内容，前端折叠显示，与 `message` 区分。

```
event: thought
data: {"delta": "患者提到头痛和发烧三天，我需要先查询科室列表..."}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| delta | string | 推理增量文本片段，前端累加拼接 |

**③ action——工具调用开始**

LLM 决定调用工具时推送，前端创建"调用工具"卡片并显示 loading 状态。`label` 为工具中文名，`arguments` 为传给工具的完整参数。

```
event: action
data: {"tool": "query_departments", "label": "查询科室列表", "arguments": {"hospital_id": 1, "keyword": "神经"}}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| tool | string | 工具名称（英文标识符） |
| label | string | 工具中文标签，用于 UI 展示 |
| arguments | object | 传给工具的完整参数 JSON |

**④ observation——工具调用结果**

工具执行完毕后推送，前端更新对应的"调用工具"卡片结果区，标记完成（绿色勾选）。`result` 为完整返回数据，前端可折叠展开。

```
event: observation
data: {"tool": "query_departments", "status": "success", "result": {"departments": [...]}, "summary": "找到3个相关科室", "duration_ms": 120}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| tool | string | 工具名称，与 `action` 事件对应 |
| status | string | 执行状态：`success` / `error` |
| result | object | 工具完整返回数据，前端可折叠展开查看 |
| summary | string | 一行摘要，折叠状态下展示 |
| duration_ms | int | 工具执行耗时（毫秒） |

**⑤ card——确认卡片**

L2 级操作需要用户确认时，Agent 推送确认卡片。用户确认后前端调用 `/api/chat/confirm`，传入 `confirm_token` + `session_id`（二者均来自 `card` 事件）。

```
event: card
data: {
  "card_type": "confirm_appointment",
  "confirm_token": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "session_id": "sess_a1b2c3d4",
  "title": "确认挂号",
  "summary": "呼吸内科 · 王医生 · 7月31日 上午 9:00-9:30",
  "details": {
    "department_name": "呼吸内科",
    "doctor_name": "王医生",
    "schedule_time": "2026-07-31T09:00:00+08:00",
    "fee_cent": 5000
  },
  "expires_at": "2026-07-30T10:05:00+08:00"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| card_type | string | 卡片类型，决定前端渲染样式和 details 字段结构 |
| confirm_token | string | 确认令牌（UUID4，36 位），前端回调时原样传回 |
| session_id | string | 当前会话 ID，前端回调 `/api/chat/confirm` 时与 `confirm_token` 一并携带，用于跨会话盗用校验 |
| title | string | 卡片标题 |
| summary | string | 单行摘要，快速展示 |
| details | object | 结构化详情，字段因 card_type 而异 |
| expires_at | string | 令牌过期时间（ISO-8601），前端超时后禁用确认按钮 |

> **注**：`session_id` 与 `confirm_token` 必须同时下发、同时回传。confirm_token 在 Redis 中绑定了 `session_id`（见 §5.5），校验时两者不匹配将被拒绝，防止跨会话盗用令牌。前端系分 V1.1 已据此要求 card 事件携带 `session_id`。

**card_type 枚举：**

| card_type | 触发工具 | details 特有字段 |
|-----------|----------|------------------|
| `confirm_appointment` | create_appointment | department_name, doctor_name, schedule_time, fee_cent |
| `confirm_cancel_appointment` | cancel_appointment | appointment_id, department_name, doctor_name, schedule_time |
| `confirm_pre_consultation` | save_pre_consultation | chief_complaint（主诉摘要）, history_summary（现病史摘要） |
| `confirm_send_message` | send_consultation_message | message_preview（消息预览前 50 字） |
| `confirm_drug_order` | create_drug_order | pharmacy_name, drug_list（药品清单数组）, total_cent |
| `confirm_cancel_drug_order` | cancel_drug_order | drug_order_id, total_cent |
| `confirm_drug_receipt` | confirm_drug_receipt | drug_order_id |
| `confirm_waitlist` | join_waitlist | slot_id, patient_id |
| `confirm_allergy` | manage_allergy | allergy_name（过敏原名称）, action（`add` / `update`） |
| `confirm_medical_history` | manage_medical_history | condition_name（病史名称）, action（`add` / `update`） |
| `confirm_report` | create_report | report_type（报告类型）, report_date（报告日期） |
| `confirm_medication_plan` | update_medication_plan | plan_name, action（`PAUSE` / `RESUME` / `COMPLETE`） |
| `confirm_follow_up` | confirm_follow_up | follow_up_type, scheduled_time（提醒时间） |
| `confirm_draft_note` | generate_draft_note | patient_name, note_preview（草稿摘要） |
| `confirm_patient_history` | query_patient_history | patient_id |

> **注**：details 为**结构化详情，字段因 card_type 而异**；其中名称类字段（如 `department_name` / `doctor_name` / `plan_name`）依赖 L2 确认执行后回填，Agent 在生成卡片（执行前）仅透出工具参数中可得的 ID/动作类字段，缺失字段前端按 card_type 降级展示。

**⑥ error——错误事件**

```
event: error
data: {"code": "TOOL_FAILED", "message": "号源已被抢完，请选择其他医生", "trace_id": "trc_xyz789"}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | string | 错误码，枚举值见 §6.1 通用错误码 |
| message | string | 面向用户的可读错误描述 |
| trace_id | string | 全链路追踪 ID |

**⑦ done——本轮结束**

```
event: done
data: {"session_id": "sess_abc123", "trace_id": "trc_xyz789", "usage": {"prompt_tokens": 520, "completion_tokens": 128}}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| session_id | string | 当前会话 ID，前端应保存用于后续对话 |
| trace_id | string | 全链路追踪 ID |
| usage | object | 本轮 LLM token 用量（`prompt_tokens` / `completion_tokens` / `total_tokens`），无 LLM 调用时为 null |

> **注**：ReAct 过程对用户可见——`action` 和 `observation` 事件让用户看到"调了什么工具、传了什么参数、返回了什么结果"；`thought` 事件（推理模型独有）展示 LLM 内部推理。`message` 事件出现在 `action` 之前的内容属于思考意图，出现在最后一个 `observation` 之后的内容属于最终回复。前端通过 `action`→`observation` 的时序配对，在 UI 中合并为一张"调用工具"卡片。详见 §5.12 事件映射表。

#### 6.2.2 L2 确认回调

前端用户点击确认卡片后，回调此端点。Agent 校验 `confirm_token`（一次性消费 + session_id/userId 绑定校验，见 §5.5），通过后同步执行对应的 MCP 工具，并**同步返回业务执行结果**。本端点为独立 HTTP 请求，前端无需依赖原 SSE 流续推。

```
POST /api/chat/confirm
Content-Type: application/json
Authorization: Bearer <JWT Token>（选填，当前版本非必填；后续 Agent 权限策略另行定义）
```

**请求体：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| confirm_token | string | 是 | Agent 在 `card` 事件中下发的确认令牌（UUID4） |
| session_id | string | 是 | 当前对话会话 ID，来自 `card` 事件或首个 `done` 事件 |

**请求示例：**

```json
{
  "confirm_token": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "session_id": "sess_abc123"
}
```

**成功响应（200）-- 统一信封 `{code, message, data, traceId}`：**

```json
{
  "code": "00000",
  "message": "操作成功",
  "data": {
    "action_result": {
      "appointment_id": 7001,
      "status": "UNPAID",
      "amount_cent": 5000,
      "expire_at": "2026-07-30T10:15:00+08:00",
      "payment_id": 8001
    },
    "message": "挂号成功，请15分钟内完成支付"
  },
  "traceId": "trc_xyz789"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | string | `00000` 表示成功 |
| data.action_result | object | 业务执行结果，结构随 `card_type` 变化（如 `confirm_appointment` 返回挂号单 `appointment_id`/`status`/`amount_cent`/`expire_at`/`payment_id`） |
| data.message | string | 面向用户的业务结果提示，前端据此更新卡片为"已完成"并展示 |
| traceId | string | 全链路追踪 ID |

> `action_result` 的内容直接取自 MCP 工具（Java REST API）的返回数据，Agent 不做二次加工。前端收到 `code=00000` 后按 `data.action_result` 更新卡片状态、按 `data.message` 展示提示（与前端系分 V1.1 §9.5 / 后端系分 V1.2 §7.4.2 一致）。

**错误响应（统一信封，`code` 为字符串错误码）：**

| code | HTTP 状态码 | 说明 | 前端处理建议 |
|------|--------|------|--------------|
| CONFIRM_INVALID | 400 | 令牌格式无效 | 提示用户刷新重试 |
| CONFIRM_CONSUMED | 400 | 令牌已被使用（重复确认） | 提示"已处理，无需重复操作" |
| CONFIRM_EXPIRED | 400 | 令牌已过期（超过 5 分钟） | 提示"操作已超时，请重新发起" |
| SESSION_MISMATCH | 400 | 令牌与会话不匹配 | 提示用户刷新重试 |
| AUTH_EXPIRED / AUTH_INVALID | 401 | JWT 无效或过期 | 跳转登录页 |

```json
{
  "code": "CONFIRM_EXPIRED",
  "message": "确认已超时，请重新发起操作",
  "data": null,
  "traceId": "trc_xyz789"
}
```

> **信封统一说明**：Agent 对外业务 HTTP 接口（`/api/chat/confirm`、`/api/knowledge/*`）统一采用与 Java 后端一致的 `{code, message, data, traceId}` 信封，成功 `code="00000"`，失败 `code` 为字符串错误码（见 §6.1）。`/health` 为基础设施级健康检查端点（供负载均衡 / 容器探针使用），采用独立的 `{status, checks, version}` 格式，不套用业务信封。`/api/chat/stream` 为 SSE 流式接口，其错误通过 `event: error` 推送（字段为 `code`/`message`/`trace_id`，见 §6.2.1 ⑥），不套用此信封。

### 6.3 Agent → Java：鉴权接口

Agent 不自建鉴权接口，直接调用 Java 后端已有的 token 解析接口校验前端 JWT 并获取 userId。C 端和 B 端各自独立部署，JWT 签名密钥互不通用，Agent 根据 chat 请求中的 `scope` 字段决定调用哪一侧的接口。

**scope 来源**：前端在 chat 请求体中传入 `scope`（`c_end` / `b_end`），标识当前用户归属。Agent 据此选择鉴权接口和工具集（C 端 30 个 / B 端 9 个），不依赖鉴权接口返回 scope。

**C 端鉴权（已有接口）：**

```
GET /api/c/v1/auth/token/parse
Authorization: Bearer <JWT Token>
```

| 字段 | 位置 | 类型 | 必填 | 说明 |
|------|------|------|------|------|
| Authorization | Header | string | 是 | 前端传来的 C 端 Access Token |

响应（出入参格式与 C 端后端系分 V1.3 一致）：

```json
{
  "code": "00000",
  "message": "令牌解析成功",
  "data": {
    "userId": 10001,
    "account": "zhangsan",
    "tokenExpiresAt": "2026-07-30T12:00:00+08:00"
  },
  "traceId": "01J7X-TOKEN-001"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | string | 业务码，`00000` 表示成功 |
| data.userId | long | C 端用户 ID，写入 AgentState |
| data.account | string | 登录账号 |
| data.tokenExpiresAt | string | Token 过期时间（ISO-8601，东八区） |

非 `00000` 响应码（如 `A0301` = Token 无效/过期）时，Agent 直接向前端返回 401，不继续后续流程。

**B 端鉴权（后端已定义，见后端系分 V1.2 §7.3.0(3)）：**

B 端后端已提供与 C 端对齐的只读 token 解析接口：

```
GET /api/b/auth/token/parse
Authorization: Bearer <JWT Token>
```

响应（出入参格式与后端系分 V1.2 §7.3.0(3) 一致）：

```json
{
  "code": "00000",
  "message": "令牌解析成功",
  "data": {
    "userId": 2001,
    "account": "doctor_li",
    "roles": ["DOCTOR"],
    "deptId": 10,
    "doctorId": 101,
    "hospitalId": 1,
    "tokenExpiresAt": "2026-07-30T12:00:00+08:00"
  },
  "traceId": "01J7X-BAUTH-003"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | string | 业务码，`00000` 表示成功 |
| data.userId | long | B 端用户 ID（b_user.id），写入 AgentState |
| data.account | string | 登录账号 |
| data.roles | list[string] | 角色：`ADMIN` / `DEPT_HEAD` / `DOCTOR`，写入 AgentState 供 B 端工具路由与审计 |
| data.deptId | long | 所属科室 ID（role=DOCTOR/DEPT_HEAD 时经 doctor_id 取得） |
| data.doctorId | long | 关联医生 ID（ADMIN 无） |
| data.hospitalId | long | 所属医院 ID（数据隔离维度） |
| data.tokenExpiresAt | string | Token 过期时间（ISO-8601，东八区） |

非 `00000` 响应码（如 `A0301` = Token 无效/过期）时，Agent 直接向前端返回 401，不继续后续流程。

> **B 端上下文落库**：Agent 鉴权后将 `userId / roles / deptId / doctorId / hospitalId` 写入 AgentState（见 §7.1），用于 B 端工具的 MCP 调用注入 Header（见 §6.4）与审计日志（见 §5.8）。B 端业务 API 的数据权限（DataScope）由 Java 端基于这些字段过滤，详见 §6.4 与 §8.2 的鉴权头策略说明。

**鉴权流程：**

1. 前端发起 chat 请求，请求体含 `scope` 字段，Header 含 `Authorization: Bearer <JWT>`
2. Agent 根据 `scope` 调用对应 Java token 解析接口
3. 校验通过 → 提取 `userId` 写入 AgentState（B 端同时写入 roles / deptId / doctorId / hospitalId），注册对应工具集，继续对话
4. 校验失败 → 返回 401，不进入对话流程

### 6.4 Agent → Java：业务数据接口（MCP 封装）

Agent 通过 MCP 协议调用 Java REST API。MCP Server 中每个工具封装一个或多个 Java API 调用：

```
Agent 编排层（MCP Client）
    │ tools/list          → 获取全部工具 Schema
    │ tools/call          → 请求执行工具
    ▼
MCP Server（Python，Agent 内嵌）
    │ HTTP REST           → 调 Java 业务 API
    ▼
Java 后端（:8080）
    ├── /api/c/v1/*       → C 端业务 API（55 个，见 C 端后端系分 §5）
    └── /api/b/*          → B 端业务 API（42 个，见 B 端后端系分 §5.2）
```

**MCP Server 请求 Java API 的通用格式：**

```
方法: GET/POST/PUT/PATCH/DELETE（根据具体 API）
路径: C 端 /api/c/v1/{resource} ；B 端 /api/b/admin/{resource} 或 /api/b/doctor/{resource}
Header:
  X-User-Id: {userId}       ← Agent 从鉴权获得的 userId
  X-Idempotency-Key: {key}  ← 创建型操作的幂等键（由 MCP Server 生成）
  Content-Type: application/json
```

**B 端鉴权头策略：**

Agent 调 B 端业务 API（`/api/b/*`）时仅传 `X-User-Id`，不转发 B 端 JWT，也不传 `X-Roles` / `X-Hospital-Id` 等额外头。Java 端通过内部鉴权辅助（拦截器或 Filter）按 `X-User-Id` 查询 `b_user` 表，补全 role / hospitalId / doctorId / deptId 上下文，供 DataScope 数据权限过滤使用。Agent 侧无需感知 B 端多字段鉴权细节，实现最简。

> **待后端确认**：Java 端需确认该内部鉴权辅助已实现或计划实现（即收到 `X-User-Id` 后自动加载 b_user 上下文），否则 B 端 DataScope 无法正常工作。

> **注**：所有 Java REST API 的详细定义（路径、参数、响应结构）见 C 端后端系分 §5 和 B 端后端系分 §5.2。本模块只描述 MCP Server 如何封装和调用这些 API，不重复定义 API 细节。

### 6.5 知识库管理接口

知识库检索（pgvector）在 Agent 内部本地执行，以下接口用于知识库的文档管理和调试检索，不经过 MCP Server 或 Java 后端。

#### 6.5.1 文档入库

```
POST /api/knowledge/ingest
Content-Type: multipart/form-data
Authorization: Bearer <JWT Token>
```

**请求：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | file | 是 | 文档文件，支持 txt / md / pdf / csv，单文件 ≤ 10MB |
| title | string | 是 | 文档标题，用于来源标注 |
| category | string | 否 | 分类标签：`patient_edu`（患者科普）/ `clinical_ref`（临床参考），默认 `patient_edu` |
| source | string | 否 | 来源说明，如"《中国药典》2025版" |

**请求示例（curl）：**

```bash
curl -X POST http://localhost:8081/api/knowledge/ingest \
  -H "Authorization: Bearer <JWT>" \
  -F "file=@感冒用药指南.md" \
  -F "title=感冒用药指南" \
  -F "category=patient_edu" \
  -F "source=《中国药典》2025版"
```

**成功响应（200）-- 统一信封 `{code, message, data, traceId}`：**

```json
{
  "code": "00000",
  "message": "success",
  "data": {
    "document_id": "doc_20260730_001",
    "title": "感冒用药指南",
    "chunk_count": 12,
    "status": "indexed"
  },
  "traceId": "a1b2c3d4"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| document_id | string | 入库后分配的唯一 ID |
| chunk_count | int | 切分后的向量块数量 |
| status | string | `indexed`（入库完成）/ `failed`（入库失败） |

**错误响应：**

| 状态码 | 错误码 | 说明 |
|--------|--------|------|
| 400 | INVALID_REQUEST | 文件格式不支持或大小超限 |
| 401 | AUTH_INVALID | JWT 无效 |

#### 6.5.2 检索测试

调试用接口，模拟 C 端 `search_medical_knowledge` 的检索流程，含相似度分数和来源引用。

```
GET /api/knowledge/search?q=感冒发烧怎么办&top_k=5&category=patient_edu
Authorization: Bearer <JWT Token>
```

**查询参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| q | string | 是 | 检索查询词 |
| top_k | int | 否 | 返回结果数，默认 5，最大 20 |
| category | string | 否 | 分类过滤：`patient_edu` / `clinical_ref`，不传则不过滤 |

**成功响应（200）-- 统一信封 `{code, message, data, traceId}`：**

```json
{
  "code": "00000",
  "message": "success",
  "data": {
    "query": "感冒发烧怎么办",
    "results": [
      {
        "rank": 1,
        "score": 0.92,
        "content": "感冒是由病毒引起的上呼吸道感染，常见症状包括...",
        "source_doc": "感冒用药指南",
        "source_page": "第3页",
        "chunk_id": "chunk_20260730_001"
      }
    ],
    "total_hits": 12,
    "elapsed_ms": 45
  },
  "traceId": "e5f6g7h8"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| results[].rank | int | 排名 |
| results[].score | float | 余弦相似度，0-1 |
| results[].content | string | 知识片段内容 |
| results[].source_doc | string | 来源文档标题 |
| results[].source_page | string | 来源页码或段落 |
| results[].chunk_id | string | 片段唯一 ID |
| total_hits | int | 检索命中总数 |
| elapsed_ms | int | 检索耗时（毫秒） |

**错误响应：**

| 状态码 | 错误码 | 说明 |
|--------|--------|------|
| 400 | INVALID_REQUEST | q 为空 |
| 401 | AUTH_INVALID | JWT 无效 |


---

## 7. 数据模型

### 7.1 对话状态（AgentState）

LangGraph 图中流转的核心状态对象：

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| messages | list[BaseMessage] | `[]` | 对话消息列表，LangGraph 内置 `add_messages` reducer 自动追加 |
| session_id | str \| None | `None` | 会话唯一标识，首次对话时生成，随首个 `done` 事件返回前端 |
| intent | str \| None | `None` | 当前识别的业务意图：`triage` / `registration` / `consultation` / `pharmacy` / `qa` / `chitchat` |
| user_id | int \| None | `None` | 从 JWT 鉴权获得的用户 ID，MCP 调用时注入 Header `X-User-Id` |
| scope | str | `"c_end"` | 当前服务端：`c_end` / `b_end` |
| roles | list[str] \| None | `None` | B 端用户角色（`ADMIN` / `DEPT_HEAD` / `DOCTOR`），由 auth_node 从 B 端 token/parse 写入，供审计日志与工具路由 |
| dept_id | int \| None | `None` | B 端用户所属科室 ID，由 auth_node 写入 |
| doctor_id | int \| None | `None` | B 端用户关联的医生 ID（b_doctor.id），由 auth_node 写入 |
| hospital_id | int \| None | `None` | B 端用户所属医院 ID，由 auth_node 写入；C 端场景由前端 context.hospital_id 传入 |
| tool_calls | list[dict] \| None | `None` | LLM 决定调用的工具列表，由 `tool_caller` 节点写入，`safety_check` 和 `tool_executor` 消费 |
| tool_results | list[dict] \| None | `None` | 工具执行结果列表（含成功和失败），由 `tool_executor` 写入，注入 `messages` 供 LLM 下一轮推理 |
| pending_confirmation | dict \| None | `None` | 待用户确认的 L2 操作信息（含 `card_type`、`confirm_token`、`title`、`summary`、`ttl`），非空时 SSE handler 推送 card 事件（见 §5.12） |
| risk_flags | list[str] | `[]` | 风险标记，由 `safety_check` 追加（如 `"drug_allergy"`、`"emergency_symptoms"`），`reply_node` 生成回复时注入警告 |

Pydantic 定义：

```python
from typing import TypedDict, Annotated
from langgraph.graph.message import add_messages
from langchain_core.messages import BaseMessage

class AgentState(TypedDict):
    messages: Annotated[list[BaseMessage], add_messages]
    session_id: str | None
    intent: str | None
    user_id: int | None
    scope: str
    roles: list[str] | None       # B 端角色（ADMIN/DEPT_HEAD/DOCTOR）
    dept_id: int | None           # B 端科室 ID
    doctor_id: int | None         # B 端医生 ID（b_doctor.id）
    hospital_id: int | None       # B 端医院 ID / C 端 context.hospital_id
    tool_calls: list[dict] | None
    tool_results: list[dict] | None
    pending_confirmation: dict | None
    risk_flags: list[str]
```

### 7.2 Function Calling 工具定义

```python
from dataclasses import dataclass, field

@dataclass
class ToolSchema:
    name: str                    # 工具唯一标识，与 MCP Server 注册表一致
    description: str             # 自然语言描述（供 LLM 理解）
    parameters: dict             # JSON Schema（OpenAI Function Calling 格式）
    scope: str                   # c_end / b_end
    security_level: str          # L1 / L2 / L3（L3 不注册）
    executor: str = "mcp"        # 执行器类型："mcp"（经 MCP Server 调 Java）或 "local"（本地执行，如 pgvector 检索）
```

### 7.3 工具调用记录

| 字段 | 类型 | 说明 |
|------|------|------|
| tool_call_id | str | Agent 生成的唯一 ID |
| tool_name | str | 工具名称 |
| arguments_hash | str | 参数 SHA256 前 16 字符（脱敏） |
| user_id | int | 当前用户 ID |
| result_code | str | Java 后端返回的业务码 |
| success | bool | 执行是否成功 |
| duration_ms | int | MCP 调用耗时 |
| timestamp | float | Unix 时间戳 |


---

## 8. 安全设计

### 8.1 纵深防御三层

| 层次 | 位置 | 机制 |
|------|------|------|
| 第一层：prompt 禁令 | C 端/B 端系统提示词 | 明确禁止 L3/L4 操作 |
| 第二层：代码硬拦截 | Agent 工具执行器 | 执行前检查 `security_level`，L3/L4 直接返回 error，不下发 MCP `tools/call` |
| 第三层：Java 鉴权 + API 隔离 | Java 后端 | Java 从 Header `X-User-Id` 过滤数据；API 层面禁止支付/处方类入口 |

核心原则：prompt 禁令只是辅助，真正的安全防线在代码层和 Java API 层。

### 8.2 Agent 安全模型

| 安全机制 | 实现 |
|----------|------|
| JWT 鉴权 | 方案 A：Agent 调 Java `GET /api/c/v1/auth/token/parse`（C 端）/ `GET /api/b/auth/token/parse`（B 端）换取 userId；Java 密钥不离开 Java 端 |
| 数据隔离 | C 端：Agent 调 Java REST API 时 Header 携带 `X-User-Id`，Java Service 层通过 DataScope 过滤（仅返回当前用户可见数据）。B 端：同样仅传 `X-User-Id`，Java 内部鉴权辅助按 userId 加载 b_user 上下文（role / hospitalId / doctorId / deptId），供 DataScope 多字段过滤使用（**待后端确认**该辅助已实现） |
| 工具权限 | MCP Server 中每个工具函数校验参数合法性；L3/L4 工具不注册 |
| 幂等 | 创建型操作的 `X-Idempotency-Key` 由 MCP Server 生成，Java 后端复用传统幂等键机制（Redis 24h） |
| 网络隔离 | Java 业务接口仅 localhost 可访问（Agent ↔ Java 同机部署）；Agent 端口对外暴露但仅接受 JWT 鉴权请求 |
| L2 confirm_token | 一次性消费（Redis get-and-delete）、5 分钟 TTL、绑定 userId + session_id |

### 8.3 禁止操作红线

Agent 在任何情况下均不可执行：开具处方/下诊断结论、直接扣款/退款/修改支付金额、删除病历/报告/处方记录、修改用户权限/取消授权、修改已签名处方内容/伪造问诊记录。这些操作的工具不注册（Agent 无入口），且 Java API 层面不暴露对应入口。

### 8.4 医疗安全声明

所有 Agent 生成的导诊推荐、报告解读、处方说明、用药提醒均须在界面显著标注："AI 建议仅供参考，不替代医生诊断"。此要求在系统提示词中强制规定。


---

## 9. 技术选型

### 9.1 选型依据

| 类别 | 选型 | 依据 |
|------|------|------|
| 开发语言 | Python 3.11+ | AI/LLM 生态成熟度最高，LangChain 原生支持 |
| AI 框架 | LangChain + LangGraph | LangChain 提供 LLM 调用抽象与 RAG 管道；LangGraph 提供有状态多节点图编排 |
| Web 框架 | FastAPI + sse-starlette | 异步高性能，原生支持 SSE 流式输出，Pydantic 模型校验 |
| MCP 协议 | mcp (Python SDK) | Anthropic 官方 Python SDK，提供 MCP Server/Client 标准实现 |
| 向量数据库 | PostgreSQL (pgvector) | 与业务数据库统一，pgvector 0.7+ 支持 IVFFlat/HNSW 索引 |
| 缓存 | Redis 7+ | L2 confirm_token、限流计数器、对话临时缓存 |
| LLM 供应商 | DeepSeek / 智谱 / 通义 | 多供应商抽象（OpenAI 兼容接口），按需切换 |
| HTTP 客户端 | httpx | 异步 HTTP，用于 MCP Server 调 Java REST API |
| 结构化日志 | structlog | JSON 格式输出，兼容 ELK/Loki |

### 9.2 Python 依赖

| 类别 | 库 |
|------|-----|
| AI 编排 | `langchain`, `langchain-openai`, `langgraph`, `langchain-community` |
| MCP 协议 | `mcp` |
| 知识库 RAG | `langchain-postgres`, `langchain-text-splitters`, `psycopg[binary]` |
| Web 服务 | `fastapi`, `uvicorn[standard]`, `sse-starlette`, `python-multipart` |
| 配置与校验 | `pydantic`, `pydantic-settings` |
| HTTP 客户端 | `httpx` |
| 缓存与会话 | `redis[hiredis]` |
| 可观测性 | `structlog` |
| 测试与代码质量 | `pytest`, `pytest-asyncio`, `pytest-mock`, `ruff`, `mypy` |

### 9.3 中间件

| 组件 | 用途 |
|------|------|
| PostgreSQL 16 | 向量存储（pgvector）+ 知识库数据 |
| pgvector 0.7+ | 向量索引，RAG 检索核心 |
| Redis 7+ | 限流、对话缓存 |
| RabbitMQ 3.12+ | 异步任务（用药提醒推送、候补通知） |

> 注意：L2 confirm_token 的 Redis 存储由 Agent 直接管理。

### 9.4 Docker Compose（开发环境基础设施）

应用单独启动，不依赖 Docker。以下 Compose 仅提供开发环境基础设施服务：

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg16
    ports: ["5432:5432"]
    environment:
      POSTGRES_USER: sphp
      POSTGRES_PASSWORD: sphp123
      POSTGRES_DB: sphp
    volumes: [pgdata:/var/lib/postgresql/data]

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

  rabbitmq:
    image: rabbitmq:3.13-management-alpine
    ports: ["5672:5672", "15672:15672"]

volumes:
  pgdata:
```


### 9.5 环境配置

所有配置通过 `.env` 文件注入，经 `pydantic-settings` 自动校验后加载为 `Settings` 单例。

**LLM 配置：**

| 变量 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `LLM_PROVIDER` | string | 是 | LLM 供应商：`deepseek` / `zhipu` / `qwen` |
| `LLM_API_KEY` | string | 是 | API 密钥 |
| `LLM_BASE_URL` | string | 否 | 自定义 API 地址（OpenAI 兼容接口），默认使用供应商官方地址 |
| `LLM_MODEL` | string | 否 | 模型名，默认供应商推荐模型（如 `deepseek-chat`） |
| `LLM_TEMPERATURE` | float | 否 | 生成温度，默认 0.3（医疗场景倾向确定性输出） |
| `LLM_MAX_TOKENS` | int | 否 | 单次最大输出 token，默认 2048 |

**Embedding 配置：**

| 变量 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `EMBEDDING_PROVIDER` | string | 是 | Embedding 供应商：`zhipu` / `qwen` / `openai` |
| `EMBEDDING_API_KEY` | string | 是 | API 密钥（可与 LLM 共用） |
| `EMBEDDING_BASE_URL` | string | 否 | 自定义 API 地址 |
| `EMBEDDING_MODEL` | string | 否 | 模型名，默认 `embedding-3` |

**数据库与缓存：**

| 变量 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `PG_HOST` | string | 否 | `localhost` | PostgreSQL 主机 |
| `PG_PORT` | int | 否 | `5432` | PostgreSQL 端口 |
| `PG_USER` | string | 否 | `sphp` | 用户名 |
| `PG_PASSWORD` | string | 是 | — | 密码 |
| `PG_DATABASE` | string | 否 | `sphp` | 数据库名 |
| `REDIS_HOST` | string | 否 | `localhost` | Redis 主机 |
| `REDIS_PORT` | int | 否 | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | string | 否 | — | Redis 密码（开发环境可空） |
| `RABBITMQ_URL` | string | 否 | `amqp://guest:guest@localhost:5672/` | RabbitMQ 连接串 |

**Agent 自身：**

| 变量 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `AGENT_HOST` | string | 否 | `0.0.0.0` | Agent 监听地址 |
| `AGENT_PORT` | int | 否 | `8081` | Agent 监听端口 |
| `JAVA_BASE_URL` | string | 是 | — | Java 后端地址，如 `http://localhost:8080`（C 端 API 前缀 `/api/c/v1`，B 端 API 前缀 `/api/b`） |
| `C_AUTH_PARSE_PATH` | string | 否 | `/api/c/v1/auth/token/parse` | C 端 token 解析接口路径 |
| `B_AUTH_PARSE_PATH` | string | 否 | `/api/b/auth/token/parse` | B 端 token 解析接口路径 |
| `CONFIRM_TOKEN_TTL` | int | 否 | `300` | L2 confirm_token 有效期（秒） |
| `RATE_LIMIT_PER_MINUTE` | int | 否 | `20` | 单用户每分钟最大请求数 |
| `MEMORY_WINDOW_SIZE` | int | 否 | `10` | 对话记忆窗口轮数 |
| `LOG_LEVEL` | string | 否 | `INFO` | 日志级别 |
| `MCP_TRANSPORT` | string | 否 | `stdio` | MCP Server transport 模式（目前仅支持 stdio） |

**Pydantic Settings 校验规则：**

```python
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    # LLM
    llm_provider: str
    llm_api_key: str
    llm_base_url: str | None = None
    llm_model: str | None = None
    llm_temperature: float = 0.3
    llm_max_tokens: int = 2048

    # Embedding
    embedding_provider: str
    embedding_api_key: str
    embedding_base_url: str | None = None
    embedding_model: str | None = None

    # Database
    pg_host: str = "localhost"
    pg_port: int = 5432
    pg_user: str = "sphp"
    pg_password: str
    pg_database: str = "sphp"

    # Redis
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_password: str | None = None

    # RabbitMQ
    rabbitmq_url: str = "amqp://guest:guest@localhost:5672/"

    # Agent
    agent_host: str = "0.0.0.0"
    agent_port: int = 8081
    java_base_url: str
    c_auth_parse_path: str = "/api/c/v1/auth/token/parse"
    b_auth_parse_path: str = "/api/b/auth/token/parse"
    confirm_token_ttl: int = 300
    rate_limit_per_minute: int = 20
    memory_window_size: int = 10
    log_level: str = "INFO"
    mcp_transport: str = "stdio"

    class Config:
        env_file = ".env"
```

**基础设施连接示意：**

```
Agent (:8081)
    │
    ├── LLM API ────────── DeepSeek / 智谱 / 通义（公网 HTTPS）
    ├── Embedding API ──── 同上
    │
    ├── Java (:8080) ──── java_base_url + /api/c/v1/auth/token/parse（C端）
    │                        java_base_url + /api/b/auth/token/parse（B端）
    │                        java_base_url + /api/c/v1/* + /api/b/*（业务 API）
    │
    ├── PostgreSQL (:5432) ── pgvector 向量检索 + 知识库数据
    ├── Redis (:6379) ──────── confirm_token + 限流 + 对话缓存
    └── RabbitMQ (:5672) ───── 异步任务（用���提醒、候补通知）
```


### 9.6 部署方案

#### 9.6.1 服务启动顺序

系统共四个独立进程，启动顺序有依赖关系：

```
┌─────────────────────────────────────────────────────────┐
│  步骤 1: 基础设施                                        │
│  docker compose up -d postgres   (PG + pgvector)        │
│  （Redis、RabbitMQ 可复用本机已有容器）                    │
├─────────────────────────────────────────────────────────┤
│  步骤 2: Java 后端 (:8080)                               │
│  启动 Spring Boot，自动建表（ddl-auto=update）             │
├─────────────────────────────────────────────────────────┤
│  步骤 3: Python Agent (:8081)                            │
│  启动 FastAPI + MCP Server（stdio 内嵌）                  │
├─────────────────────────────────────────────────────────┤
│  步骤 4: 前端 C 端 (:8001) + B 端 (:8000)               │
│  启动 Umi 开发服务器                                      │
└─────────────────────────────────────────────────────────┘
```

启动依赖链：PostgreSQL → Java → Agent → 前端。其中 Java 启动前需确保 PG 可用，Agent 启动前需确保 Java 可用（注册工具时需调用 Java API 获取工具元数据）。前端启动无严格依赖，但 Agent 不可用时前端对话功能无法使用。

#### 9.6.2 端口映射汇总

| 服务 | 端口 | 协议 | 说明 |
|------|------|------|------|
| 前端 B 端 | 8000 | HTTP | Umi 开发服务器，医生/管理员后台 |
| 前端 C 端 | 8001 | HTTP | Umi 开发服务器，患者端 |
| Java 后端 | 8080 | HTTP | Spring Boot REST API |
| Python Agent | 8081 | HTTP + SSE | FastAPI 对话接口 + SSE 流式输出 |
| PostgreSQL | 5432 | TCP | pgvector 向量存储 + 知识库 |
| Redis | 6379 | TCP | 缓存、confirm_token、限流 |
| RabbitMQ | 5672 | AMQP | 消息队列 |
| RabbitMQ 管理 | 15672 | HTTP | 管理控制台 |

> 注意：Redis 和 RabbitMQ 可复用本机已有的容器，SPHP 项目只需单独启动 PostgreSQL 容器。

#### 9.6.3 开发环境 vs 生产环境

| 配置项 | 开发环境 | 生产环境 |
|--------|----------|----------|
| LOG_LEVEL | DEBUG | INFO |
| LLM_TEMPERATURE | 0.7（更多样测试） | 0.3（确定性输出） |
| CONFIRM_TOKEN_TTL | 300s | 300s（按需调整） |
| RATE_LIMIT_PER_MINUTE | 无限制（设为极大值） | 20（按需调整） |
| MCP_TRANSPORT | stdio | stdio（内嵌无额外端口） |
| PG_HOST | localhost | 内网 IP 或服务名 |
| REDIS_HOST | localhost | 内网 IP 或服务名 |
| uvicorn reload | `--reload`（热重载） | 不使用，配合多 worker |
| Python 依赖 | requirements.txt（含 dev 依赖） | requirements.txt（仅生产依赖） |
| 前端代理 | Umi proxy 直连 localhost:8081 | Nginx 反向代理 |
| 健康检查 | 手动 curl | 接入 Prometheus / 云监控 |

生产环境建议额外配置：

- **Nginx 反向代理**：统一入口，SSL 终结，静态资源缓存
- **多 worker**：`uvicorn app.main:app --host 0.0.0.0 --port 8081 --workers 4`
- **进程管理**：supervisord 或 systemd 守护 Agent 进程
- **日志收集**：structlog JSON 输出 → Filebeat → ELK / Loki

#### 9.6.4 部署验证

部署完成后，按以下步骤验证各服务可用性：

```
# 1. 验证 Java 后端
curl http://localhost:8080/actuator/health
# 期望: {"status": "UP"}

# 2. 验证 Agent 健康检查
curl http://localhost:8081/health
# 期望: {"status":"healthy","checks":{"pg":"ok","redis":"ok","llm":"ok"},"version":"2.0.0"}

# 3. 验证 C 端前端
curl http://localhost:8001
# 期望: 200，返回 HTML

# 4. 验证 B 端前端
curl http://localhost:8000
# 期望: 200，返回 HTML

# 5. 验证 SSE 对话（简单发一条消息）
curl -X POST http://localhost:8081/api/chat/stream \
  -H "Authorization: Bearer <test_token>" \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"content":"你好"}'
# 期望: SSE 流式返回
```

#### 9.6.5 一键启动脚本（开发环境）

```bash
#!/bin/bash
# start-dev.sh — SPHP 开发环境一键启动

echo "=== 1/4 启动基础设施 ==="
docker compose -f C:/Users/Lenovo/PycharmProjects/SPHP/docker-compose.yml up -d postgres
sleep 3  # 等待 PG 就绪

echo "=== 2/4 启动 Java 后端 ==="
cd C:/Users/Lenovo/PycharmProjects/SPHP/sphp-backend
mvn spring-boot:run &
sleep 15  # 等待 Java 启动

echo "=== 3/4 启动 Agent ==="
cd C:/Users/Lenovo/PycharmProjects/SPHP/sphp-agent
uvicorn app.main:app --host 0.0.0.0 --port 8081 --reload &
sleep 5

echo "=== 4/4 启动前端 ==="
# C 端
cd C:/Users/Lenovo/PycharmProjects/SPHP/sphp-frontend-c
npm run dev &
# B 端
cd C:/Users/Lenovo/PycharmProjects/SPHP/sphp-frontend-b
npm run dev &

echo "=== 全部启动完成 ==="
echo "C端: http://localhost:8001"
echo "B端: http://localhost:8000"
echo "Agent: http://localhost:8081"
echo "Java: http://localhost:8080"
```

> 以上为开发环境参考脚本，生产环境请使用 systemd / supervisord / Docker Compose 管理各进程。


---

## 10. 质量属性

### 10.1 性能指标

| 指标 | 目标 |
|------|------|
| AI 首次响应 | ≤ 5s（超时展示"处理中"） |
| Agent 工具调用平均时长 | ≤ 1s（从发起 MCP `tools/call` 到获得结果） |
| AI 对话并发 | 200 人同时使用 |
| 号源查询（经 Agent + MCP） | 500 次/秒 |

### 10.2 可靠性指标

| 指标 | 目标 |
|------|------|
| MCP 工具调用成功率 | ≥ 99.5% |
| 导诊采纳率 | ≥ 75% |
| 用户确认率 | ≥ 80% |
| 安全拦截次数 | 趋于 0 |

### 10.3 可维护性设计

- **分层解耦**：四层架构确保每一层可独立修改和测试
- **工具注册表**：全局单例，新增工具只需定义 ToolSchema + 调用 register()；C 端和 B 端工具均在启动时加载
- **结构化日志**：JSON 格式，便于对接日志平台
- **import 规范**：`from app.xxx import ...` 从应用根导入
- **代码质量**：ruff（line-length=100）+ mypy（strict=true）

### 10.4 并发设计

Agent 需要承载 200 并发 SSE 连接和 500 工具调用/秒，以下是各层的连接池和并发配置。

**连接池配置：**

| 组件 | 池类型 | 池大小 | 依据 |
|------|--------|--------|------|
| httpx（→ Java） | `httpx.AsyncClient` 连接池 | `limits=100, keepalive=30s` | 工具调用平均耗时 < 1s，按 Little's Law：100 连接足以支撑 100 req/s。配合 `semaphore=50` 限制并发未完成请求数 |
| asyncpg（→ PostgreSQL） | 连接池 | `min=5, max=20` | RAG 检索单次耗时 < 100ms，20 连接支撑 200 并发检索。写操作（知识库入库）走批量，不占长连接 |
| redis（→ Redis） | `redis.asyncio` 连接池 | `max_connections=50` | confirm_token 操作为 O(1) 的单 key 读写，50 连接充裕 |
| RabbitMQ | `aio_pika` 连接 | 单连接 + `prefetch_count=10` | 异步任务（用药提醒推送）非实时，单连接即可，prefetch 限制消费者内存 |

**uvicorn 配置：**

```
# 开发环境（单 worker + reload）
uvicorn app.main:app --host 0.0.0.0 --port 8081 --reload

# 生产环境（多 worker）
uvicorn app.main:app --host 0.0.0.0 --port 8081 --workers 4 --limit-concurrency 200
```

| 参数 | 开发 | 生产 | 说明 |
|------|------|------|------|
| workers | 1 | 4 | 生产按 CPU 核数（4 核 → 4 worker），Python GIL 下多 worker 是提升并发的唯一方式 |
| limit-concurrency | 无限制 | 200 | 每 worker 最大并发连接数，4 worker × 200 = 800 总并发，为 200 目标留 4 倍余量 |

**异步模型约束：**

Agent 采用 Python `asyncio` 单线程事件循环模型，所有 I/O（HTTP、DB、Redis、MQ）必须使用异步客户端，禁止在请求处理路径上使用同步阻塞调用。具体约束：

- MCP Server（stdio transport）运行在独立线程，通过 `asyncio.run_coroutine_threadsafe()` 与主事件循环通信
- LLM 调用（httpx AsyncClient）为异步，`astream_events` 原生支持 async iterator
- pgvector 检索使用 `asyncpg` 异步驱动，禁止 `psycopg2`（同步）
- `tool_executor` 中多个独立工具调用使用 `asyncio.gather()` 并发执行（如同时查科室 + 查医生），减少串行等待

**并发执行工具调用示例：**

```python
# tool_executor 中并发执行多个独立 L1 查询
async def tool_executor(state: AgentState) -> dict:
    tasks = []
    for tc in state.tool_calls:
        if ToolRegistry.is_local(tc.name):
            tasks.append(execute_local_tool(tc.name, tc.arguments))
        else:
            tasks.append(mcp_client.call_tool(tc.name, tc.arguments))
    results = await asyncio.gather(*tasks, return_exceptions=True)
    return {"tool_results": results}
```

**限流保护：**

在 `chat.py` 的请求入口通过 Redis 滑动窗口限流，保护 Agent 不被单一用户打垮：

```python
# 滑动窗口限流伪代码
async def check_rate_limit(user_id: str) -> bool:
    key = f"rate_limit:{user_id}"
    now = time.time()
    window = 60  # 60 秒窗口
    async with redis.pipeline() as pipe:
        pipe.zremrangebyscore(key, 0, now - window)  # 删除窗口外记录
        pipe.zcard(key)                                # 当前窗口内请求数
        pipe.zadd(key, {str(now): now})               # 记录本次请求
        pipe.expire(key, window)
        _, count, _, _ = await pipe.execute()
    return count < RATE_LIMIT_PER_MINUTE  # 默认 20 次/分钟
```


---

## 11. 待对齐项

### 11.1 架构层面

1. **MCP Server transport 模式**：当前设计采用 stdio transport（Agent 内嵌，无额外端口）。如果后续需要 MCP Server 独立部署或对外暴露，需评估切换到 SSE transport。当前以 stdio 为准。

### 11.2 接口层面

2. **B 端 token 解析端点实现**：响应格式已与后端系分 §7.3.0(3) 对齐（返回 userId / account / roles / deptId / doctorId / hospitalId / tokenExpiresAt，信封 {code:"00000", message, data, traceId}）。**Java 端仍需实现 `GET /api/b/auth/token/parse` REST 端点**（可通过 `StpUtil.getLoginIdAsLong()` + `StpUtil.getRoleList()` 轻量实现），当前仅有拦截器级校验。
3. **B 端 API 查询参数**：B 端后端系分已升级至 V1.1，API 路径统一为 `/api/b/admin/*` 和 `/api/b/doctor/*`。工具表参数已与 V1.1 对齐，但部分 API 查询参数规格仍待 B 端后端补充完善。
4. **B 端鉴权头策略**：Agent 调 B 端业务 API 时仅传 `X-User-Id`（§6.4），依赖 Java 端内部鉴权辅助按 userId 加载 b_user 上下文（role / hospitalId / doctorId / deptId）供 DataScope 使用。**待后端确认**该内部鉴权辅助已实现或计划实现。
5. **generate_draft_note 双入口**：B 端前端系分 §6.2 描述医生手动 `PUT /note` 保存病历，Agent 文档设为 L2 工具（`generate_draft_note`）。双入口可共存，但需 B 端前端确认是否处理 `confirm_draft_note` 确认卡片渲染。
6. **hospital_id 前端联动**：C 端工具（`query_departments`、`query_schedule_slots` 等）需要 `hospital_id` 参数，已在 §6.2.1 context 中补充该字段（标注"待前端确认"）。前端系分当前 context 不含 hospital_id，需 C 端前端确认是否在对话请求中传入当前选择的医院 ID。
7. **confirm_appointment 扩展 details**：前端系分列出 6 个扩展字段（remaining_slots / hospital_name / doctor_title / location / lock_expire_seconds / payment_notice），标注"待后端补充"。Agent 系分当前仅含 4 个基础字段，待后端补充后同步。

### 11.3 产品层面

8. **工具清单最终确认**：本文档 C 端 30 个工具参数已与 C 端后端系分 V1.3（55 个 API）对齐，B 端 9 个工具 API 路径已与 B 端后端系分 V1.1（42 个 API）对齐。最终能力集需三方确认。

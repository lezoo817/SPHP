# SPHP Agent 模块 - Claude Code 项目指南

## 项目概述

**智愈先锋(SPHP) Agent 模块** - AI 驱动的全链路医疗健康平台的核心 AI 服务。

### 团队协作说明

**职责边界**:
- Claude Code **主要负责 `sphp-agent/` 模块**(Python FastAPI + LangGraph)
- 其他模块(`sphp-backend/`, `sphp-frontend-c/`, `sphp-frontend-b/`)可参考但**不得擅自修改**
- 跨模块改动需**先与项目负责人确认**

**核心边界**:
| 原则 | 含义 |
|------|------|
| 能查,不能断 | 可查报告指标/药典,但不可确诊、不可开方 |
| 能建议,不能决定 | 可推荐医生/药品,但不可替人挂号、替人下单 |
| 能代办,不能代确认 | 可发起流程、创建草稿,但敏感操作必须人工确认 |

---

## 技术架构

### 四层架构

```
① API接入层 (app/api/)        - 对话端点 + JWT鉴权 + 限流
② 编排层 (app/orchestrator/)  - LangGraph子图 + 节点函数
③ 引擎层 (app/engine/)        - LLM工厂 + 工具Schema + RAG
④ MCP Server (app/mcp_server/) - 工具封装(stdio transport)
⑤ 基础设施层 (app/infrastructure/) - Java客户端 + Redis + 配置
```

**分层规则**: 改动一个 graph 不应触及 LLM 工厂,换数据库驱动不应改动任何 node。

### 核心机制

**对话处理流程**:
```
前端 JWT → 鉴权节点 → 意图识别 → [业务操作/医疗咨询] 
    → [工具调用/RAG检索] → 回复生成 → SSE流式输出
```

**L1-L4 安全验证**:
- L1 查询级: 直接执行
- L2 业务级: 生成 confirm_token → 等待确认
- L3/L4: **代码硬拦截,不注册工具**

**SSE 事件映射**:
| 事件 | 说明 |
|------|------|
| `message` | LLM 正常输出 |
| `action` | 工具调用开始 |
| `observation` | 工具返回结果 |
| `card` | L2 确认卡片 |
| `done` | 本轮完成 |

---

## 开发规范(强制)

### 注释规范

**所有代码必须包含规范注释,不符合要求不予合并。**

**函数注释示例**:
```python
async def auth_node(state: AgentState) -> dict:
    """JWT 鉴权节点(系分 §5.1)。

    从请求中提取 JWT Token,调 Java token/parse 接口校验并获取 userId。
    B 端场景同时写入 roles/dept_id/doctor_id/hospital_id。

    Args:
        state: 当前图状态,包含 scope 字段。

    Returns:
        dict: 部分状态更新,包含 user_id / roles / dept_id 等。

    Raises:
        AgentAuthError: JWT 无效或过期时抛出。
    """
```

**模块注释示例**:
```python
"""对话接口(系分 §6.2)。

前端直连 Agent 对话端点(SSE 流式) + L2 确认回调。

关键设计:
    - SSE 流式输出通过 LangGraph astream_events 实现
    - L2 确认通过 Redis confirm_token 实现
"""
```

### 代码质量

- **函数长度**: ≤ 50 行(超过必须拆分)
- **文件长度**: ≤ 800 行
- **嵌套深度**: ≤ 4 层(使用早返回)
- **类型注解**: 所有函数签名必须标注
- **测试覆盖**: ≥ 80%

### Git 提交规范

**每完成一个小功能(函数/类/模块)必须立即提交。**

```bash
# ✓ 正确: 每个功能点一次提交
git add app/orchestrator/nodes/auth.py
git commit -m "feat(auth): 实现 JWT 鉴权节点"

git add tests/unit/test_auth.py
git commit -m "test(auth): 添加 auth_node 单元测试"

# ✗ 错误: 多个功能一次提交
git add .
git commit -m "feat: 完成对话功能"
```

**提交消息格式**:
```bash
<type>(<scope>): <subject>

# type: feat/fix/refactor/docs/test/chore
# scope: auth/intent/tool/rag (可选)
# subject: 简要描述(≤50字符)
```

**提交前检查**:
- [ ] 所有函数有完整注释
- [ ] 函数 ≤ 50 行,嵌套 ≤ 4 层
- [ ] 已运行 `ruff format` / `ruff check` / `mypy`
- [ ] 测试覆盖率 ≥ 80%
- [ ] 单次提交只包含一个功能点

---

## 接口契约

### 前端 → Agent

```http
POST /api/chat/stream
Authorization: Bearer <JWT>

{
  "content": "我头疼三天了",
  "scope": "c_end",
  "session_id": "sess_abc123"
}
```

**SSE 响应**:
```
event: message
data: {"delta":"建议您挂号呼吸内科"}

event: card
data: {"card_type":"confirm_appointment","confirm_token":"..."}

event: done
data: {"session_id":"sess_abc123"}
```

### Agent → Java

```http
GET /api/c/v1/auth/token/parse  # C端鉴权
GET /api/b/auth/token/parse      # B端鉴权

# 业务 API (MCP tools/call)
Header:
  X-User-Id: {userId}
  X-Idempotency-Key: {uuid4}
```

---

## 配置管理

**关键环境变量**(`.env`):
```bash
JAVA_BASE_URL=http://localhost:8080
ZHIPU_API_KEY=your_key
PG_HOST=localhost
REDIS_HOST=localhost
CONFIRM_TOKEN_TTL=300
RATE_LIMIT_PER_MINUTE=20
```

---

## 关键设计约束

1. **MCP 工具纯数据搬运**: 推理在编排层,MCP Server 只做数据获取
2. **SSE 事件顺序**: `done` 必须在最后
3. **统一信封**: 对外接口统一 `{code, message, data, traceId}`
4. **异步模型**: 所有 I/O 必须异步
5. **医疗安全声明**: 回复末尾强制注入 "AI 建议仅供参考"

---

## Claude Code 职责

### ✅ 可直接操作

- `sphp-agent/` 所有代码
- Agent 模块测试/文档/配置

### ⚠️ 需确认

- 项目根目录配置文件
- 其他模块代码
- 跨模块接口定义

### ❌ 禁止擅自修改

- `sphp-backend/` - Java 后端
- `sphp-frontend-c/` - C 端前端
- `sphp-frontend-b/` - B 端前端
- `docs/全局/`

---

## 开发流程

1. **理解上下文**: 读系分文档和现有代码
2. **明确边界**: 确认改动在 `sphp-agent/` 内
3. **实现功能**: 按节点职责实现
4. **补充注释**: 完整 docstring
5. **编写测试**: 覆盖核心路径
6. **本地提交**: 小功能立即提交
7. **联调验证**: 测试 SSE 流式输出

---

## 关键文件

```
app/main.py                 # FastAPI 入口
app/orchestrator/state.py   # AgentState 状态定义
app/api/routes/chat.py      # 对话端点 + SSE
app/infrastructure/config/  # 配置管理
app/mcp_server/tools/       # MCP 工具封装
```

---

## 参考文档

### Agent模块文档（优先阅读）

- **开发计划**: `docs/Agent/开发计划-V2.md` - 包含完整进度跟踪
- **系分**: `docs/Agent/Agent模块系分.md` (V2.1)

### 全局文档（非必要不读）

⚠️ **重要提示**: `docs/全局/` 目录下的文档是项目全局系分文档，文件量巨大（数百页），**非必要不要阅读**，以免消耗过多token。

如需了解其他模块设计，请：
1. 先询问项目负责人
2. 只阅读与当前任务相关的章节
3. 避免一次性读取完整文档

**全局文档位置**:
- `docs/全局/高SPHP_后端系分.md` - C端后端系分
- `docs/全局/高SPHP-后端系分.md` - B端后端系分
- `docs/全局/高SPHP-前端系分.md` - 前端系分

---

**最后更新**: 2026-08-02
**文档版本**: 基于 Agent 模块系分 V2.1
# SPHP 项目长期记忆

## 项目结构
- `frontend/C/user-h5`：C 端患者 H5（Umi 4 + React + 自定义样式，用 lucide-react 图标）
- `frontend/B/b-sphp`：B 端医院管理后台（Umi Max + Antd 5.x + TypeScript）
- 后端 Java :8080（C 端 `/api/c/v1/*`，B 端 `/api/b/*`）
- Agent Python :8081（`/api/chat/stream` SSE 对话、`/api/chat/confirm` L2 确认）

## B 端关键约定
- token 存储：`localStorage.b_access_token`，无 refreshToken 逻辑，401 直接清除跳 `/login`
- 类型定义：`typings.d.ts` 用 `declare global { namespace API {...} }`，全局可见无需 import
- 请求层：`@umijs/max` 的 request，后端统一返回 `Result<T>`（code/message/data/traceId）
- 构建命令：`max setup` 生成 .umi 类型 → `max build` 构建

## Agent 模块架构（系分 V2.1）
- MCP 协议：Agent 内嵌 MCP Server 封装 Java REST API，编排层作 MCP Client
- 鉴权方案 A：Agent 拿前端 JWT 调 Java token/parse 换 userId，后续用 X-User-Id 头
- SSE 七类事件：message/thought/action/observation/card/error/done
- L2 确认：Redis confirm_token（5min TTL，绑 session_id+user_id），前端 POST /api/chat/confirm 同步返回结果
- scope：c_end / b_end，B 端 9 工具（接诊辅助4+导诊1+处方审核3+报告解读1）
- B 端会话：一次问诊一个会话，切换患者重置

## C 端 agent 模块（参考实现）
- `src/services/agent.ts`、`src/hooks/useAgentStream.ts`、`src/models/agent.ts`
- `src/constants/agent.ts`、`src/typings/agent.ts`
- `src/components/agent/`（AgentChat/Message/Thought/ToolCard/ConfirmCard/FloatingButton）
- `src/pages/agent/index.tsx`

## B 端 agent 模块（2026-08-05 新增）
- 镜像 C 端结构，类型合并到 typings.d.ts 的 namespace Agent
- 用 Antd 5.x 组件替代 C 端自定义样式
- AiPanel 支持 embedded（接诊台侧栏）+ 全屏双形态
- 已集成到 ConsultDetail 三栏布局右栏 + /agent 独立页 + MainLayout 菜单
- 前端用绝对 URL `${AGENT_BASE_URL}/api/chat/stream`（默认 `http://localhost:8081`），不走 Umi proxy——Agent 必须真实监听 8081 + 配置 CORS 允许前端来源

## Agent 故障排查（2026-08-05）
- 现象："网络连接失败，请稍后重试"（NETWORK_ERROR）→ fetch 抛网络层异常
- 排查顺序：
  1. `netstat -ano | findstr :8081` 确认 Agent 是否启动（最常见原因）
  2. `curl http://localhost:8081/health` 看 healthy/degraded
  3. DevTools Network 看请求是 ERR_CONNECTION_REFUSED（没启动）还是 blocked by CORS（启动但无 CORS）
  4. degraded 时看 checks: pg/redis/llm 是否 error
- 修复：启动 Agent（`uvicorn app.main:app --port 8081`）+ FastAPI CORSMiddleware 允许 `localhost:8000`/`8001`
- 备选：把 `AGENT_BASE_URL` 改为空字符串用相对路径，走 `.umirc.ts` 的 `/api/chat → :8081` proxy 转发

## B 端 AI 助手"网络连接失败"真实根因（2026-08-05）
- 用户场景：Agent 已启动（/health 返回 healthy），B 端仍报"网络连接失败"
- 根因：**Agent `.env` 的 `CORS_ORIGINS` 只配了 `["http://localhost:8001"]`（C 端），漏了 B 端 `http://localhost:8000`**
- 验证方法：`curl -i -X POST http://localhost:8081/api/chat/stream -H "Origin: http://localhost:8000" ...` 看响应头有没有 `access-control-allow-origin`，没有就是 CORS 拦截
- 修复：`.env` 改为 `CORS_ORIGINS=["http://localhost:8000", "http://localhost:8001"]`，**重启 Agent**（Pydantic Settings 启动时读 .env，uvicorn --reload 不监听 .env 改动）
- 教训：B 端 dev 默认 :8000，C 端 dev 默认 :8001，两个端口都必须在 Agent CORS 白名单里

## B 端 AI 助手跳转登录页根因（2026-08-05）⚠️关键
- 用户场景：B 端用 AI 助手直接跳登录页（token 被清）
- 根因：**Agent JWT 中间件通过 `X-Scope` 请求头路由 scope（默认 c_end），B 端前端只在 body 传 `scope:'b_end'`，没传 `X-Scope` 头** → Agent 按 c_end 调 C 端 `GET /api/c/v1/auth/token/parse` 校验 B 端 JWT → C/B 端 JWT 密钥不同，校验失败 → Agent 返回 401 AUTH_INVALID → 前端 401 处理清 token 跳登录
- 关键代码：`sphp-agent/app/api/middleware/jwt_auth.py` 第 56 行 `scope = request.headers.get("X-Scope", "c_end")`
- 修复：B 端 `services/agent.ts` 新增 `buildAgentHeaders(token, extra)` 统一注入 `X-Scope: b_end`，所有 5 个 fetch（chatStream/confirmCard/getSessions/deleteSession/getSessionMessages）都走它
- 教训：Agent 的 scope 路由只看请求头 `X-Scope`，**不看 body 里的 scope 字段**；body 的 scope 仅用于编排层工具集选择，不参与鉴权路由。C 端不传 X-Scope 也能工作（默认 c_end），B 端必须显式传 `X-Scope: b_end`

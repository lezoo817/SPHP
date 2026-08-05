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

# 项目长期记忆 —— SPHP C 端 H5

## 项目结构
- 工程目录：`frontend/C/user-h5`，基于 Umi Max 4 + React 18 + TypeScript
- 实际技术栈（与系分文档描述的 zustand/TanStack Query 不同）：plain `request<T>` 服务层 + sessionStorage 模型 + 全局 `.less` + `lucide-react` 图标
- 路由配置在 `.umirc.ts`（非 config/routes.ts，后者为空）
- 五大模块：login / home / assistant / pharmacy / mine / agent（agent 于 2026-08-03 新增）
- Java 后端基础路径 `/api/c/v1`（:8080），Agent 直连 `:8081`

## 编码约定
- 服务层：`src/services/*.ts`，统一 `request<T>(path, options)`，统一响应 `{code,message,data,traceId}`，成功码 `00000`
- 模型层：`src/models/*.ts` 为 sessionStorage 持久化辅助函数（非 zustand store）
- 样式：全局 `src/styles/app.less`，BEM 风格类名，医疗红 `#b33444`、成功绿 `#16875c`
- 鉴权：`src/models/session.ts` 管理 sessionStorage 令牌，`request.ts` 自动刷新 + 401 跳登录
- 页面：`src/pages/<module>/index.tsx`，每个 Tab 页内自行渲染 `BottomTab`

## Agent 模块要点（2026-08-03 新增）
- Agent 服务地址 `http://localhost:8081`，常量在 `src/constants/agent.ts`
- SSE 格式：`event: <name>\ndata: <json>\n\n`，七类事件 message/thought/action/observation/card/error/done
- 后端 observation 用 `success` 布尔，前端在 service 层归一化为 `status: 'success'|'error'`
- 后端 card 当前不下发 details，前端按可选处理
- 悬浮球在 Layout 全局挂载，跳转 `/agent` 全屏页
- 联调需在 sphp-agent 配置 CORS 允许 `localhost:8001`

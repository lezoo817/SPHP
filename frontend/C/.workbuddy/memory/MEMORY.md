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
- SSE 格式：`event: <name>\ndata: <json>\n\n`，八类事件 message/thought/action/observation/card/**options**/error/done
- 后端 observation 用 `success` 布尔，前端在 service 层归一化为 `status: 'success'|'error'`
- 后端 card 当前不下发 details，前端按可选处理
- 悬浮球在 Layout 全局挂载，可拖动、贴边吸附、位置持久化到 localStorage
- 联调需在 sphp-agent 配置 CORS 允许 `localhost:8001`

## Agent 可选项卡片（2026-08-06 options 事件）
- `options` 事件 → 可选项卡片，区别于 L2 确认卡片（"确认一个操作" vs "从列表里选一个"）
- `AgentEntry` 新增 `kind: 'select'`；`AgentSelectCard` 运行时对象含 selectType / items / prompt / replyTemplate / selectedId
- `selectOption(card, item)` hook 暴露方法：标记 selectedId + 构造"我选择{label}"消息调 send()
- `reply_template` 兜底为 `"我选择{label}"`，后端按此模板回填 doctor_id / slot_id 等字段
- 服务层 `parseSseFrame` 新增 `case 'options'`；hook `handleSseEvent` 加对应分支
- 选项类型：`select_doctor` / `select_department` / `select_slot` / `select_pharmacy` / string
- 新组件 `AgentSelectCard`（样式 `.agent-select`）沿用 agent-card 基调，单选点选

## Agent 对话上下文（2026-08-05 补全 address_id）
- `AgentChatContext` 字段：page / hospital_id / patient_id / appointment_id / consultation_id / **address_id**
- `buildAgentContext(pathname)` 同步构造基础字段（路由/选择）
- `resolveAgentContext(pathname)` 异步版：在 `buildAgentContext` 基础上补 `address_id`
- `loadDefaultAddressId()` 调 `getDeliveryAddresses()`，优先 `isDefault`，无默认取首条
- 错误降级：地址接口失败仅缺 `address_id`，其他字段不受影响
- AgentPage 用 `useState + useEffect` 异步加载 context，cancelled 标志防卸载后写状态

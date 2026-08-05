# B 端 Agent 悬浮球实现计划

**日期**：2026-08-05
**作者**：AI 助手
**设计文档**：`docs/superpowers/specs/2026-08-05-b-end-agent-floating-ball-design.md`

---

## 概述

本计划详细说明如何为 B 端前端实现 Agent 悬浮球功能。纯前端实现，不改动后端代码。

**总工期**：5-9 天
**目标**：为医生提供 AI 助手，支持病历查询、诊断辅助、处方审核、药品查询

---

## 前置条件

### 1. 环境准备

- [x] B 端前端项目可正常运行（`frontend/B/b-sphp`）
- [x] Python Agent 可正常运行（端口 8081）
- [x] B 端 Sa-Token 认证正常工作

### 2. 依赖检查

- [ ] 确认 B 端前端已安装 `lucide-react`（图标库）
- [ ] 确认 B 端前端已安装 `react-markdown`（Markdown 渲染，如果 C 端使用）

---

## 阶段一：基础框架（1-2 天）

### 任务 1.1：复制 C 端 Agent 组件到 B 端

**目标**：将 C 端的 Agent 组件完整复制到 B 端

**步骤**：

1. 创建 B 端 Agent 组件目录
   ```bash
   mkdir -p frontend/B/b-sphp/src/components/agent
   ```

2. 复制 C 端组件文件
   ```bash
   # 复制组件
   cp frontend/C/user-h5/src/components/agent/*.tsx frontend/B/b-sphp/src/components/agent/
   
   # 复制 hooks
   cp frontend/C/user-h5/src/hooks/useAgentStream.ts frontend/B/b-sphp/src/hooks/
   
   # 复制 services
   cp frontend/C/user-h5/src/services/agent.ts frontend/B/b-sphp/src/services/
   
   # 复制 typings
   cp frontend/C/user-h5/src/typings/agent.ts frontend/B/b-sphp/src/typings/
   ```

3. 验证文件复制成功
   ```bash
   ls -la frontend/B/b-sphp/src/components/agent/
   ```

**交付物**：
- B 端 Agent 组件目录结构
- 所有组件、hooks、services、typings 文件

---

### 任务 1.2：创建 B 端常量文件

**目标**：创建 `constants/agent.ts`，配置 B 端特有的常量

**步骤**：

1. 创建常量文件
   ```bash
   touch frontend/B/b-sphp/src/constants/agent.ts
   ```

2. 编写常量内容
   ```typescript
   /**
    * B 端 Agent 常量配置
    */
   
   /** Agent 服务基础地址 */
   export const AGENT_BASE_URL =
     (typeof window !== 'undefined' && (window as any).__AGENT_BASE_URL__) || 'http://localhost:8081';
   
   /** B 端对话固定 scope */
   export const AGENT_SCOPE = 'b_end' as const;
   
   /** 用户输入最大长度 */
   export const AGENT_CONTENT_MAX = 2000;
   
   /** 会话存储键名 */
   export const AGENT_SESSION_KEY = 'sphp_b_agent_session_id';
   
   /** 悬浮球位置存储键名 */
   export const AGENT_FLOAT_POS_KEY = 'sphp_b_agent_float_pos';
   
   /** 欢迎语 */
   export const AGENT_WELCOME =
     '您好，我是智愈先锋 AI 助手，可以帮您查询病历、辅助诊断、审核处方和查询药品。请问有什么可以帮您？';
   
   /** 常用咨询快捷入口 */
   export const AGENT_QUICK_PROMPTS: { label: string; content: string }[] = [
     { label: '病历查询', content: '帮我查询患者张三的病历记录。' },
     { label: '诊断建议', content: '患者症状：咳嗽、发热3天，胸片显示肺部阴影，请给出诊断建议。' },
     { label: '处方审核', content: '请审核这张处方的合理性。' },
     { label: '药品查询', content: '查询阿莫西林的用法用量和禁忌症。' },
   ];
   
   /** 工具英文标识符到中文标签的映射 */
   export const AGENT_TOOL_LABELS: Record<string, string> = {
     // B 端特有工具
     generate_draft_note: '生成病历草稿',
     query_patient_history: '查询患者病历',
     check_drug_interaction: '检查药物相互作用',
     check_contraindication: '检查禁忌症',
     check_allergy_risk: '检查过敏风险',
     check_duplicate_medication: '检查重复用药',
     recommend_care: '推荐诊疗方案',
     interpret_report: '解读检查报告',
     
     // 通用查询工具
     query_doctors: '查询医生',
     query_schedule_slots: '查询号源',
     query_departments: '查询科室',
     query_hospitals: '查询医院',
     query_appointments: '查询挂号',
     query_appointment_detail: '查询挂号详情',
     query_consultations: '查询问诊',
     query_consultation_detail: '查询问诊详情',
     query_prescriptions: '查询处方',
     query_prescription_detail: '查询处方详情',
     query_prescription_interpretation: '处方解读',
     query_pharmacy_inventory: '查询药房库存',
     query_drug_orders: '查询购药订单',
     query_drug_order_detail: '查询购药订单详情',
     query_health_record: '查询健康档案',
     query_reports: '查询检查报告',
     query_report_detail: '查询报告详情',
     query_report_interpretation: '报告解读',
     query_medication_plans: '查询用药计划',
     query_follow_ups: '查询随访计划',
     query_notifications: '查询通知',
     query_profile: '查询本人资料',
     query_family_members: '查询家庭成员',
     query_payment: '查询支付状态',
     
     // C 端工具（保留，避免报错）
     create_appointment: '确认挂号',
     cancel_appointment: '确认取消挂号',
     save_pre_consultation: '确认提交预问诊',
     send_consultation_message: '确认发送问诊消息',
     create_drug_order: '确认创建购药订单',
     cancel_drug_order: '确认取消购药订单',
     confirm_drug_receipt: '确认收货',
     manage_allergy: '确认更新过敏史',
     manage_medical_history: '确认更新既往史',
     create_report: '确认录入检查报告',
     update_medication_plan: '确认更新用药计划',
     confirm_follow_up: '确认随访提醒',
     join_waitlist: '确认登记候补',
   };
   
   /** L2 确认卡片错误码到面向医生的提示文案映射 */
   export const AGENT_CONFIRM_ERROR_TEXT: Record<string, string> = {
     CONFIRM_INVALID: '确认参数无效，请重新发起操作',
     CONFIRM_CONSUMED: '已处理，无需重复确认',
     CONFIRM_EXPIRED: '确认已超时，请重新发起操作',
     SESSION_MISMATCH: '会话不匹配，请重新发起操作',
     TOOL_FAILED: '操作执行失败，请稍后重试',
     TOOL_DENIED: '该操作已被安全策略阻止',
   };
   
   /** Agent / SSE 错误码到面向医生提示的映射 */
   export const AGENT_ERROR_TEXT: Record<string, string> = {
     AUTH_MISSING: '未登录，请重新登录',
     AUTH_EXPIRED: '登录已失效，请重新登录',
     AUTH_INVALID: '登录已失效，请重新登录',
     RATE_LIMITED: '对话请求过于频繁，请稍后重试',
     INVALID_REQUEST: '请求参数无效，请修改后重试',
     SESSION_NOT_FOUND: '会话已过期，已为您创建新会话',
     TOOL_DENIED: '该操作暂不支持',
     TOOL_FAILED: '服务暂时不可用，请稍后重试',
     SERVER_ERROR: '服务异常，请稍后重试',
   };
   
   /** 医疗免责声明 */
   export const AGENT_DISCLAIMER = '本结果仅供医疗参考，不替代医生专业判断，请结合临床实际情况使用。';
   ```

3. 验证文件创建成功
   ```bash
   cat frontend/B/b-sphp/src/constants/agent.ts
   ```

**交付物**：
- B 端常量配置文件

---

### 任务 1.3：修改服务层认证方式

**目标**：适配 B 端 Sa-Token 认证

**步骤**：

1. 打开 `frontend/B/b-sphp/src/services/agent.ts`

2. 修改认证函数
   ```typescript
   // 找到 authHeaders 函数，修改为：
   function authHeaders(): Record<string, string> {
     // B 端使用 Sa-Token，从 localStorage 获取
     const token = localStorage.getItem('sphp_b_token') || '';
     return token ? { Authorization: `Bearer ${token}` } : {};
   }
   ```

3. 修改 `ensureAccessToken` 函数
   ```typescript
   async function ensureAccessToken(): Promise<string | null> {
     const token = localStorage.getItem('sphp_b_token');
     if (!token) return null;
     // B 端 Sa-Token 过期处理由后端统一处理，前端只检查是否存在
     return token;
   }
   ```

4. 修改 `redirectToLogin` 调用
   ```typescript
   // 在认证失败时，跳转到 B 端登录页
   function redirectToLogin() {
     localStorage.removeItem('sphp_b_token');
     window.location.href = '/login';
   }
   ```

5. 验证修改正确
   ```bash
   grep -n "authHeaders\|ensureAccessToken\|redirectToLogin" frontend/B/b-sphp/src/services/agent.ts
   ```

**交付物**：
- 适配 B 端认证的服务层

---

### 任务 1.4：修改组件中的常量引用

**目标**：更新组件文件中的常量引用，使用 B 端常量

**步骤**：

1. 修改 `AgentFloatingButton.tsx`
   ```typescript
   // 找到 FLOAT_POS_KEY 常量，修改为：
   import { AGENT_FLOAT_POS_KEY } from '../../constants/agent';
   
   // 将 FLOAT_POS_KEY 替换为 AGENT_FLOAT_POS_KEY
   ```

2. 修改 `AgentChat.tsx`
   ```typescript
   // 修改 import 语句
   import { 
     AGENT_CONTENT_MAX, 
     AGENT_QUICK_PROMPTS, 
     AGENT_WELCOME 
   } from '../../constants/agent';
   ```

3. 修改 `useAgentStream.ts`
   ```typescript
   // 修改 import 语句
   import { AGENT_SESSION_KEY } from '../constants/agent';
   
   // 将所有 'sphp_c_agent_session_id' 替换为 AGENT_SESSION_KEY
   ```

4. 验证所有引用已更新
   ```bash
   grep -rn "sphp_c_agent" frontend/B/b-sphp/src/
   ```

**交付物**：
- 更新常量引用的组件文件

---

### 任务 1.5：验证基础框架

**目标**：确保基础框架可运行

**步骤**：

1. 启动 B 端前端
   ```bash
   cd frontend/B/b-sphp && pnpm dev
   ```

2. 检查是否有编译错误
   - 如果有导入错误，修复路径问题
   - 如果有类型错误，检查 typings 文件

3. 在浏览器中打开 B 端页面，检查控制台是否有错误

4. 验证悬浮球是否显示（暂时不测试功能）

**交付物**：
- 可运行的基础框架（悬浮球显示，但样式可能不对）

---

## 阶段二：样式适配（2-3 天）

### 任务 2.1：调整悬浮球样式

**目标**：将悬浮球从移动端圆形改为桌面端圆角方形

**步骤**：

1. 打开 `frontend/B/b-sphp/src/components/agent/AgentFloatingButton.tsx`

2. 修改默认位置（避开 B 端侧边栏）
   ```typescript
   function defaultPos(): FloatPos {
     const vw = window.innerWidth;
     const vh = window.innerHeight;
     // 假设侧边栏宽度 200px，底部留出空间
     return { x: vw - 100, y: vh - 100 };
   }
   ```

3. 修改按钮尺寸和样式
   ```typescript
   // 在 return 语句中修改 className 和 style
   <button
     type="button"
     className={`b-agent-float${dragging ? ' is-dragging' : ''}`}
     aria-label="打开 AI 助手"
     style={{ 
       left: pos.x, 
       top: pos.y, 
       right: 'auto', 
       bottom: 'auto',
       width: '56px',
       height: '56px',
     }}
     // ... 其他属性
   >
   ```

4. 创建或修改 CSS 文件（如果使用 CSS Modules）
   ```css
   /* styles/agent-float.module.css */
   .b-agent-float {
     position: fixed;
     z-index: 1000;
     display: flex;
     flex-direction: column;
     align-items: center;
     justify-content: center;
     background: #1890ff;
     color: white;
     border: none;
     border-radius: 12px;
     cursor: pointer;
     box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
     transition: transform 0.2s, box-shadow 0.2s;
   }
   
   .b-agent-float:hover {
     transform: scale(1.05);
     box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
   }
   
   .b-agent-float.is-dragging {
     cursor: grabbing;
     transform: scale(1.1);
   }
   
   .b-agent-float__label {
     font-size: 12px;
     margin-top: 4px;
   }
   ```

5. 验证样式效果

**交付物**：
- B 端风格的悬浮球

---

### 任务 2.2：调整聊天面板样式

**目标**：将聊天面板从移动端全屏改为桌面端弹出式

**步骤**：

1. 打开 `frontend/B/b-sphp/src/components/agent/AgentChat.tsx`

2. 修改面板容器样式
   ```typescript
   <div className="b-agent-chat" style={{
     position: 'fixed',
     bottom: '100px',
     right: '20px',
     width: '450px',
     height: '600px',
     zIndex: 1001,
   }}>
   ```

3. 创建或修改 CSS 文件
   ```css
   /* styles/agent-chat.module.css */
   .b-agent-chat {
     display: flex;
     flex-direction: column;
     background: white;
     border-radius: 12px;
     box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
     overflow: hidden;
   }
   
   .b-agent-chat__bar {
     display: flex;
     justify-content: space-between;
     align-items: center;
     padding: 12px 16px;
     background: #fafafa;
     border-bottom: 1px solid #f0f0f0;
   }
   
   .b-agent-chat__list {
     flex: 1;
     overflow-y: auto;
     padding: 16px;
   }
   
   .b-agent-chat__input-bar {
     display: flex;
     padding: 12px 16px;
     border-top: 1px solid #f0f0f0;
     background: #fafafa;
   }
   
   .b-agent-chat__input {
     flex: 1;
     padding: 8px 12px;
     border: 1px solid #d9d9d9;
     border-radius: 6px;
     resize: none;
     font-size: 14px;
   }
   
   .b-agent-chat__send {
     margin-left: 8px;
     padding: 8px 16px;
     background: #1890ff;
     color: white;
     border: none;
     border-radius: 6px;
     cursor: pointer;
   }
   
   .b-agent-chat__send:disabled {
     background: #d9d9d9;
     cursor: not-allowed;
   }
   ```

4. 验证面板样式

**交付物**：
- B 端风格的聊天面板

---

### 任务 2.3：调整消息气泡样式

**目标**：将消息气泡从移动端圆润风格改为桌面端卡片式

**步骤**：

1. 打开 `frontend/B/b-sphp/src/components/agent/AgentMessage.tsx`

2. 修改消息容器样式
   ```typescript
   <div className={`b-agent-message b-agent-message--${message.role}`}>
     <div className="b-agent-message__content">
       {/* 消息内容 */}
     </div>
     <div className="b-agent-message__time">
       {formatTime(message.timestamp)}
     </div>
   </div>
   ```

3. 创建或修改 CSS 文件
   ```css
   /* styles/agent-message.module.css */
   .b-agent-message {
     margin-bottom: 16px;
   }
   
   .b-agent-message--user {
     display: flex;
     justify-content: flex-end;
   }
   
   .b-agent-message--assistant {
     display: flex;
     justify-content: flex-start;
   }
   
   .b-agent-message__content {
     max-width: 80%;
     padding: 12px 16px;
     border-radius: 8px;
     font-size: 14px;
     line-height: 1.5;
   }
   
   .b-agent-message--user .b-agent-message__content {
     background: #1890ff;
     color: white;
   }
   
   .b-agent-message--assistant .b-agent-message__content {
     background: #f5f5f5;
     color: #262626;
   }
   
   .b-agent-message__time {
     font-size: 12px;
     color: #8c8c8c;
     margin-top: 4px;
   }
   ```

4. 验证消息样式

**交付物**：
- B 端风格的消息气泡

---

### 任务 2.4：调整输入框和按钮样式

**目标**：适配 Ant Design 风格

**步骤**：

1. 打开 `frontend/B/b-sphp/src/components/agent/AgentChat.tsx`

2. 修改输入框样式
   ```typescript
   <textarea
     className="b-agent-chat__input"
     placeholder="输入问题，1 至 2000 字"
     value={input}
     onChange={(e) => setInput(e.target.value.slice(0, AGENT_CONTENT_MAX))}
     rows={1}
     disabled={isStreaming}
     onKeyDown={(e) => {
       if (e.key === 'Enter' && !e.shiftKey) {
         e.preventDefault();
         handleSubmit(e);
       }
     }}
   />
   ```

3. 修改发送按钮样式
   ```typescript
   <button 
     type="submit" 
     className="b-agent-chat__send" 
     disabled={isStreaming || !input.trim()}
   >
     <Send size={18} />
   </button>
   ```

4. 验证输入框和按钮样式

**交付物**：
- Ant Design 风格的输入框和按钮

---

### 任务 2.5：适配 B 端主题色

**目标**：确保所有组件使用 B 端主题色

**步骤**：

1. 检查 B 端主题色配置
   ```bash
   grep -rn "primary-color\|#1890ff" frontend/B/b-sphp/
   ```

2. 修改组件中的硬编码颜色
   - 将所有 `#1890ff` 替换为主题变量（如果使用 CSS 变量）
   - 或者保持使用 `#1890ff`（Ant Design 默认主色）

3. 验证颜色一致性

**交付物**：
- 主题色适配完成

---

### 任务 2.6：响应式布局适配

**目标**：确保在不同屏幕尺寸下正常显示

**步骤**：

1. 测试不同屏幕尺寸
   - 1920x1080（全高清）
   - 1366x768（笔记本）
   - 1024x768（最小宽度）

2. 调整悬浮球和面板位置
   ```typescript
   // 在 AgentFloatingButton.tsx 中添加响应式逻辑
   useEffect(() => {
     const handleResize = () => {
       setPos((prev) => clampPos(prev));
     };
     window.addEventListener('resize', handleResize);
     return () => window.removeEventListener('resize', handleResize);
   }, []);
   ```

3. 调整面板宽度（小屏幕时缩小）
   ```typescript
   // 在 AgentChat.tsx 中
   const panelWidth = window.innerWidth < 1200 ? '350px' : '450px';
   ```

4. 验证响应式效果

**交付物**：
- 响应式布局适配完成

---

## 阶段三：功能定制（1-2 天）

### 任务 3.1：修改欢迎语和快捷入口

**目标**：使用 B 端医生相关的欢迎语和快捷入口

**步骤**：

1. 打开 `frontend/B/b-sphp/src/constants/agent.ts`

2. 验证欢迎语已修改
   ```typescript
   export const AGENT_WELCOME = '您好，我是智愈先锋 AI 助手，可以帮您查询病历、辅助诊断、审核处方和查询药品。请问有什么可以帮您？';
   ```

3. 验证快捷入口已修改
   ```typescript
   export const AGENT_QUICK_PROMPTS = [
     { label: '病历查询', content: '帮我查询患者张三的病历记录。' },
     { label: '诊断建议', content: '患者症状：咳嗽、发热3天，胸片显示肺部阴影，请给出诊断建议。' },
     { label: '处方审核', content: '请审核这张处方的合理性。' },
     { label: '药品查询', content: '查询阿莫西林的用法用量和禁忌症。' },
   ];
   ```

4. 在浏览器中验证欢迎语和快捷入口显示正确

**交付物**：
- B 端定制化内容

---

### 任务 3.2：添加 B 端特有工具标签

**目标**：确保 B 端特有工具有正确的中文标签

**步骤**：

1. 打开 `frontend/B/b-sphp/src/constants/agent.ts`

2. 验证工具标签映射已包含 B 端特有工具
   ```typescript
   export const AGENT_TOOL_LABELS = {
     // B 端特有工具
     generate_draft_note: '生成病历草稿',
     query_patient_history: '查询患者病历',
     check_drug_interaction: '检查药物相互作用',
     check_contraindication: '检查禁忌症',
     check_allergy_risk: '检查过敏风险',
     check_duplicate_medication: '检查重复用药',
     recommend_care: '推荐诊疗方案',
     interpret_report: '解读检查报告',
     // ... 其他工具
   };
   ```

3. 在浏览器中触发工具调用，验证标签显示正确

**交付物**：
- B 端工具标签映射完成

---

### 任务 3.3：集成到 B 端全局 Layout

**目标**：将 Agent 悬浮球添加到 B 端全局布局

**步骤**：

1. 打开 `frontend/B/b-sphp/src/layouts/index.tsx`

2. 添加 Agent 组件导入
   ```typescript
   import { useState } from 'react';
   import { AgentFloatingButton } from '@/components/agent/AgentFloatingButton';
   import { AgentChat } from '@/components/agent/AgentChat';
   ```

3. 在 Layout 组件中添加 Agent
   ```typescript
   export default function Layout({ children }) {
     const [showChat, setShowChat] = useState(false);
     const location = useLocation();
     
     // 需要隐藏 Agent 的页面
     const hideAgentPages = ['/login'];
     const shouldHideAgent = hideAgentPages.some(path => 
       location.pathname.startsWith(path)
     );
     
     return (
       <div className="b-layout">
         {/* B 端原有布局 */}
         <div className="b-layout__content">
           {children}
         </div>
         
         {/* Agent 悬浮球 */}
         {!shouldHideAgent && (
           <AgentFloatingButton onClick={() => setShowChat(true)} />
         )}
         
         {/* Agent 聊天面板 */}
         {showChat && (
           <AgentChat 
             context={{ 
               page: 'b_end',
               hospitalId: getHospitalId(), // 从 B 端 session 获取
               doctorId: getDoctorId(),     // 从 B 端 session 获取
             }} 
             onClose={() => setShowChat(false)} 
           />
         )}
       </div>
     );
   }
   ```

4. 添加获取医院和医生 ID 的辅助函数
   ```typescript
   function getHospitalId(): string {
     // 从 B 端 session 获取医院 ID
     return localStorage.getItem('sphp_b_hospital_id') || '';
   }
   
   function getDoctorId(): string {
     // 从 B 端 session 获取医生 ID
     return localStorage.getItem('sphp_b_doctor_id') || '';
   }
   ```

5. 验证悬浮球在所有页面显示（除登录页）

**交付物**：
- Layout 集成完成

---

### 任务 3.4：实现页面隐藏规则

**目标**：在特定页面隐藏悬浮球

**步骤**：

1. 确定需要隐藏悬浮球的页面
   - `/login` - 登录页
   - 其他页面（根据需求）

2. 在 Layout 组件中实现隐藏逻辑
   ```typescript
   const hideAgentPages = ['/login'];
   const shouldHideAgent = hideAgentPages.some(path => 
     location.pathname.startsWith(path)
   );
   ```

3. 验证隐藏规则生效

**交付物**：
- 页面隐藏规则实现完成

---

## 阶段四：测试与优化（1-2 天）

### 任务 4.1：功能测试

**目标**：测试所有核心功能

**测试用例**：

#### 悬浮球测试
- [ ] 悬浮球显示
- [ ] 悬浮球拖拽
- [ ] 悬浮球位置持久化
- [ ] 悬浮球点击打开面板

#### 聊天面板测试
- [ ] 面板打开/关闭
- [ ] 消息发送
- [ ] 消息接收（SSE 流式）
- [ ] 历史会话加载
- [ ] 新建会话
- [ ] 删除会话

#### 认证测试
- [ ] Token 有效时正常对话
- [ ] Token 过期时跳转登录
- [ ] 未登录时跳转登录

#### 工具调用测试
- [ ] L2 确认卡片显示
- [ ] 确认操作成功
- [ ] 取消操作成功
- [ ] 确认失败处理

**交付物**：
- 功能测试报告

---

### 任务 4.2：兼容性测试

**目标**：测试不同浏览器和屏幕尺寸

**测试矩阵**：

| 浏览器 | 版本 | 测试结果 |
|--------|------|----------|
| Chrome | 90+ | ✅/❌ |
| Firefox | 88+ | ✅/❌ |
| Safari | 14+ | ✅/❌ |
| Edge | 90+ | ✅/❌ |

| 屏幕尺寸 | 分辨率 | 测试结果 |
|----------|--------|----------|
| 全高清 | 1920x1080 | ✅/❌ |
| 笔记本 | 1366x768 | ✅/❌ |
| 最小宽度 | 1024x768 | ✅/❌ |

**交付物**：
- 兼容性测试报告

---

### 任务 4.3：性能测试

**目标**：测试性能表现

**测试点**：

#### 悬浮球性能
- [ ] 拖拽流畅度（60fps）
- [ ] 内存占用

#### 聊天面板性能
- [ ] 消息列表滚动（100+ 消息）
- [ ] SSE 连接稳定性（长时间对话）
- [ ] 内存泄漏检测

**测试方法**：
- 使用 Chrome DevTools Performance 面板
- 使用 Chrome DevTools Memory 面板

**交付物**：
- 性能测试报告

---

### 任务 4.4：Bug 修复

**目标**：修复测试中发现的 Bug

**步骤**：

1. 收集测试中的问题
2. 按优先级排序（P0 > P1 > P2）
3. 逐一修复
4. 回归测试

**交付物**：
- Bug 修复记录

---

### 任务 4.5：用户体验优化

**目标**：优化用户体验

**优化点**：

#### 交互优化
- [ ] 悬浮球 hover 效果
- [ ] 面板打开/关闭动画
- [ ] 消息发送反馈

#### 视觉优化
- [ ] 加载状态显示
- [ ] 错误状态显示
- [ ] 空状态显示

#### 引导优化
- [ ] 新用户使用引导
- [ ] 快捷入口提示

**交付物**：
- 优化后的 UI/UX

---

## 总工期估算

| 阶段 | 任务 | 工期 | 累计 |
|------|------|------|------|
| 阶段一 | 基础框架 | 1-2 天 | 1-2 天 |
| 阶段二 | 样式适配 | 2-3 天 | 3-5 天 |
| 阶段三 | 功能定制 | 1-2 天 | 4-7 天 |
| 阶段四 | 测试优化 | 1-2 天 | 5-9 天 |

**里程碑**：
- 第 2 天：基础框架完成，可运行
- 第 5 天：样式适配完成，可演示
- 第 7 天：功能定制完成，可测试
- 第 9 天：测试通过，可上线

---

## 风险与缓解

### 技术风险

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| B 端认证方式与 C 端不同 | 认证失败 | 提前调研 Sa-Token 存储方式 |
| 样式与 B 端主题不一致 | 用户体验差 | 参考 B 端现有组件样式 |
| 性能问题（大量消息） | 卡顿 | 虚拟列表优化 |

### 业务风险

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 医生不习惯使用 AI 助手 | 使用率低 | 提供使用引导和培训 |
| B 端功能不够丰富 | 价值不足 | 优先实现核心功能，逐步迭代 |

---

## 后续迭代

### 功能扩展

- **语音输入**：支持语音输入病历描述
- **图片识别**：支持识别检查报告图片
- **智能提醒**：基于患者数据主动提醒
- **多轮对话**：支持复杂诊断场景

### 技术优化

- **虚拟列表**：优化大量消息的渲染性能
- **离线缓存**：支持离线查看历史会话
- **推送通知**：异步任务完成通知

---

## 附录

### 参考文档

- 设计文档：`docs/superpowers/specs/2026-08-05-b-end-agent-floating-ball-design.md`
- C 端 Agent 实现：`frontend/C/user-h5/src/components/agent/`
- Python Agent API：`sphp-agent/app/api/`
- B 端工具定义：`sphp-agent/app/engine/tools/b_schemas.py`

### 常用命令

```bash
# 启动 B 端前端
cd frontend/B/b-sphp && pnpm dev

# 启动 Python Agent
cd sphp-agent && uvicorn app.main:app --port 8081

# 启动 Java 后端
cd sphp-server && mvn spring-boot:run -pl sphp-bootstrap
```

---

**计划版本**：v1.0
**最后更新**：2026-08-05

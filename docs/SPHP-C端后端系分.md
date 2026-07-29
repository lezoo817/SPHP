# 智愈先锋 C 端传统业务后端系统分析与设计

| 项目 | 内容 |
| --- | --- |
| 产品名称 | 智愈先锋 AI 驱动医疗健康平台 |
| 文档范围 | H5 C 端传统业务，不包含 Agent 对话、工具调用、确认编排与审计接口 |
| 文档版本 | V1.0 |
| 技术基线 | Java 21、Spring Boot、PostgreSQL、Redis |
| 编写日期 | 2026-07-28 |

## 1. 变更记录

| 日期 | 版本 | 修订说明 | 作者 |
| --- | --- | --- | --- |
| 2026-07-28 | V1.0 | 初版：C 端传统业务系统分析与接口设计 | 后端团队 |

## 2. 项目背景与边界

患者通过 H5 完成账号注册登录、手动挂号、文字问诊、处方购药和健康管理。C 端账号系统与医院 B 端账号系统完全独立：两端不共享用户表、角色、JWT 签发密钥、刷新令牌或权限。

本期 MVP 必做：认证、科室/医生/号源查询、挂号锁号、防超卖、模拟支付和挂号订单。在线问诊、购药、档案、提醒和随访按完整闭环设计。所有导诊、报告和处方解读仅展示健康说明，不得形成诊断结论、修改处方或替代执业医生。

### 2.1 功能模块树

```text
C 端传统业务
├── 认证与个人资料（MVP）
│   ├── 图形验证码、注册、登录、刷新令牌、退出
│   └── 个人资料、密码修改
├── 手动挂号（MVP）
│   ├── 科室、医生、号源查询
│   ├── 锁号、取消、候补、模拟支付
│   └── 订单与支付状态查询
├── 在线问诊（扩展）
│   ├── 预问诊、文字消息、问诊记录
│   └── 处方与处方解读
├── 处方购药（扩展）
│   ├── 药店库存查询、购药草稿、模拟支付
│   └── 药品订单查询
└── 健康管理（扩展）
    ├── 档案、过敏史、既往史、检查报告
    ├── 用药计划、随访计划
    └── 通知
```

### 2.2 核心业务流程

```mermaid
flowchart TD
    A[账号密码登录] --> B[查询科室、医生与号源]
    B --> C[创建挂号锁定订单]
    C -->|锁定成功| D[15 分钟内输入登录密码模拟支付]
    C -->|余量不足| E[推荐其他号源或登记候补]
    D -->|支付成功| F[挂号成功]
    D -->|超时或取消| G[释放号源]
    F --> H[预问诊与文字问诊]
    H --> I[医生处方]
    I --> J[查询药店库存并创建购药订单]
    J --> K[支付、用药提醒与随访]
```

## 3. 全局接口约定

### 3.1 基础约定

- 基础路径：`/api/c/v1`。
- 除验证码、注册、登录和刷新令牌外，所有接口携带 `Authorization: Bearer {accessToken}`。
- 所有创建型接口携带 `X-Idempotency-Key`，服务端以“用户 ID + 接口路径 + 幂等键”在 Redis 保存 24 小时结果。
- 时间使用 ISO-8601、东八区，例如 `2026-07-28T09:00:00+08:00`；金额使用分的整数 `amountCent`。
- HTTP 状态码仅表达协议处理结果：参数校验通常使用 `400`，未认证 `401`，无权限 `403`，资源不存在 `404`，状态冲突/库存不足 `409`，限流 `429`。业务码始终按本节五位编码返回，后二者不存在编号映射关系。

### 3.2 统一响应格式

```json
{
    "code":  "00000",
    "message":  "操作成功",
    "data":  {

             },
    "traceId":  "01J7X..."
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | string | 五位字符串业务码；成功固定为 `00000` |
| message | string | 面向调用方的简要消息 |
| data | object | 当前接口的业务数据，失败时为 `null` |
| traceId | string | 请求追踪 ID |

列表 `data` 固定为：

```json
{
    "pageNo":  1,
    "pageSize":  20,
    "total":  100,
    "records":  [

                ]
}
```

### 3.3 业务码规范

参考阿里Java开发手册，业务码为字符串类型，共五位：首位表示错误来源，后四位为编号。编号按大类预留 100 的间隔；后三位与 HTTP 状态码无关。

| 代码 | 中文描述 | 来源 | 使用场景 |
| --- | --- | --- | --- |
| `00000` | 一切正常 | 成功 | 请求正确执行后的返回 |
| `A0111` | 账号已存在 | 用户 | 注册账号重复 |
| `A0120` | 密码校验失败 | 用户 | 登录、修改密码或模拟支付密码不正确/不合规 |
| `A0203` | 账号已停用 | 用户 | 已停用账号尝试登录 |
| `A0210` | 用户登录失败 | 用户 | 账号不存在或登录密码错误 |
| `A0211` | 用户输入密码错误次数超限 | 用户 | 登录失败次数超过限制 |
| `A0230` | 用户登录已过期 | 用户 | 刷新令牌已过期 |
| `A0240` | 用户验证码错误 | 用户 | 图形验证码错误、过期或已使用 |
| `A0241` | 用户验证码尝试次数超限 | 用户 | 验证码请求或校验超过频控阈值 |
| `A0301` | 访问未授权 | 用户 | 缺少有效令牌或访问非本人资源 |
| `A0341` | 用户签名异常 | 用户 | 支付渠道回调签名不匹配 |
| `A0400` | 用户请求参数错误 | 用户 | 参数格式、必填项或资料校验失败 |
| `A0402` | 无效的用户输入 | 用户 | 路径或查询中指定的业务资源不存在 |
| `A0420` | 请求参数值超出允许范围 | 用户 | 日期、分页大小或枚举值超出允许范围 |
| `A0430` | 用户输入内容非法 | 用户 | 症状、消息等内容为空、超长或不合规 |
| `A0441` | 用户支付超时 | 用户 | 超过 15 分钟支付窗口 |
| `A0443` | 订单已关闭或状态不可操作 | 用户 | 已关闭订单、未支付挂号或不允许的状态转换 |
| `A0501` | 请求次数超出限制 | 用户 | 用户访问超过频率限制 |
| `A0506` | 用户重复请求 | 用户 | 幂等键冲突、重复挂号、重复候补或重复创建问诊 |
| `B0001` | 系统执行出错 | 系统 | 系统内部未预期异常 |
| `B0201` | 系统高并发库存竞争 | 系统 | 抢号、锁号时库存已被并发请求占用 |
| `B0202` | 系统业务状态冲突 | 系统 | 解读尚未生成、资源状态冲突等业务处理失败 |
| `B0300` | 系统资源或库存不足 | 系统 | 药品库存不足 |
| `C0001` | 调用第三方服务出错 | 第三方 | 第三方支付、消息或外部服务调用失败 |
| `C0200` | 第三方系统执行超时 | 第三方 | 支付、消息等第三方服务超时 |
| `C0500` | 通知服务出错 | 第三方 | 短信、站内通知等投递失败 |

### 3.4 核心状态

| 领域 | 状态 |
| --- | --- |
| 挂号订单 | `LOCKED`、`PAID`、`CANCELLED`、`EXPIRED` |
| 购药订单 | `DRAFT`、`PENDING_PAYMENT`、`PAID`、`CANCELLED`、`EXPIRED` |
| 支付单 | `PENDING`、`SUCCESS`、`FAILED`、`CLOSED` |
| 问诊 | `PENDING`、`IN_PROGRESS`、`COMPLETED` |
| 提醒 | `ACTIVE`、`PAUSED`、`COMPLETED` |
| 随访 | `PENDING_CONFIRM`、`CONFIRMED`、`COMPLETED`、`CANCELLED` |

## 4. 核心领域模型

```plantuml
@startuml
hide methods
class CUser { +id: bigint\n+account: varchar\n+passwordHash: varchar\n+status: varchar }
class PatientProfile { +id: bigint\n+userId: bigint\n+name: varchar\n+idCardNo: varchar }
class ScheduleSlot { +id: bigint\n+doctorId: bigint\n+startTime: timestamptz\n+totalCount: int\n+availableCount: int }
class AppointmentOrder { +id: bigint\n+patientId: bigint\n+slotId: bigint\n+status: varchar\n+expireAt: timestamptz }
class Consultation { +id: bigint\n+appointmentId: bigint\n+status: varchar }
class Prescription { +id: bigint\n+consultationId: bigint\n+status: varchar }
class DrugOrder { +id: bigint\n+patientId: bigint\n+status: varchar }
class PaymentOrder { +id: bigint\n+businessType: varchar\n+businessId: bigint\n+status: varchar }
CUser "1" *-- "1" PatientProfile
PatientProfile "1" -- "*" AppointmentOrder
ScheduleSlot "1" -- "*" AppointmentOrder
AppointmentOrder "1" -- "0..1" Consultation
Consultation "1" -- "0..*" Prescription
PatientProfile "1" -- "*" DrugOrder
AppointmentOrder "1" -- "1" PaymentOrder
DrugOrder "1" -- "1" PaymentOrder
@enduml
```

## 5. API 设计

每个接口的流程图和 PlantUML 时序图后均紧跟 `RESTFUL API设计`，用于定义请求参数、格式化响应示例和错误码。

### 5.1 认证与个人资料（MVP）

#### 5.1.1 获取图形验证码

```mermaid
flowchart LR
    A[H5 请求验证码] --> B[认证服务生成随机码]
    B --> C[Redis 保存 challengeId 与哈希]
    C --> D[返回 SVG/图片数据]
```

```plantuml
@startuml
participant H5
participant AuthService
database Redis
H5 -> AuthService: GET /auth/captcha
AuthService -> Redis: SET captcha:challengeId hash EX 120
AuthService --> H5: challengeId、imageBase64、expireSeconds
@enduml
```

##### RESTFUL API设计

**接口路径：** GET /auth/captcha

**接口信息：** 无需鉴权；用于注册前取得一次性图形验证码。

**请求参数：** 无。

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "获取验证码成功",
    "data":  {
                 "challengeId":  "cap_01J7X8A2",
                 "imageBase64":  "data:image/svg+xml;base64,PHN2Zy...",
                 "expireSeconds":  120
             },
    "traceId":  "01J7X8B3"
}
```

#### 5.1.2 注册 C 端账号

```mermaid
flowchart LR
    A[提交账号、密码、验证码] --> B[校验验证码]
    B --> C{账号未占用}
    C -->|是| D[哈希密码并创建 C 用户]
    C -->|否| E[返回账号已存在]
```

```plantuml
@startuml
participant H5
participant AuthService
database Redis
database PostgreSQL
H5 -> AuthService: POST /auth/register
AuthService -> Redis: 校验并删除 captcha
AuthService -> PostgreSQL: 查询 c_user.account
AuthService -> PostgreSQL: 写入 c_user、patient_profile
AuthService --> H5: 注册成功
@enduml
```

##### RESTFUL API设计

**接口路径：** POST /auth/register



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| account | Body | String | 是 | C 端唯一登录账号，4 至 32 位 | `patient_zhangsan` |
| password | Body | String | 是 | 登录密码，8 至 64 位，仅通过 TLS 传输 | `P@ssw0rd123` |
| challengeId | Body | String | 是 | 获取图形验证码时返回的挑战标识 | `cap_01J7X8A2` |
| captchaCode | Body | String | 是 | 用户填写的图形验证码 | `A7K9` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "注册成功",
    "data":  {
                 "userId":  10001,
                 "account":  "patient_zhangsan"
             },
    "traceId":  "01J7X8C4"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0240 | 400 | 图形验证码无效 | 验证码错误、过期或已使用 |
| A0111 | 409 | 登录账号已存在 | `account` 与已有 C 端账号重复 |
| A0120 | 400 | 密码格式不合法 | 密码长度或复杂度不满足规则 |

#### 5.1.3 账号密码登录

```mermaid
flowchart LR
    A[提交账号密码] --> B[查询 C 端用户]
    B --> C[校验 BCrypt]
    C --> D[签发 C 端 access/refresh Token]
```

```plantuml
@startuml
participant H5
participant AuthService
database PostgreSQL
database Redis
H5 -> AuthService: POST /auth/login
AuthService -> PostgreSQL: 查询 c_user
AuthService -> Redis: 保存 refreshToken 会话
AuthService --> H5: accessToken、refreshToken、expiresIn
@enduml
```

##### RESTFUL API设计

**接口路径：** POST /auth/login



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| account | Body | String | 是 | C 端登录账号 | `patient_zhangsan` |
| password | Body | String | 是 | 登录密码 | `P@ssw0rd123` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "登录成功",
    "data":  {
                 "accessToken":  "eyJhbGciOiJIUzI1NiJ9...",
                 "refreshToken":  "rt_01J7X8D5...",
                 "expiresIn":  7200,
                 "user":  {
                              "id":  10001,
                              "account":  "patient_zhangsan"
                          }
             },
    "traceId":  "01J7X8E6"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0210 | 401 | 账号或密码错误 | 账号不存在或 BCrypt 密码校验失败 |
| A0203 | 403 | 账号已被停用 | `c_user.status` 为 `DISABLED` |
| A0211 | 429 | 登录暂时锁定 | 连续失败 5 次后的 15 分钟锁定期内 |

#### 5.1.4 刷新访问令牌

```mermaid
flowchart LR
    A[提交 refreshToken] --> B[验证 C 端 Token 会话] --> C[轮换 Token] --> D[返回新 Token]
```

```plantuml
@startuml
participant H5
participant AuthService
database Redis
H5 -> AuthService: POST /auth/token/refresh
AuthService -> Redis: 校验旧 refreshToken 并轮换
AuthService --> H5: 新 accessToken、新 refreshToken
@enduml
```

##### RESTFUL API设计

**接口路径：** POST /auth/token/refresh



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| refreshToken | Body | String | 是 | 登录成功后取得的 C 端刷新令牌 | `rt_01J7X8D5...` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "令牌刷新成功",
    "data":  {
                 "accessToken":  "eyJhbGciOiJIUzI1NiJ9.new...",
                 "refreshToken":  "rt_01J7X9F7...",
                 "expiresIn":  7200
             },
    "traceId":  "01J7X9G8"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0301 | 401 | 刷新令牌无效 | 令牌格式不正确、签名错误或已撤销 |
| A0230 | 401 | 刷新令牌已过期 | 超过刷新令牌有效期 |

#### 5.1.5 退出登录

```mermaid
flowchart LR
    A[H5 退出] --> B[认证服务撤销刷新会话] --> C[返回成功]
```

```plantuml
@startuml
participant H5
participant AuthService
database Redis
H5 -> AuthService: POST /auth/logout
AuthService -> Redis: 删除当前 refreshToken 会话
AuthService --> H5: 成功
@enduml
```

##### RESTFUL API设计

**接口路径：** POST /auth/logout



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| refreshToken | Body | String | 是 | 需要撤销的刷新令牌 | `rt_01J7X9F7...` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "退出登录成功",
    "data":  {
                 "loggedOut":  true
             },
    "traceId":  "01J7X9H9"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0301 | 401 | 当前会话无效 | accessToken 或 refreshToken 已失效 |

#### 5.1.6 查询和更新个人资料

```mermaid
flowchart LR
    A[H5 查询或提交资料] --> B[校验当前用户] --> C[读取或更新本人档案] --> D[返回资料]
```

```plantuml
@startuml
participant H5
participant ProfileService
database PostgreSQL
H5 -> ProfileService: GET/PUT /profile
ProfileService -> PostgreSQL: 按 token.userId 查询或更新 patient_profile
ProfileService --> H5: 本人资料
@enduml
```

##### RESTFUL API设计

###### 查询

**接口路径：** GET /profile

**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |

**响应示例：**

```json
{
    "code":  "00000",
    "message":  "查询成功",
    "data":  {
                 "id":  20001,
                 "name":  "张三",
                 "gender":  "MALE",
                 "birthday":  "1990-05-20",
                 "phone":  "138****8000",
                 "emergencyContact":  "李四 139****9000"
             },
    "traceId":  "01J7X9J0"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0301 | 401 | 未登录或令牌失效 | 未携带有效 C 端 accessToken |

###### 更新

**接口路径：** PUT /profile

**请求参数：**

| 参数             | 位置   | 类型   | 必填 | 描述              | 示例/默认值        |
| ---------------- | ------ | ------ | ---- | ----------------- | ------------------ |
| Authorization    | Header | String | 是   | 当前 C 端访问令牌 | `Bearer eyJ...`    |
| name             | Body   | String | 是   | 患者姓名          | `张三`             |
| gender           | Body   | String | 否   | 性别枚举          | `MALE`             |
| birthday         | Body   | Date   | 否   | 出生日期          | `1990-05-20`       |
| phone            | Body   | String | 否   | 联系电话          | `13800138000`      |
| emergencyContact | Body   | String | 否   | 紧急联系人及电话  | `李四 13900139000` |

**响应示例：**

```json
{
    "code":  "00000",
    "message":  "个人资料已更新",
    "data":  {
                 "id":  20001,
                 "name":  "张三",
                 "phone":  "138****8000",
                 "updatedAt":  "2026-07-28T10:00:00+08:00"
             },
    "traceId":  "01J7X9K1"
}
```

| 错误码 | HTTP 状态 | 含义             | 触发场景                       |
| ------ | --------- | ---------------- | ------------------------------ |
| A0400  | 400       | 资料字段校验失败 | 姓名、日期或联系方式格式不合法 |
| A0301  | 401       | 未登录或令牌失效 | 未携带有效 C 端 accessToken    |



#### 5.1.7 修改登录密码

```mermaid
flowchart LR
    A[提交旧密码和新密码] --> B[校验旧密码] --> C[更新 BCrypt 哈希] --> D[撤销其他会话]
```

```plantuml
@startuml
participant H5
participant AuthService
database PostgreSQL
database Redis
H5 -> AuthService: PUT /auth/password
AuthService -> PostgreSQL: 校验并更新 password_hash
AuthService -> Redis: 撤销该用户其他 refreshToken
AuthService --> H5: passwordChanged=true
@enduml
```

##### RESTFUL API设计

**接口路径：** PUT /auth/password



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| oldPassword | Body | String | 是 | 当前登录密码 | `P@ssw0rd123` |
| newPassword | Body | String | 是 | 新登录密码，8 至 64 位 | `NewP@ssw0rd123` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "密码修改成功",
    "data":  {
                 "passwordChanged":  true
             },
    "traceId":  "01J7X9L2"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0120 | 400 | 当前密码不正确 | `oldPassword` 不能通过 BCrypt 校验 |
| A0120 | 400 | 新密码格式不合法 | 新密码不符合长度或复杂度规则 |

### 5.2 挂号资源查询与导诊（MVP）

#### 5.2.1 查询科室

```mermaid
flowchart LR
    A[H5 查询科室] --> B[读取启用科室] --> C[返回扁平列表]
```

```plantuml
@startuml
participant H5
participant DepartmentService
database PostgreSQL
H5 -> DepartmentService: GET /departments
DepartmentService -> PostgreSQL: 查询 enabled department
DepartmentService --> H5: 科室列表
@enduml
```

##### RESTFUL API设计

**接口路径：** GET /departments



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| keyword | Query | String | 否 | 按科室名称模糊搜索 | `呼吸` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "查询成功",
    "data":  [
                 {
                     "id":  301,
                     "name":  "呼吸内科",
                     "description":  "呼吸系统疾病诊疗"
                 }
             ],
    "traceId":  "01J7X9M3"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0301 | 401 | 未登录或令牌失效 | 未携带有效 C 端 accessToken |

#### 5.2.2 查询医生

```mermaid
flowchart LR
    A[H5 按科室筛选] --> B[过滤在岗医生] --> C[返回医生与号源概览]
```

```plantuml
@startuml
participant H5
participant DoctorService
database PostgreSQL
H5 -> DoctorService: GET /doctors
DoctorService -> PostgreSQL: 查询启用医生与近期余量
DoctorService --> H5: 分页医生列表
@enduml
```

##### RESTFUL API设计

**接口路径：** GET /doctors



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| departmentId | Query | Long | 是 | 需查询的科室 ID | `301` |
| date | Query | Date | 否 | 用于聚合号源余量的出诊日期 | `2026-07-30` |
| pageNo | Query | Integer | 否 | 页码，从 1 开始 | `1` |
| pageSize | Query | Integer | 否 | 每页条数，默认 20，最大 100 | `20` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "查询成功",
    "data":  {
                 "pageNo":  1,
                 "pageSize":  20,
                 "total":  1,
                 "records":  [
                                 {
                                     "id":  401,
                                     "name":  "王医生",
                                     "title":  "主任医师",
                                     "specialty":  "哮喘、慢阻肺",
                                     "registrationFeeCent":  5000,
                                     "availableCount":  6
                                 }
                             ]
             },
    "traceId":  "01J7X9N4"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 科室不存在或已停用 | `departmentId` 不可用于 C 端查询 |
| A0420 | 400 | 分页大小不合法 | `pageSize` 小于 1 或大于 100 |

#### 5.2.3 查询医生可预约时段

```mermaid
flowchart LR
    A[H5 选择医生和日期] --> B[查询已发布时段] --> C[返回实时余量]
```

```plantuml
@startuml
participant H5
participant ScheduleService
database Redis
database PostgreSQL
H5 -> ScheduleService: GET /doctors/{doctorId}/slots
ScheduleService -> Redis: 获取可用余量
ScheduleService -> PostgreSQL: 补充排班资料
ScheduleService --> H5: 时段及 availableCount
@enduml
```

##### RESTFUL API设计

**接口路径：** GET /doctors/{doctorId}/slots



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| doctorId | Path | Long | 是 | 医生 ID | `401` |
| date | Query | Date | 是 | 查询指定日期的出诊时段 | `2026-07-30` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "查询成功",
    "data":  [
                 {
                     "slotId":  501,
                     "startTime":  "2026-07-30T09:00:00+08:00",
                     "endTime":  "2026-07-30T09:30:00+08:00",
                     "feeCent":  5000,
                     "availableCount":  6,
                     "status":  "OPEN"
                 }
             ],
    "traceId":  "01J7X9P5"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 医生不存在或不可预约 | `doctorId` 未找到或医生已停诊 |
| A0420 | 400 | 查询日期不合法 | 日期为空、已过期或超出放号周期 |

#### 5.2.4 提交症状获取导诊建议

```mermaid
flowchart LR
    A[提交症状] --> B[规则库匹配风险与科室] --> C[返回非诊断性建议]
```

```plantuml
@startuml
participant H5
participant TriageService
database PostgreSQL
H5 -> TriageService: POST /triage/assessments
TriageService -> PostgreSQL: 读取导诊规则与科室
TriageService --> H5: 推荐科室、风险提示
@enduml
```

##### RESTFUL API设计

**接口路径：** POST /triage/assessments



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| symptom | Body | String | 是 | 患者当前症状描述，1 至 1000 字 | `咳嗽发热三天` |
| duration | Body | String | 否 | 症状持续时间 | `3天` |
| temperature | Body | Number | 否 | 最近一次体温，单位摄氏度 | `38.5` |
| medicalHistory | Body | String | 否 | 与本次症状相关的既往史 | `无慢性肺病` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "导诊评估完成",
    "data":  {
                 "assessmentId":  601,
                 "urgency":  "MEDIUM",
                 "recommendedDepartments":  [
                                                {
                                                    "id":  301,
                                                    "name":  "呼吸内科",
                                                    "reason":  "症状与呼吸系统相关"
                                                }
                                            ],
                 "disclaimer":  "本结果仅供健康咨询参考，不替代医生诊断。"
             },
    "traceId":  "01J7X9Q6"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0430 | 400 | 症状内容不合法 | 症状为空、超长或包含不允许内容 |
| A0301 | 401 | 未登录或令牌失效 | 未携带有效 C 端 accessToken |

### 5.3 挂号订单与模拟支付（MVP）

#### 5.3.1 创建挂号锁定订单

```mermaid
flowchart LR
    A[提交 slotId] --> B[幂等校验] --> C{Redis 原子预扣成功}
    C -->|是| D[事务创建锁定订单与支付单]
    C -->|否| E[返回号源不足]
```

```plantuml
@startuml
participant H5
participant AppointmentService
database Redis
database PostgreSQL
H5 -> AppointmentService: POST /appointments
AppointmentService -> Redis: Lua 原子扣减 slot 库存
alt 预扣成功
  AppointmentService -> PostgreSQL: 创建 LOCKED 订单和 PENDING 支付单
  AppointmentService --> H5: 订单、15 分钟到期时间
else 预扣失败
  AppointmentService --> H5: B0201
end
@enduml
```

##### RESTFUL API设计

**接口路径：** POST /appointments



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| X-Idempotency-Key | Header | String | 是 | 防止重复锁号的客户端唯一请求键 | `uuid-v7` |
| slotId | Body | Long | 是 | 要预约的医生时段 ID | `501` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "号源锁定成功，请在15分钟内完成支付",
    "data":  {
                 "appointmentId":  7001,
                 "status":  "LOCKED",
                 "amountCent":  5000,
                 "expireAt":  "2026-07-28T10:15:00+08:00",
                 "paymentId":  8001
             },
    "traceId":  "01J7X9R7"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| B0201 | 409 | 号源已约满 | Redis 原子预扣时余量不足 |
| A0443 | 409 | 当前时段不可预约 | 时段停诊、未开放或已过期 |
| A0506 | 409 | 存在冲突的有效挂号 | 当前用户已预约同一时段 |
| A0506 | 409 | 幂等键参数不一致 | 同一幂等键对应不同业务请求 |

#### 5.3.2 查询挂号订单列表

```mermaid
flowchart LR
    A[H5 查询本人订单] --> B[按 patientId 分页] --> C[返回列表]
```

```plantuml
@startuml
participant H5
participant AppointmentService
database PostgreSQL
H5 -> AppointmentService: GET /appointments
AppointmentService -> PostgreSQL: 查询本人订单
AppointmentService --> H5: 分页订单
@enduml
```

##### RESTFUL API设计

**接口路径：** GET /appointments



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| status | Query | String | 否 | 按订单状态筛选 | `PAID` |
| pageNo | Query | Integer | 否 | 页码，从 1 开始 | `1` |
| pageSize | Query | Integer | 否 | 每页条数 | `20` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "查询成功",
    "data":  {
                 "pageNo":  1,
                 "pageSize":  20,
                 "total":  1,
                 "records":  [
                                 {
                                     "id":  7001,
                                     "doctorName":  "王医生",
                                     "departmentName":  "呼吸内科",
                                     "startTime":  "2026-07-30T09:00:00+08:00",
                                     "status":  "PAID",
                                     "amountCent":  5000,
                                     "expireAt":  null
                                 }
                             ]
             },
    "traceId":  "01J7X9S8"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0420 | 400 | 分页大小不合法 | `pageSize` 小于 1 或大于 100 |

#### 5.3.3 查询挂号订单详情

```mermaid
flowchart LR
    A[H5 请求详情] --> B[校验订单归属] --> C[返回订单及支付信息]
```

```plantuml
@startuml
participant H5
participant AppointmentService
database PostgreSQL
H5 -> AppointmentService: GET /appointments/{appointmentId}
AppointmentService -> PostgreSQL: 查询订单且 patient_id=当前患者
AppointmentService --> H5: 订单详情
@enduml
```

##### RESTFUL API设计

**接口路径：** GET /appointments/{appointmentId}



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| appointmentId | Path | Long | 是 | 挂号订单 ID | `7001` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "查询成功",
    "data":  {
                 "id":  7001,
                 "status":  "LOCKED",
                 "doctor":  {
                                "id":  401,
                                "name":  "王医生",
                                "departmentName":  "呼吸内科"
                            },
                 "slot":  {
                              "id":  501,
                              "startTime":  "2026-07-30T09:00:00+08:00",
                              "endTime":  "2026-07-30T09:30:00+08:00"
                          },
                 "amountCent":  5000,
                 "expireAt":  "2026-07-28T10:15:00+08:00",
                 "payment":  {
                                 "id":  8001,
                                 "status":  "PENDING"
                             }
             },
    "traceId":  "01J7X9T9"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 挂号订单不存在 | `appointmentId` 未找到 |
| A0301 | 403 | 无权查看该订单 | 订单不属于当前患者 |

#### 5.3.4 取消挂号锁定订单

```mermaid
flowchart LR
    A[用户取消 LOCKED 订单] --> B[条件更新订单] --> C[释放 Redis 和数据库库存] --> D[关闭支付单]
```

```plantuml
@startuml
participant H5
participant AppointmentService
database PostgreSQL
database Redis
H5 -> AppointmentService: POST /appointments/{appointmentId}/cancel
AppointmentService -> PostgreSQL: LOCKED -> CANCELLED 条件更新
AppointmentService -> Redis: 归还 slot 库存
AppointmentService --> H5: cancelled=true
@enduml
```

##### RESTFUL API设计

**接口路径：** POST /appointments/{appointmentId}/cancel



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| X-Idempotency-Key | Header | String | 是 | 防止重复取消的请求键 | `uuid-v7` |
| appointmentId | Path | Long | 是 | 待取消的锁定订单 ID | `7001` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "挂号订单已取消",
    "data":  {
                 "appointmentId":  7001,
                 "status":  "CANCELLED"
             },
    "traceId":  "01J7X9U0"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0443 | 409 | 当前订单不可取消 | 订单不是 `LOCKED` 状态 |
| A0301 | 403 | 无权取消该订单 | 订单不属于当前患者 |

#### 5.3.5 创建候补登记

```mermaid
flowchart LR
    A[目标时段已满] --> B[校验无重复候补] --> C[写入候补队列] --> D[返回登记结果]
```

```plantuml
@startuml
participant H5
participant WaitlistService
database PostgreSQL
H5 -> WaitlistService: POST /waitlists
WaitlistService -> PostgreSQL: 写入 appointment_waitlist
WaitlistService --> H5: waitlistId、排队序号
@enduml
```

##### RESTFUL API设计

**接口路径：** POST /waitlists



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| X-Idempotency-Key | Header | String | 是 | 防止重复登记的请求键 | `uuid-v7` |
| slotId | Body | Long | 是 | 已约满或待候补的号源时段 ID | `501` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "候补登记成功",
    "data":  {
                 "waitlistId":  9001,
                 "slotId":  501,
                 "status":  "WAITING",
                 "queueNo":  3
             },
    "traceId":  "01J7X9V1"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0506 | 409 | 已登记该时段候补 | 当前患者对同一时段重复登记 |
| A0402 | 404 | 号源时段不存在 | `slotId` 未找到 |

#### 5.3.6 模拟支付挂号订单

```mermaid
flowchart LR
    A[输入登录密码] --> B[校验订单归属和有效期] --> C[校验 BCrypt] --> D[事务标记支付成功] --> E[确认挂号]
```

```plantuml
@startuml
participant H5
participant PaymentService
database PostgreSQL
H5 -> PaymentService: POST /payments/{paymentId}/simulate-pay
PaymentService -> PostgreSQL: 校验订单、密码哈希和支付状态
PaymentService -> PostgreSQL: PENDING -> SUCCESS，LOCKED -> PAID
PaymentService --> H5: paymentStatus=SUCCESS
@enduml
```

##### RESTFUL API设计

**接口路径：** POST /payments/{paymentId}/simulate-pay



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| X-Idempotency-Key | Header | String | 是 | 防止重复支付的请求键 | `uuid-v7` |
| paymentId | Path | Long | 是 | 待付款的挂号或购药支付单 ID | `8001` |
| loginPassword | Body | String | 是 | 当前 C 端登录密码，用于模拟付款校验 | `P@ssw0rd123` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "支付成功",
    "data":  {
                 "paymentId":  8001,
                 "status":  "SUCCESS",
                 "paidAt":  "2026-07-28T10:02:00+08:00"
             },
    "traceId":  "01J7X9W2"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0120 | 400 | 支付密码校验失败 | `loginPassword` 与当前登录密码不匹配 |
| A0441 | 409 | 支付单已过期 | 超过 15 分钟待支付窗口 |
| A0301 | 403 | 无权支付该订单 | 支付单不属于当前 C 端用户 |
| A0443 | 409 | 支付单状态不允许付款 | 支付单已成功、关闭或失败 |

#### 5.3.7 查询支付状态

```mermaid
flowchart LR
    A[H5 轮询支付状态] --> B[校验支付单归属] --> C[返回状态]
```

```plantuml
@startuml
participant H5
participant PaymentService
database PostgreSQL
H5 -> PaymentService: GET /payments/{paymentId}
PaymentService -> PostgreSQL: 查询本人支付单
PaymentService --> H5: status、paidAt、expireAt
@enduml
```

##### RESTFUL API设计

**接口路径：** GET /payments/{paymentId}



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| paymentId | Path | Long | 是 | 支付单 ID | `8001` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "查询成功",
    "data":  {
                 "id":  8001,
                 "businessType":  "APPOINTMENT",
                 "businessId":  7001,
                 "amountCent":  5000,
                 "status":  "SUCCESS",
                 "paidAt":  "2026-07-28T10:02:00+08:00",
                 "expireAt":  "2026-07-28T10:15:00+08:00"
             },
    "traceId":  "01J7X9X3"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 支付单不存在 | `paymentId` 未找到 |
| A0301 | 403 | 无权查看该支付单 | 支付单不属于当前用户 |

#### 5.3.8 支付回调预留接口

```mermaid
flowchart LR
    A[支付渠道回调] --> B[验签与幂等校验] --> C[更新支付与业务订单] --> D[返回 ACK]
```

```plantuml
@startuml
participant Provider
participant PaymentCallbackService
database PostgreSQL
Provider -> PaymentCallbackService: POST /internal/payment-callbacks
PaymentCallbackService -> PostgreSQL: 幂等更新 payment_order 与业务订单
PaymentCallbackService --> Provider: 200 ACK
@enduml
```

##### RESTFUL API设计

**接口路径：** POST /internal/payment-callbacks



**请求参数：** 该接口仅允许已签名的内部支付渠道调用，不使用 H5 Bearer Token。

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| providerTransactionId | Body | String | 是 | 支付渠道交易流水号 | `wx_202607280001` |
| paymentId | Body | Long | 是 | 平台支付单 ID | `8001` |
| status | Body | String | 是 | 渠道支付结果 | `SUCCESS` |
| paidAt | Body | DateTime | 是 | 渠道实际付款时间 | `2026-07-28T10:02:00+08:00` |
| signature | Body | String | 是 | 渠道回调签名 | `sha256=...` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "回调已受理",
    "data":  {
                 "accepted":  true
             },
    "traceId":  "01J7X9Y4"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0341 | 401 | 回调签名验证失败 | `signature` 与渠道签名不匹配 |
| A0402 | 404 | 支付单不存在 | `paymentId` 未找到 |

### 5.4 在线问诊与处方（扩展）

#### 5.4.1 创建或保存预问诊

```mermaid
flowchart LR
    A[填写病情资料] --> B[校验挂号归属] --> C[保存草稿或提交] --> D[返回问诊信息]
```

```plantuml
@startuml
participant H5
participant ConsultationService
database PostgreSQL
H5 -> ConsultationService: POST /consultations/pre-consultations
ConsultationService -> PostgreSQL: 校验 PAID 挂号并保存摘要
ConsultationService --> H5: consultationId、status
@enduml
```

##### RESTFUL API设计

**接口路径：** POST /consultations/pre-consultations



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| X-Idempotency-Key | Header | String | 是 | 防止重复保存的请求键 | `uuid-v7` |
| appointmentId | Body | Long | 是 | 已支付的挂号订单 ID | `7001` |
| chiefComplaint | Body | String | 是 | 患者主诉 | `咳嗽发热三天` |
| submit | Body | Boolean | 是 | 是否提交给医生 | `true` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "操作成功",
    "data":  {
                 "consultationId":  "consultationId",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0443 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0506 | 409 | 当前业务状态不允许该操作 | 资源状态不满足创建、修改或支付条件 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.4.2 查询问诊记录列表

```mermaid
flowchart LR
    A[查询我的问诊] --> B[按患者归属分页] --> C[返回问诊列表]
```

```plantuml
@startuml
participant H5
participant ConsultationService
database PostgreSQL
H5 -> ConsultationService: GET /consultations
ConsultationService -> PostgreSQL: 查询本人问诊
ConsultationService --> H5: 分页记录
@enduml
```

##### RESTFUL API设计

**接口路径：** GET /consultations



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| status | Query | String | 否 | 问诊状态筛选 | `PENDING` |
| pageNo | Query | Integer | 否 | 页码，从 1 开始 | `1` |
| pageSize | Query | Integer | 否 | 每页条数 | `20` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "查询成功",
    "data":  {
                 "records":  "records",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.4.3 查询问诊详情和文字消息

```mermaid
flowchart LR
    A[请求问诊详情] --> B[校验本人归属] --> C[读取摘要与消息] --> D[返回详情]
```

```plantuml
@startuml
participant H5
participant ConsultationService
database PostgreSQL
H5 -> ConsultationService: GET /consultations/{consultationId}
ConsultationService -> PostgreSQL: 查询本人问诊、摘要和消息
ConsultationService --> H5: 问诊详情
@enduml
```

##### RESTFUL API设计

**接口路径：** GET /consultations/{consultationId}



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| consultationId | Path | Long | 是 | 问诊记录 ID | `10001` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "查询成功",
    "data":  {
                 "consultation":  "consultation",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.4.4 发送文字问诊消息

```mermaid
flowchart LR
    A[患者发送文字] --> B[校验问诊进行中] --> C[持久化消息] --> D[通知医生端]
```

```plantuml
@startuml
participant H5
participant ConsultationService
participant NotificationService
database PostgreSQL
H5 -> ConsultationService: POST /consultations/{consultationId}/messages
ConsultationService -> PostgreSQL: 写入 consultation_message
ConsultationService -> NotificationService: 创建医生待处理通知
ConsultationService --> H5: messageId
@enduml
```

##### RESTFUL API设计

**接口路径：** POST /consultations/{consultationId}/messages



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| X-Idempotency-Key | Header | String | 是 | 防止重复发送的请求键 | `uuid-v7` |
| consultationId | Path | Long | 是 | 问诊记录 ID | `10001` |
| content | Body | String | 是 | 文字问诊内容，1 至 2000 字 | `最高体温38.5度` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "操作成功",
    "data":  {
                 "messageId":  "messageId",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0443 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0430 | 409 | 当前业务状态不允许该操作 | 资源状态不满足创建、修改或支付条件 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.4.5 查询处方列表和详情

```mermaid
flowchart LR
    A[查询本人处方] --> B[按患者归属过滤] --> C[返回处方及明细]
```

```plantuml
@startuml
participant H5
participant PrescriptionService
database PostgreSQL
H5 -> PrescriptionService: GET /prescriptions 或 /prescriptions/{prescriptionId}
PrescriptionService -> PostgreSQL: 查询本人已签发处方
PrescriptionService --> H5: 处方列表或详情
@enduml
```

##### RESTFUL API设计

**接口路径：** GET /prescriptions
**接口路径：** GET /prescriptions/{prescriptionId}



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| pageNo | Query | Integer | 否 | 页码，从 1 开始 | `1` |
| pageSize | Query | Integer | 否 | 每页条数 | `20` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "查询成功",
    "data":  {
                 "records":  "records",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.4.6 查询处方解读

```mermaid
flowchart LR
    A[请求处方解读] --> B[校验本人处方] --> C[读取医生确认的解读] --> D[返回免责声明]
```

```plantuml
@startuml
participant H5
participant PrescriptionService
database PostgreSQL
H5 -> PrescriptionService: GET /prescriptions/{prescriptionId}/interpretation
PrescriptionService -> PostgreSQL: 查询已生成解读
PrescriptionService --> H5: 解读内容及免责声明
@enduml
```

##### RESTFUL API设计

**接口路径：** GET /prescriptions/{prescriptionId}/interpretation



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| prescriptionId | Path | Long | 是 | 处方 ID | `12001` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "查询成功",
    "data":  {
                 "interpretation":  "interpretation",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| B0202 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

### 5.5 处方购药与模拟支付（扩展）

#### 5.5.1 查询药店和处方药库存

```mermaid
flowchart LR
    A[选择处方] --> B[验证本人处方] --> C[查询药店库存] --> D[返回可供药店]
```

```plantuml
@startuml
participant H5
participant PharmacyService
database PostgreSQL
H5 -> PharmacyService: GET /pharmacies/inventory
PharmacyService -> PostgreSQL: 查询处方药在售库存
PharmacyService --> H5: 药店及库存列表
@enduml
```

##### RESTFUL API设计

**接口路径：** GET /pharmacies/inventory



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| prescriptionId | Query | Long | 是 | 要匹配药店库存的本人处方 ID | `12001` |
| longitude | Query | Number | 否 | 当前位置经度，用于距离排序 | `116.397` |
| latitude | Query | Number | 否 | 当前位置纬度，用于距离排序 | `39.908` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "查询成功",
    "data":  {
                 "pharmacies":  "pharmacies",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.5.2 创建购药订单草稿

```mermaid
flowchart LR
    A[提交药店和处方] --> B[校验处方及库存] --> C[Redis 预留库存] --> D[创建 DRAFT 订单]
```

```plantuml
@startuml
participant H5
participant DrugOrderService
database Redis
database PostgreSQL
H5 -> DrugOrderService: POST /drug-orders
DrugOrderService -> Redis: 原子预留各药品库存
DrugOrderService -> PostgreSQL: 创建 DRAFT 订单、明细、支付单
DrugOrderService --> H5: 订单与 expireAt
@enduml
```

##### RESTFUL API设计

**接口路径：** POST /drug-orders



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| X-Idempotency-Key | Header | String | 是 | 防止重复下单的请求键 | `uuid-v7` |
| prescriptionId | Body | Long | 是 | 本人有效处方 ID | `12001` |
| pharmacyId | Body | Long | 是 | 选定药店 ID | `14001` |
| deliveryMethod | Body | String | 是 | 取药方式 | `DELIVERY` |
| deliveryAddress | Body | String | 条件必填 | 配送到家时的收货地址 | `北京市东城区示例路1号` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "操作成功",
    "data":  {
                 "drugOrderId":  "drugOrderId",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| B0300 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0443 | 409 | 当前业务状态不允许该操作 | 资源状态不满足创建、修改或支付条件 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.5.3 查询购药订单列表和详情

```mermaid
flowchart LR
    A[查询本人购药订单] --> B[按患者归属读取] --> C[返回列表或详情]
```

```plantuml
@startuml
participant H5
participant DrugOrderService
database PostgreSQL
H5 -> DrugOrderService: GET /drug-orders 或 /drug-orders/{drugOrderId}
DrugOrderService -> PostgreSQL: 查询本人订单与明细
DrugOrderService --> H5: 订单数据
@enduml
```

##### RESTFUL API设计

**接口路径：** GET /drug-orders
**接口路径：** GET /drug-orders/{drugOrderId}



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| status | Query | String | 否 | 订单状态筛选 | `PENDING_PAYMENT` |
| pageNo | Query | Integer | 否 | 页码 | `1` |
| pageSize | Query | Integer | 否 | 每页条数 | `20` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "查询成功",
    "data":  {
                 "records":  "records",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.5.4 取消购药订单

```mermaid
flowchart LR
    A[取消待付款订单] --> B[条件更新订单] --> C[释放预留库存] --> D[关闭支付单]
```

```plantuml
@startuml
participant H5
participant DrugOrderService
database PostgreSQL
database Redis
H5 -> DrugOrderService: POST /drug-orders/{drugOrderId}/cancel
DrugOrderService -> PostgreSQL: PENDING_PAYMENT -> CANCELLED
DrugOrderService -> Redis: 归还药品预留库存
DrugOrderService --> H5: cancelled=true
@enduml
```

##### RESTFUL API设计

**接口路径：** POST /drug-orders/{drugOrderId}/cancel



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| X-Idempotency-Key | Header | String | 是 | 防止重复取消的请求键 | `uuid-v7` |
| drugOrderId | Path | Long | 是 | 待取消的购药订单 ID | `15001` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "操作成功",
    "data":  {
                 "drugOrderId":  "drugOrderId",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0443 | 409 | 当前业务状态不允许该操作 | 资源状态不满足创建、修改或支付条件 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.5.5 模拟支付购药订单

```mermaid
flowchart LR
    A[输入登录密码] --> B[校验支付单] --> C[校验密码] --> D[支付成功并扣减库存] --> E[创建用药计划]
```

```plantuml
@startuml
participant H5
participant PaymentService
participant ReminderService
database PostgreSQL
H5 -> PaymentService: POST /payments/{paymentId}/simulate-pay
PaymentService -> PostgreSQL: 更新支付单和购药订单为成功
PaymentService -> ReminderService: 按处方创建用药计划
PaymentService --> H5: SUCCESS
@enduml
```

##### RESTFUL API设计

**接口路径：** POST /payments/{paymentId}/simulate-pay



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| X-Idempotency-Key | Header | String | 是 | 防止重复支付的请求键 | `uuid-v7` |
| paymentId | Path | Long | 是 | 购药支付单 ID | `8002` |
| loginPassword | Body | String | 是 | 当前登录密码，用于模拟支付校验 | `P@ssw0rd123` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "操作成功",
    "data":  {
                 "paymentId":  "paymentId",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0301 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0443 | 409 | 当前业务状态不允许该操作 | 资源状态不满足创建、修改或支付条件 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

### 5.6 健康档案、提醒、随访和通知（扩展）

#### 5.6.1 查询健康档案

```mermaid
flowchart LR
    A[请求本人健康档案] --> B[按用户归属读取] --> C[汇总基础与健康信息] --> D[返回档案]
```

```plantuml
@startuml
participant H5
participant HealthRecordService
database PostgreSQL
H5 -> HealthRecordService: GET /health-record
HealthRecordService -> PostgreSQL: 查询本人资料、过敏史和既往史
HealthRecordService --> H5: 健康档案
@enduml
```

##### RESTFUL API设计

**接口路径：** GET /health-record



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "查询成功",
    "data":  {
                 "healthRecord":  "healthRecord",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.6.2 维护过敏史

```mermaid
flowchart LR
    A[提交过敏史] --> B[校验本人档案] --> C[新增或更新记录] --> D[返回记录]
```

```plantuml
@startuml
participant H5
participant HealthRecordService
database PostgreSQL
H5 -> HealthRecordService: POST/PUT /health-record/allergies
HealthRecordService -> PostgreSQL: 写入 patient_allergy
HealthRecordService --> H5: 过敏史记录
@enduml
```

##### RESTFUL API设计

**接口路径：** POST /health-record/allergies
**接口路径：** PUT /health-record/allergies



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| allergyId | Path | Long | 条件必填 | 更新记录时的过敏史 ID | `16001` |
| allergen | Body | String | 是 | 过敏原名称 | `青霉素` |
| reaction | Body | String | 否 | 过敏反应描述 | `皮疹` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "操作成功",
    "data":  {
                 "allergy":  "allergy",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.6.3 维护既往史

```mermaid
flowchart LR
    A[提交既往史] --> B[校验归属] --> C[写入记录] --> D[返回既往史]
```

```plantuml
@startuml
participant H5
participant HealthRecordService
database PostgreSQL
H5 -> HealthRecordService: POST/PUT /health-record/histories
HealthRecordService -> PostgreSQL: 写入 patient_medical_history
HealthRecordService --> H5: 既往史记录
@enduml
```

##### RESTFUL API设计

**接口路径：** POST /health-record/histories
**接口路径：** PUT /health-record/histories



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| historyId | Path | Long | 条件必填 | 更新记录时的既往史 ID | `17001` |
| content | Body | String | 是 | 既往病史内容 | `高血压病史5年` |
| occurredAt | Body | Date | 否 | 病史发生或记录日期 | `2021-01-01` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "操作成功",
    "data":  {
                 "history":  "history",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.6.4 录入和查询检查报告

```mermaid
flowchart LR
    A[录入或查询报告] --> B[校验本人归属] --> C[写入或分页读取] --> D[返回报告]
```

```plantuml
@startuml
participant H5
participant ReportService
database PostgreSQL
H5 -> ReportService: POST/GET /reports
ReportService -> PostgreSQL: 写入或查询 patient_report
ReportService --> H5: 报告数据
@enduml
```

##### RESTFUL API设计

**接口路径：** POST /reports
**接口路径：** GET /reports



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| X-Idempotency-Key | Header | String | 条件必填 | 录入报告时的防重键 | `uuid-v7` |
| reportName | Body | String | 条件必填 | 检查报告名称 | `血常规` |
| reportDate | Body | Date | 条件必填 | 报告日期 | `2026-07-28` |
| indicators | Body | Array | 条件必填 | 指标数组，元素含名称、数值、单位和参考区间 | `[{"name":"白细胞","value":"12.5"}]` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "操作成功",
    "data":  {
                 "report":  "report",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.6.5 查询报告解读

```mermaid
flowchart LR
    A[请求报告说明] --> B[校验报告归属] --> C[读取已生成说明] --> D[返回免责声明]
```

```plantuml
@startuml
participant H5
participant ReportService
database PostgreSQL
H5 -> ReportService: GET /reports/{reportId}/interpretation
ReportService -> PostgreSQL: 查询报告说明
ReportService --> H5: 指标说明、就医建议、免责声明
@enduml
```

##### RESTFUL API设计

**接口路径：** GET /reports/{reportId}/interpretation



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| reportId | Path | Long | 是 | 检查报告 ID | `18001` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "查询成功",
    "data":  {
                 "interpretation":  "interpretation",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| B0202 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.6.6 查询与更新用药计划

```mermaid
flowchart LR
    A[查询或更新提醒] --> B[校验计划归属] --> C[读取或变更状态] --> D[返回计划]
```

```plantuml
@startuml
participant H5
participant ReminderService
database PostgreSQL
H5 -> ReminderService: GET /medication-plans 或 PATCH /medication-plans/{planId}
ReminderService -> PostgreSQL: 查询或更新本人用药计划
ReminderService --> H5: 用药计划
@enduml
```

##### RESTFUL API设计

**接口路径：** GET /medication-plans
**接口路径：** PATCH /medication-plans/{planId}



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| planId | Path | 条件必填 | 更新计划时的计划 ID | `19001` |
| status | Query | String | 否 | 查询状态筛选 | `ACTIVE` |
| action | Body | String | 条件必填 | 更新动作，暂停、恢复或完成 | `PAUSE` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "操作成功",
    "data":  {
                 "medicationPlan":  "medicationPlan",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0443 | 409 | 当前业务状态不允许该操作 | 资源状态不满足创建、修改或支付条件 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.6.7 查询与确认随访计划

```mermaid
flowchart LR
    A[查询或确认随访] --> B[校验本人归属] --> C[读取或更新确认状态] --> D[返回计划]
```

```plantuml
@startuml
participant H5
participant FollowUpService
database PostgreSQL
H5 -> FollowUpService: GET /follow-ups 或 POST /follow-ups/{followUpId}/confirm
FollowUpService -> PostgreSQL: 查询或更新 follow_up_plan
FollowUpService --> H5: 随访计划
@enduml
```

##### RESTFUL API设计

**接口路径：** GET /follow-ups
**接口路径：** POST /follow-ups/{followUpId}/confirm



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| followUpId | Path | 条件必填 | 确认随访时的计划 ID | `20001` |
| status | Query | String | 否 | 查询状态筛选 | `PENDING_CONFIRM` |
| remindAt | Body | DateTime | 条件必填 | 确认后的提醒时间 | `2026-08-10T09:00:00+08:00` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "操作成功",
    "data":  {
                 "followUp":  "followUp",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0443 | 409 | 当前业务状态不允许该操作 | 资源状态不满足创建、修改或支付条件 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.6.8 查询和标记通知已读

```mermaid
flowchart LR
    A[查询或已读通知] --> B[按当前用户过滤] --> C[分页读取或更新] --> D[返回结果]
```

```plantuml
@startuml
participant H5
participant NotificationService
database PostgreSQL
H5 -> NotificationService: GET /notifications 或 POST /notifications/{notificationId}/read
NotificationService -> PostgreSQL: 查询或更新本人通知
NotificationService --> H5: 通知数据
@enduml
```

##### RESTFUL API设计

**接口路径：** GET /notifications
**接口路径：** POST /notifications/{notificationId}/read



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| notificationId | Path | 条件必填 | 标记已读时的通知 ID | `21001` |
| read | Query | Boolean | 否 | 是否筛选已读状态 | `false` |
| pageNo | Query | Integer | 否 | 页码 | `1` |
| pageSize | Query | Integer | 否 | 每页条数 | `20` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "操作成功",
    "data":  {
                 "notifications":  "notifications",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

### 5.7 组合读写接口的独立图例

本节为第 5 章中参数与响应已分别定义、但原先共用业务描述的接口补充独立流程图与时序图，确保每一个 HTTP 接口均具备可执行的交互描述。

#### 5.7.1 更新个人资料 `PUT /profile`

```mermaid
flowchart LR
    A[提交个人资料] --> B[校验当前用户] --> C[更新 patient_profile] --> D[返回更新结果]
```

```plantuml
@startuml
participant H5
participant ProfileService
database PostgreSQL
H5 -> ProfileService: PUT /profile
ProfileService -> PostgreSQL: UPDATE patient_profile WHERE user_id=token.userId
ProfileService --> H5: 更新后的资料
@enduml
```

##### RESTFUL API设计

**

|      |      |      |      |
| --- | --- | --- | --- |
|      |      |      |      |
|      |      |      |      |

#### 5.7.2 更新过敏史 `PUT /health-record/allergies/{allergyId}`

```mermaid
flowchart LR
    A[提交过敏史修改] --> B[校验 allergy 归属] --> C[更新记录] --> D[返回结果]
```

```plantuml
@startuml
participant H5
participant HealthRecordService
database PostgreSQL
H5 -> HealthRecordService: PUT /health-record/allergies/{allergyId}
HealthRecordService -> PostgreSQL: UPDATE patient_allergy WHERE id=? AND patient_id=?
HealthRecordService --> H5: 更新后的过敏史
@enduml
```

##### RESTFUL API设计

**接口路径：** PUT /health-record/allergies/{allergyId}



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| allergyId | Path | Long | 条件必填 | 更新记录时的过敏史 ID | `16001` |
| allergen | Body | String | 是 | 过敏原名称 | `青霉素` |
| reaction | Body | String | 否 | 过敏反应描述 | `皮疹` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "操作成功",
    "data":  {
                 "allergy":  "allergy",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.7.3 更新既往史 `PUT /health-record/histories/{historyId}`

```mermaid
flowchart LR
    A[提交既往史修改] --> B[校验记录归属] --> C[更新记录] --> D[返回结果]
```

```plantuml
@startuml
participant H5
participant HealthRecordService
database PostgreSQL
H5 -> HealthRecordService: PUT /health-record/histories/{historyId}
HealthRecordService -> PostgreSQL: UPDATE patient_medical_history WHERE id=? AND patient_id=?
HealthRecordService --> H5: 更新后的既往史
@enduml
```

##### RESTFUL API设计

**接口路径：** PUT /health-record/histories/{historyId}



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| historyId | Path | Long | 条件必填 | 更新记录时的既往史 ID | `17001` |
| content | Body | String | 是 | 既往病史内容 | `高血压病史5年` |
| occurredAt | Body | Date | 否 | 病史发生或记录日期 | `2021-01-01` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "操作成功",
    "data":  {
                 "history":  "history",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.7.4 查询处方详情 `GET /prescriptions/{prescriptionId}`

```mermaid
flowchart LR
    A[请求处方详情] --> B[校验患者归属] --> C[查询处方与明细] --> D[返回详情]
```

```plantuml
@startuml
participant H5
participant PrescriptionService
database PostgreSQL
H5 -> PrescriptionService: GET /prescriptions/{prescriptionId}
PrescriptionService -> PostgreSQL: 查询处方、明细且 patient_id=当前患者
PrescriptionService --> H5: 处方详情
@enduml
```

##### RESTFUL API设计

**接口路径：** GET /prescriptions/{prescriptionId}



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| prescriptionId | Path | Long | 是 | 处方 ID | `12001` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "查询成功",
    "data":  {
                 "prescription":  "prescription",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.7.5 查询购药订单详情 `GET /drug-orders/{drugOrderId}`

```mermaid
flowchart LR
    A[请求购药订单] --> B[校验患者归属] --> C[查询订单与明细] --> D[返回详情]
```

```plantuml
@startuml
participant H5
participant DrugOrderService
database PostgreSQL
H5 -> DrugOrderService: GET /drug-orders/{drugOrderId}
DrugOrderService -> PostgreSQL: 查询订单明细且 patient_id=当前患者
DrugOrderService --> H5: 购药订单详情
@enduml
```

##### RESTFUL API设计

**接口路径：** GET /drug-orders/{drugOrderId}



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| drugOrderId | Path | Long | 是 | 购药订单 ID | `15001` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "查询成功",
    "data":  {
                 "drugOrder":  "drugOrder",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.7.6 查询报告列表 `GET /reports`

```mermaid
flowchart LR
    A[请求报告列表] --> B[按本人档案过滤] --> C[分页查询] --> D[返回列表]
```

```plantuml
@startuml
participant H5
participant ReportService
database PostgreSQL
H5 -> ReportService: GET /reports
ReportService -> PostgreSQL: SELECT patient_report WHERE patient_id=?
ReportService --> H5: 分页报告列表
@enduml
```

##### RESTFUL API设计

**接口路径：** GET /reports



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| pageNo | Query | Integer | 否 | 页码 | `1` |
| pageSize | Query | Integer | 否 | 每页条数 | `20` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "查询成功",
    "data":  {
                 "records":  "records",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.7.7 查询报告详情 `GET /reports/{reportId}`

```mermaid
flowchart LR
    A[请求报告详情] --> B[校验报告归属] --> C[查询指标] --> D[返回报告]
```

```plantuml
@startuml
participant H5
participant ReportService
database PostgreSQL
H5 -> ReportService: GET /reports/{reportId}
ReportService -> PostgreSQL: 查询报告及指标且 patient_id=?
ReportService --> H5: 报告详情
@enduml
```

##### RESTFUL API设计

**接口路径：** GET /reports/{reportId}



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| reportId | Path | Long | 是 | 检查报告 ID | `18001` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "查询成功",
    "data":  {
                 "report":  "report",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.7.8 更新用药计划 `PATCH /medication-plans/{planId}`

```mermaid
flowchart LR
    A[暂停、恢复或完成] --> B[校验计划归属和状态] --> C[更新计划] --> D[返回状态]
```

```plantuml
@startuml
participant H5
participant ReminderService
database PostgreSQL
H5 -> ReminderService: PATCH /medication-plans/{planId}
ReminderService -> PostgreSQL: 条件更新 medication_plan
ReminderService --> H5: 计划新状态
@enduml
```

##### RESTFUL API设计

**接口路径：** PATCH /medication-plans/{planId}



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| planId | Path | 条件必填 | 更新计划时的计划 ID | `19001` |
| status | Query | String | 否 | 查询状态筛选 | `ACTIVE` |
| action | Body | String | 条件必填 | 更新动作，暂停、恢复或完成 | `PAUSE` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "操作成功",
    "data":  {
                 "medicationPlan":  "medicationPlan",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0443 | 409 | 当前业务状态不允许该操作 | 资源状态不满足创建、修改或支付条件 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.7.9 确认随访计划 `POST /follow-ups/{followUpId}/confirm`

```mermaid
flowchart LR
    A[确认提醒时间] --> B[校验计划归属] --> C[状态改为 CONFIRMED] --> D[返回计划]
```

```plantuml
@startuml
participant H5
participant FollowUpService
database PostgreSQL
H5 -> FollowUpService: POST /follow-ups/{followUpId}/confirm
FollowUpService -> PostgreSQL: 更新 follow_up_plan 为 CONFIRMED
FollowUpService --> H5: 已确认的随访计划
@enduml
```

##### RESTFUL API设计

**接口路径：** POST /follow-ups/{followUpId}/confirm



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| followUpId | Path | 条件必填 | 确认随访时的计划 ID | `20001` |
| status | Query | String | 否 | 查询状态筛选 | `PENDING_CONFIRM` |
| remindAt | Body | DateTime | 条件必填 | 确认后的提醒时间 | `2026-08-10T09:00:00+08:00` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "操作成功",
    "data":  {
                 "followUp":  "followUp",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0443 | 409 | 当前业务状态不允许该操作 | 资源状态不满足创建、修改或支付条件 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

#### 5.7.10 标记通知已读 `POST /notifications/{notificationId}/read`

```mermaid
flowchart LR
    A[标记已读] --> B[校验通知归属] --> C[写入 read_at] --> D[返回已读状态]
```

```plantuml
@startuml
participant H5
participant NotificationService
database PostgreSQL
H5 -> NotificationService: POST /notifications/{notificationId}/read
NotificationService -> PostgreSQL: UPDATE notification SET read_at=now() WHERE id=? AND user_id=?
NotificationService --> H5: read=true
@enduml
```

##### RESTFUL API设计

**接口路径：** POST /notifications/{notificationId}/read



**请求参数：**

| 参数 | 位置 | 类型 | 必填 | 描述 | 示例/默认值 |
| --- | --- | --- | --- | --- | --- |
| Authorization | Header | String | 是 | 当前 C 端访问令牌 | `Bearer eyJ...` |
| notificationId | Path | 条件必填 | 标记已读时的通知 ID | `21001` |
| read | Query | Boolean | 否 | 是否筛选已读状态 | `false` |
| pageNo | Query | Integer | 否 | 页码 | `1` |
| pageSize | Query | Integer | 否 | 每页条数 | `20` |

**响应示例：**
```json
{
    "code":  "00000",
    "message":  "操作成功",
    "data":  {
                 "notifications":  "notifications",
                 "status":  "SUCCESS"
             },
    "traceId":  "01J7X-EXAMPLE"
}
```

| 错误码 | HTTP 状态 | 含义 | 触发场景 |
| --- | --- | --- | --- |
| A0402 | 404 | 资源不存在或当前用户无可访问资源 | 请求的业务资源未找到或不可访问 |
| A0301 | 403 | 无权访问该患者资源 | 资源不属于当前 C 端用户 |

## 6. PostgreSQL 数据库设计

所有主表使用 `bigint generated by default as identity` 主键、`timestamptz not null default now()` 创建和更新时间，业务删除优先使用 `deleted_at` 软删除。身份证号、联系人电话等敏感列采用应用层加密后存储；密码仅保存 BCrypt 哈希。

| 表名 | 用途 | 核心字段与约束 |
| --- | --- | --- |
| `c_user` | C 端独立账号 | `account` 唯一，`password_hash`，`status` |
| `c_refresh_token` | C 端刷新令牌会话 | `token_hash` 唯一，`user_id`，`expired_at`，`revoked_at` |
| `patient_profile` | 患者基础资料 | `user_id` 唯一，姓名、性别、生日、加密联系方式 |
| `patient_allergy` / `patient_medical_history` | 健康病史 | `patient_id` 外键与归属索引 |
| `department` / `doctor` / `schedule_slot` | 挂号资源 | 启用状态；时段唯一 `(doctor_id,start_time)` |
| `appointment_order` | 挂号订单 | `(patient_id,slot_id)` 的有效订单唯一约束，状态、到期时间 |
| `appointment_waitlist` | 候补队列 | `(patient_id,slot_id)` 唯一，`queue_no` |
| `payment_order` | 抽象支付单 | `business_type + business_id` 唯一，金额、支付状态、到期时间 |
| `consultation` / `consultation_message` | 问诊与文字消息 | `appointment_id` 唯一，按问诊和发送时间索引 |
| `prescription` / `prescription_item` | 医生签发处方 | 处方状态、药品、频次、用法、疗程 |
| `pharmacy` / `pharmacy_drug_stock` | 药店和药品库存 | `(pharmacy_id,drug_id)` 唯一，`available_count >= 0` |
| `drug_order` / `drug_order_item` | 购药订单与明细 | 订单状态、配送信息、预留到期时间 |
| `patient_report` / `patient_report_indicator` | 检查报告 | 报告归属、指标值与参考区间 |
| `medication_plan` / `follow_up_plan` | 提醒与随访 | 状态、下次提醒时间、患者归属 |
| `notification` | C 端通知 | `(user_id,created_at desc)` 索引、已读时间 |

### 6.1 关键建表示例

```sql
-- C 端用户表：与 B 端用户域、角色与密钥完全隔离
create table c_user (
    id bigint generated by default as identity primary key,
    account varchar(32) not null unique,
    password_hash varchar(100) not null,
    status varchar(20) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_c_user_status check (status in ('ACTIVE', 'DISABLED'))
);
comment on table c_user is 'C端独立登录用户表';
comment on column c_user.password_hash is '使用 BCrypt 生成的登录密码哈希';

-- 号源时段表：available_count 是 PostgreSQL 最终一致性库存
create table schedule_slot (
    id bigint generated by default as identity primary key,
    doctor_id bigint not null references doctor(id),
    start_time timestamptz not null,
    end_time timestamptz not null,
    total_count integer not null,
    available_count integer not null,
    fee_cent integer not null,
    status varchar(20) not null default 'OPEN',
    constraint uk_schedule_slot unique (doctor_id, start_time),
    constraint ck_schedule_slot_count check (total_count >= 0 and available_count between 0 and total_count)
);
comment on table schedule_slot is '医生可预约号源时段表';
comment on column schedule_slot.available_count is '可预约余量，须与 Redis 预扣库存定期核对';

-- 挂号订单表：仅锁定中的订单可被取消或由超时任务释放
create table appointment_order (
    id bigint generated by default as identity primary key,
    patient_id bigint not null references patient_profile(id),
    slot_id bigint not null references schedule_slot(id),
    status varchar(20) not null,
    amount_cent integer not null,
    expire_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint ck_appointment_status check (status in ('LOCKED', 'PAID', 'CANCELLED', 'EXPIRED'))
);
create index idx_appointment_patient_created on appointment_order(patient_id, created_at desc);
create index idx_appointment_expire on appointment_order(status, expire_at) where status = 'LOCKED';
comment on table appointment_order is 'C端挂号订单表';
comment on column appointment_order.expire_at is '锁号和待支付的15分钟失效时间';

-- 支付单：一个业务订单只允许对应一张支付单
create table payment_order (
    id bigint generated by default as identity primary key,
    business_type varchar(30) not null,
    business_id bigint not null,
    payer_user_id bigint not null references c_user(id),
    amount_cent integer not null,
    status varchar(20) not null default 'PENDING',
    expire_at timestamptz not null,
    paid_at timestamptz,
    provider_transaction_id varchar(128),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint uk_payment_business unique (business_type, business_id),
    constraint ck_payment_status check (status in ('PENDING', 'SUCCESS', 'FAILED', 'CLOSED'))
);
comment on table payment_order is '支付抽象订单表，当前由模拟支付驱动';
```

## 7. 关键技术设计

### 7.1 号源和药品库存防超卖

1. 查询接口读取 Redis 余量快照；下单不能信任前端余量。
2. 创建订单执行 Redis Lua 脚本：校验余量大于零、扣减余量、写入 15 分钟锁定标记，保证原子性。
3. Redis 扣减成功后，在 PostgreSQL 事务中创建订单、支付单和库存流水；事务失败时执行补偿归还 Redis 库存。
4. 支付、取消和超时处理以订单状态条件更新保证幂等。超时任务扫描 `LOCKED/PENDING_PAYMENT` 且 `expire_at < now()` 的订单，关闭支付单并释放预留。
5. 每分钟校验 Redis 与 PostgreSQL 库存差异，发生差异时暂停该时段下单并告警、重建缓存。

### 7.2 密码、认证与数据权限

- 图形验证码以哈希形式存入 Redis，120 秒过期且验证一次即删除；按 IP 和账号限流。
- 登录密码和模拟支付复用同一 BCrypt 校验，但支付请求不记录密码明文、哈希或可逆日志。
- JWT 设置 `aud=c-h5` 和独立签名密钥；C 端网关拒绝 B 端 `aud`，B 端网关同样拒绝 C 端 `aud`。
- 所有患者资源查询强制追加 `patient_id = currentPatientId`，禁止仅按资源 ID 读取；病历、处方、报告和订单不提供匿名访问。

### 7.3 异步任务与通知

- 超时释放、候补通知、用药提醒和随访提醒通过持久化任务表或消息队列触发，任务消费必须以业务唯一键幂等。
- 支付成功后，购药订单用事务外盒模式创建用药计划；失败可重试，不影响已成功支付的订单。
- 通知仅写入当前 C 端用户的 `notification`，不向 B 端账号域写入患者消息。

## 8. 验收与测试场景

| 场景 | 预期结果 |
| --- | --- |
| 验证码错误、过期、重复使用 | 注册失败；验证码不可重复验证 |
| C/B Token 混用 | 网关拒绝，返回未认证 |
| 两人并发抢最后一个号源 | 仅一人创建 `LOCKED` 订单，另一人返回 `B0201` |
| 重复提交创建或支付 | 根据幂等键返回首次结果，不重复扣库存或重复支付 |
| 15 分钟未支付 | 订单和支付单关闭，库存准确归还 |
| 密码错误、支付单非本人或已关闭 | 支付失败，订单与库存不变 |
| 重复支付回调 | 仅首次改变状态，后续回调成功 ACK 且不重复执行业务动作 |
| 非本人访问档案、处方、报告或订单 | 返回 `A0301` 或资源不存在，不泄露资源信息 |
| 报告/处方解读 | 仅展示说明和“仅供参考，不替代医生诊断”声明，不输出确诊或修改处方 |

## 9. 排期建议

| 阶段 | 内容 | 交付 |
| --- | --- | --- |
| 第一阶段 | C 端认证、资料、科室医生号源查询、锁号与模拟支付 | MVP 挂号闭环 |
| 第二阶段 | 候补、超时任务、库存核对、通知 | 挂号可靠性完善 |
| 第三阶段 | 问诊、处方、购药订单与提醒 | 诊后购药闭环 |
| 第四阶段 | 档案、报告、随访与运营监控 | 健康管理闭环 |

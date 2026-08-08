# SPHP 生产部署

本目录的模板用于将 Java 后端、Python Agent 与两套前端部署到同一台 Linux 服务器。真实密码、Token 和 API Key 只能保存在服务器私密文件中，禁止提交到仓库。

## 运行环境

- OpenJDK 21
- Python 3.11 或更高版本
- Node.js 20 或更高版本与 pnpm
- Nginx
- PostgreSQL 16，且已启用 `pgvector`
- Redis 7
- RabbitMQ 3（管理插件可选）

## Java 环境变量

在服务器创建权限为 `600` 的 `/etc/sphp/java.env`。以下变量必须填入真实值：

```text
SPHP_DB_USERNAME=
SPHP_DB_PASSWORD=
SPHP_DB_HOST=127.0.0.1
SPHP_DB_PORT=5432
SPHP_DB_NAME=sphp
SPHP_REDIS_HOST=127.0.0.1
SPHP_REDIS_PORT=6379
SPHP_REDIS_PASSWORD=
SPHP_RABBITMQ_HOST=127.0.0.1
SPHP_RABBITMQ_PORT=5672
SPHP_RABBITMQ_USERNAME=
SPHP_RABBITMQ_PASSWORD=
SPHP_ADMIN_SATOKEN_SECRET=
SPHP_PATIENT_JWT_SECRET=
SPHP_ADMIN_JWT_SECRET=
SPHP_CORS_ORIGIN_C=https://c.example.com
SPHP_CORS_ORIGIN_B=https://b.example.com
```

密钥字段应使用至少 32 字节随机值。Java 通过 `application-prod.yml` 读取上述变量，启动时必须指定 `prod` profile。

## Agent 配置

将 `sphp-agent/.env.production.example` 复制到服务器 `/opt/sphp/agent/.env`，填写 PostgreSQL、Redis、LLM 和 Embedding 的真实凭据。生产环境须保持 `DEBUG=false`、`ALLOW_ANONYMOUS=false` 和 `CHECKPOINTER_BACKEND=postgres`。

## 发布顺序

1. 连接 PostgreSQL 后，先确认数据库、角色、`vector` 扩展和现有迁移版本；空库才按 SQL 文件的编号顺序导入表结构与选定种子数据。
2. 在 Windows 工作站构建 Java：`mvn -pl sphp-bootstrap -am clean package -DskipTests`；将 `sphp-bootstrap/target/*.jar` 上传为 `/opt/sphp/server/sphp-bootstrap.jar`。
3. 在 Windows 工作站分别执行两套前端的 `npm run build`，上传 C 端产物至 `/var/www/sphp-c`、B 端产物至 `/var/www/sphp-b`。
4. 在服务器为 Agent 创建 Python 虚拟环境，按 `pyproject.toml` 安装生产依赖，包含 `langgraph-checkpoint-postgres`。
5. 将 `systemd` 模板安装为服务单元，填充 Nginx 域名与证书配置后启用站点。
6. 验收 Java API、`/health`、C/B 登录、Agent SSE 与一次 L2 确认流程。

## 网络边界

公网仅开放 `80/443` 与受限 SSH 管理端口。`5432`、`6379`、`5672`、`15672` 必须限制为本机或指定管理 IP；Java `8080` 和 Agent `8081` 只监听回环地址并由 Nginx 转发。

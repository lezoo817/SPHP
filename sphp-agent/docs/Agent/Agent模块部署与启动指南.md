# Agent 模块部署与启动指南

> 适用范围：智愈先锋（SPHP）项目中的 **sphp-agent（Python 模块）**。
> 本文记录 agent 的启动方式（开发/生产）、与 Java 后端的启动关系、以及服务器部署步骤。

---

## 1. Java 与 Agent 需要各自启动吗？

**需要。** 它们是两个相互独立的进程，各自占用不同端口，启动顺序有依赖关系。

| 模块 | 技术栈 | 端口 | 说明 |
|------|--------|------|------|
| 中间件 | PostgreSQL / Redis / RabbitMQ | 5432 / 6379 / 5672 | 先启动，agent 与 Java 共用 |
| Java 后端 | Spring Boot（sphp-bootstrap） | 8080 | 依赖 PG |
| Python Agent | FastAPI + MCP | 8081 | 依赖 PG + Redis，并通过 `JAVA_BASE_URL` 调 Java |
| 前端 C/B 端 | Umi | 8001 / 8000 | 依赖 agent 与 Java |

**启动依赖链**：PostgreSQL → Java → Agent → 前端。

- agent **不内嵌** Java，启动时需要 `JAVA_BASE_URL` 指向可用的 Java 后端（用于注册工具时调 Java API 获取元数据）。
- agent 与 Java **共用**同一套 PG / Redis（连接参数各自从自己的配置文件读取，见 §3）。

---

## 2. 配置来源（agent 只读 `.env`）

Agent 的所有配置（中间件连接、LLM、Java 地址、端口）**只从 `sphp-agent/.env` 读取**，由 `app/infrastructure/config/settings.py` 加载。改环境只改 `.env`，不改 `.py`。

`.env` 已在 `.gitignore` 中，不入库；`sphp-agent/.env.example` 为模板（可入库），密钥留空。

### `.env` 关键配置项

| 键 | 默认（开发） | 生产需改 | 说明 |
|----|------|------|------|
| `AGENT_HOST` / `AGENT_PORT` | `0.0.0.0` / `8081` | 视服务器而定 | `python -m app.main` 时的监听地址 |
| `DEBUG` | `True` | **`False`** | `True` 时 uvicorn 自动 reload；生产必须关闭 |
| `JAVA_BASE_URL` | `http://localhost:8080` | **服务器上的 Java 地址** | agent 调 Java 的入口（含 context-path 前缀由调用方拼接） |
| `PG_HOST/PORT/USER/PASSWORD/DATABASE` | `localhost:5432/sphp/sphp123/sphp` | 服务器 PG | 与 Java 共用同一库 |
| `REDIS_HOST/PORT/PASSWORD` | `localhost:6379` / `123456` | 服务器 Redis | confirm_token / 限流 |
| `RABBITMQ_HOST/PORT/USER/PASSWORD/VHOST` | `guest/guest@5672` | 按需 | 当前未使用，占位 |
| `DEFAULT_LLM_PROVIDER` / `ZHIPU_API_KEY` 等 | `zhipu` + 各 key | 生产 key | LLM 供应商 |
| `EMBEDDING_PROVIDER` / `SILICONFLOW_*` | `siliconflow` + key | 生产 key | 知识库向量化 |

> ⚠️ **开发库与生产库的关键差异**：agent 使用 pgvector 做向量检索。目标 PG 必须已安装 pgvector 扩展（`CREATE EXTENSION vector`），否则向量库首次建表会报错。该项目 **没有** `sql/01-init-pgvector.sql`，扩展需在数据库侧确保（本地 docker 的 `pgvector/pgvector:pg16` 镜像自带该扩展）。

---

## 3. 开发阶段如何启动

### 3.1 前提

1. 中间件已启动（本地 docker 或本机服务），PG/Redis/RabbitMQ 端口与 `.env` 一致。
2. `sphp-agent/.env` 已就绪（含 `ZHIPU_API_KEY`、`SILICONFLOW_API_KEY` 等真实 key）。
3. 依赖已安装：`pip install -e .`（含 `mcp`、`redis`、`psycopg` 等）。
4. Java 后端已启动（agent 注册工具时需要调用它）。

### 3.2 启动命令

```bash
cd sphp-agent

# 方式一（推荐）：读 .env 的 AGENT_HOST/AGENT_PORT，DEBUG=True 自动热重载
python -m app.main

# 方式二：uvicorn 直接指定
python -m uvicorn app.main:app --host 0.0.0.0 --port 8081 --reload
```

### 3.3 验证

```bash
curl http://localhost:8081/health
# 期望: {"status":"healthy","checks":{"pg":"ok","redis":"ok","llm":"ok"},"version":"2.0.0"}

# Swagger 文档
curl http://localhost:8081/docs   # 期望 HTTP 200
```

启动日志中应看到：

```
INFO:  Uvicorn running on http://0.0.0.0:8081
INFO:  Tool schemas registered (C:30, B:9)
INFO:  MCP Server started (stdio transport)
INFO:  Application startup complete.
```

> 若出现 `mcp SDK not installed, MCP Server skipped`，说明 venv 缺少 `mcp` 包，执行 `pip install mcp` 后重启。

---

## 4. 服务器部署阶段如何启动

### 4.1 部署前准备

1. **Python 环境**：服务器装 Python 3.11+，`pip install -e .`（或按 `requirements.txt`）。
2. **`.env` 文件**：在服务器 `sphp-agent/` 下放置生产版 `.env`（`.env` 不入库，需手动放）。
3. **pgvector 扩展**：目标 PG 确认已 `CREATE EXTENSION vector`。
4. **网络/端口**：放开 `8081`；agent 能访问 Java 后端、PG、Redis、RabbitMQ。
5. **`DEBUG=False`**：生产必须关闭 reload 与调试模式。

### 4.2 启动（后台常驻）

```bash
cd sphp-agent
nohup .venv/bin/python -m uvicorn app.main:app \
  --host 0.0.0.0 --port 8081 \
  > agent.log 2>&1 &
```

### 4.3 推荐：systemd 托管（开机自启 + 自动重启）

`/etc/systemd/system/sphp-agent.service`：

```ini
[Unit]
Description=SPHP Agent Service
After=network.target postgresql.service redis.service

[Service]
Type=simple
User=www-data
WorkingDirectory=/path/to/sphp-agent
ExecStart=/path/to/sphp-agent/.venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8081
Restart=always
RestartSec=3
EnvironmentFile=/path/to/sphp-agent/.env

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now sphp-agent
sudo systemctl status sphp-agent     # 查看状态
journalctl -u sphp-agent -f          # 查看日志
```

### 4.4 全项目启动顺序（服务器）

1. 中间件：PG / Redis / RabbitMQ（服务器自身服务或 docker），确认 pgvector 扩展已装。
2. Java 后端：`mvn spring-boot:run`（或 `java -jar xxx.jar`），使用 `application-prod.yml`。
3. Python Agent：上表 systemd 或 nohup 方式，`JAVA_BASE_URL` 指向 Java。
4. 前端：C/B 端 `npm run build` 后静态托管，或开发模式 `npm run dev`。

---

## 5. 常见问题

| 现象 | 原因 | 解决 |
|------|------|------|
| 启动即退出，无日志 | `main.py` 缺 `if __name__ == "__main__": run()` | 确认入口存在 |
| 端口是 8000 而不是 8081 | 误用旧 `.env` 的 `HOST/PORT`（已废弃） | 用 `AGENT_HOST/AGENT_PORT`，删掉 `HOST/PORT` |
| 连不上 PG | `.env` 的 `PG_*` 与真实库不一致 | 核对端口/账号/库名 |
| 向量库建表报错 | 目标 PG 缺 pgvector 扩展 | `CREATE EXTENSION vector` |
| MCP 功能未启用 | venv 缺 `mcp` | `pip install mcp` |
| 生产端口暴露调试 | `DEBUG=True` | 设 `DEBUG=False` |

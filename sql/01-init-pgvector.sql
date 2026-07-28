-- 智愈先锋 - 数据库初始化脚本
-- 此文件在 docker compose up 时自动执行（挂载到 /docker-entrypoint-initdb.d）

-- 启用 pgvector 扩展（向量检索用）
CREATE EXTENSION IF NOT EXISTS vector;

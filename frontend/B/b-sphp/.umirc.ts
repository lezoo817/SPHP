import { defineConfig } from '@umijs/max';
import routes from './config/routes';

export default defineConfig({
  // 生产环境部署到同一站点的 /b/ 路径，开发环境仍从根路径访问。
  base: process.env.NODE_ENV === 'production' ? '/b/' : '/',
  publicPath: process.env.NODE_ENV === 'production' ? '/b/' : '/',
  antd: {},
  access: {},
  model: {},
  initialState: {},
  request: {},
  routes,
  npmClient: 'pnpm',
  // 生产由 Nginx 动态返回 Agent 地址，避免构建产物写死服务器地址。
  headScripts: ['/runtime-config.js'],
  // 多异步页面共享压缩帮助函数时隔离 IIFE，避免生产构建产物符号冲突。
  esbuildMinifyIIFE: true,
  proxy: {
    '/api/b': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
    // 在线问诊 STOMP WebSocket，开发环境转发至统一 Java 后端。
    '/api/ws': {
      target: 'ws://localhost:8080',
      changeOrigin: true,
      ws: true,
    },
    // Agent 服务（:8081）—— AI 辅助面板流式对话与 L2 确认回调
    '/api/chat': {
      target: 'http://localhost:8081',
      changeOrigin: true,
    },
  },
});

import { defineConfig } from '@umijs/max';
import routes from './config/routes';

export default defineConfig({
  antd: {},
  access: {},
  model: {},
  initialState: {},
  request: {},
  routes,
  npmClient: 'pnpm',
  utoopack: {},
  proxy: {
    '/api/b': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    },
    // Agent 服务（:8081）—— AI 辅助面板流式对话与 L2 确认回调
    '/api/chat': {
      target: 'http://localhost:8081',
      changeOrigin: true,
    },
  },
});
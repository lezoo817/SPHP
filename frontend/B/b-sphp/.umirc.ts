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
  },
});
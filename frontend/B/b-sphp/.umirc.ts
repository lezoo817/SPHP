import { defineConfig } from '@umijs/max';
import routes from './config/routes';

export default defineConfig({
  port: 8000,
  antd: {},
  access: {},
  model: {},
  initialState: {},
  request: {},
  layout: {},
  routes,
  npmClient: 'pnpm',
  utoopack: {},
});
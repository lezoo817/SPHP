import { defineConfig } from "umi";

export default defineConfig({
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: 'login/index' },
    { path: '/mine', component: 'mine/index' },
    { path: '/mine/family-members', component: 'mine/family-members' },
    { path: '/mine/health-record', component: 'mine/health-record' },
  ],
  npmClient: 'pnpm',
});

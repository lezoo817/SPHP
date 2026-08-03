import { defineConfig } from "umi";

export default defineConfig({
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: 'login/index' },
    { path: '/home', component: 'home/index' },
    { path: '/home/hospitals', component: 'home/hospitals' },
    { path: '/home/departments', component: 'home/departments' },
    { path: '/home/search', component: 'home/search' },
    { path: '/assistant', component: 'assistant/index' },
    { path: '/assistant/book', component: 'assistant/book' },
    { path: '/assistant/pay/:paymentId', component: 'assistant/pay' },
    { path: '/assistant/pre-consultation/:appointmentId', component: 'assistant/pre-consultation' },
    { path: '/assistant/consultation/:consultationId', component: 'assistant/consultation' },
    { path: '/assistant/prescription/:prescriptionId', component: 'assistant/prescription' },
    { path: '/pharmacy', component: 'pharmacy/index' },
    { path: '/pharmacy/orders', component: 'pharmacy/orders' },
    { path: '/pharmacy/prescription/:prescriptionId', component: 'pharmacy/prescription' },
    { path: '/pharmacy/order/:drugOrderId', component: 'pharmacy/order' },
    { path: '/mine', component: 'mine/index' },
    { path: '/mine/profile', component: 'mine/profile' },
    { path: '/mine/family-members', component: 'mine/family-members' },
    { path: '/mine/health-record', component: 'mine/health-record' },
  ],
  npmClient: 'pnpm',
  // 多个异步页面共用压缩帮助函数时隔离 IIFE，避免生产构建产物命名冲突。
  esbuildMinifyIIFE: true,
});

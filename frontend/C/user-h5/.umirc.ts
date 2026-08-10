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
    { path: '/assistant/doctor/:doctorId', component: 'assistant/doctor' },
    { path: '/assistant/pay/:paymentId', component: 'assistant/pay' },
    { path: '/assistant/pre-consultation/:doctorId', component: 'assistant/pre-consultation' },
    { path: '/assistant/consultation/:consultationId', component: 'assistant/consultation' },
    { path: '/assistant/prescription/:prescriptionId', component: 'assistant/prescription' },
    { path: '/pharmacy', component: 'pharmacy/index' },
    { path: '/pharmacy/orders', component: 'pharmacy/orders' },
    { path: '/pharmacy/prescription/:prescriptionId/inventory', component: 'pharmacy/prescription-inventory' },
    { path: '/pharmacy/prescription/:prescriptionId', component: 'pharmacy/prescription' },
    { path: '/pharmacy/order/:drugOrderId/logistics', component: 'pharmacy/logistics' },
    { path: '/pharmacy/order/:drugOrderId', component: 'pharmacy/order' },
    { path: '/medical-records', component: 'reports/index' },
    { path: '/medical-records/:consultId', component: 'reports/detail' },
    { path: '/reports', component: 'reports/redirect' },
    { path: '/reports/:reportId', component: 'reports/redirect' },
    { path: '/mine/prescriptions', component: 'mine/prescriptions' },
    { path: '/mine/appointments', component: 'mine/appointments' },
    { path: '/mine', component: 'mine/index' },
    { path: '/mine/profile', component: 'mine/profile' },
    { path: '/mine/family-members', component: 'mine/family-members' },
    { path: '/mine/health-record', component: 'mine/health-record' },
    { path: '/mine/addresses', component: 'mine/addresses' },
    { path: '/mine/addresses/new', component: 'mine/address-form' },
    { path: '/mine/addresses/:addressId/edit', component: 'mine/address-form' },
    { path: '/agent', component: 'agent/index' },
    { path: '/mine/medication-plans', component: 'mine/medication-plans' },
    { path: '/mine/follow-ups', component: 'mine/follow-ups' },
    { path: '/mine/notifications', component: 'mine/notifications' },
  ],
  npmClient: 'pnpm',
  // 生产由 Nginx 动态返回运行时 API 地址，开发环境同样加载本地占位配置。
  headScripts: ['/runtime-config.js'],
  proxy: {
    // 开发环境代理 Java 统一后端，生产环境由 Nginx 同域代理 /api。
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
      // 同时转发在线问诊 STOMP WebSocket 握手。
      ws: true,
    },
  },
  // 多个异步页面共用压缩帮助函数时隔离 IIFE，避免生产构建产物命名冲突。
  esbuildMinifyIIFE: true,
  // 关闭 MFSU：与 @tanstack/react-query 存在 React 多实例冲突（hooks 为 null）。
  mfsu: false,
});

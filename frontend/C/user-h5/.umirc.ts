import { defineConfig } from "umi";

export default defineConfig({
  port: 8001,
  routes: [
    { path: "/", component: "index" },
    { path: "/docs", component: "docs" },
  ],
  npmClient: 'pnpm',
  utoopack: {},
});

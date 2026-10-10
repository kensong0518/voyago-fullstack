import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      // 開發時把 /api 轉發到 Spring Boot 後端，避免 CORS 麻煩
      "/api": { target: "http://localhost:8080", changeOrigin: true },
      // WebSocket 即時客服聊天（STOMP），開發時同樣轉發到後端
      "/ws-chat": { target: "ws://localhost:8080", ws: true, changeOrigin: true },
    },
  },
  build: {
    target: "es2020",
    minify: "esbuild",
    cssCodeSplit: true,
    sourcemap: false,
    chunkSizeWarningLimit: 800,
    rollupOptions: {
      output: {
        manualChunks: {
          // 把 vendor 與主程式拆成獨立 chunk，提升瀏覽器快取命中率
          vue: ["vue", "vue-router", "pinia"],
          axios: ["axios"],
        },
      },
    },
  },
});

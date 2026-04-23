import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  base: "/qianxun/",
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      "/QianXunService": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
      },
    },
  },
});

# ───────── Build：Node 20 ─────────
FROM node:20-alpine AS build
WORKDIR /workspace

COPY frontend/package.json frontend/package-lock.json* ./
RUN npm install --no-audit --no-fund

COPY frontend/index.html frontend/vite.config.ts frontend/tsconfig.json frontend/tsconfig.node.json ./
COPY frontend/src ./src
RUN npm run build

# ───────── Runtime：nginx alpine（SSE 友好） ─────────
FROM nginx:1.27-alpine
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80

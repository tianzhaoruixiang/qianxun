# ───────── Build：Node 20 ─────────
FROM node:20-alpine AS build
WORKDIR /workspace

COPY fronted/package.json fronted/package-lock.json* ./
RUN npm install --no-audit --no-fund

COPY fronted/ ./
RUN npm run build

# ───────── Runtime：nginx alpine（SSE 友好） ─────────
FROM nginx:1.27-alpine
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
EXPOSE 80

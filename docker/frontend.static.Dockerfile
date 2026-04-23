# 仅打包已构建好的前端静态资源（frontend/dist），不在镜像内执行 npm。
# 用于在 arm64 宿主机上产出 linux/amd64 镜像，避免 QEMU 下跑 Node/npm 崩溃。
# 使用方式：先在仓库根执行 `cd frontend && npm ci && npm run build`，再：
#   docker build --platform linux/amd64 -f docker/frontend.static.Dockerfile -t qianxun/frontend:dev-amd64 .
FROM nginx:1.27-alpine
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY frontend/dist /usr/share/nginx/html
EXPOSE 80

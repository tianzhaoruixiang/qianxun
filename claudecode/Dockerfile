# 火山 AIO 作智能体执行沙箱基座（Python / 浏览器 / Jupyter / MCP）。
# 不要覆盖 ENTRYPOINT：必须走 /opt/gem/run.sh，由 supervisord 拉起千寻网关。
#
# npm 在宿主机架构安装：AIO 内的 Node 在 QEMU 下会 SIGSEGV，无法交叉执行 npm。
# 用 --os/--cpu 拉取目标平台的 SDK 可选包（linux-x64 / linux-arm64）。
FROM --platform=$BUILDPLATFORM node:22-bookworm-slim AS npm-deps
ARG TARGETARCH
WORKDIR /app
COPY package.json ./
RUN set -eux; \
  case "${TARGETARCH}" in \
    amd64) npm install --omit=dev --os=linux --cpu=x64 ;; \
    arm64) npm install --omit=dev --os=linux --cpu=arm64 ;; \
    *) npm install --omit=dev ;; \
  esac

FROM enterprise-public-cn-beijing.cr.volces.com/vefaas-public/all-in-one-sandbox:latest

COPY requirements.txt /tmp/requirements.txt
RUN pip install --no-cache-dir -r /tmp/requirements.txt \
 && rm -rf /tmp/requirements.txt /root/.cache/pip

WORKDIR /app
COPY package.json ./
COPY --from=npm-deps /app/node_modules ./node_modules
COPY src ./src
COPY templates ./templates
COPY supervisord.qianxun.conf /opt/gem/supervisord/supervisord.qianxun.conf
COPY disable-aio-browser.sh /opt/gem/disable-aio-browser.sh
RUN chmod +x /opt/gem/disable-aio-browser.sh \
 && /opt/gem/disable-aio-browser.sh
# gem 在容器启动时按 uid 1000 创建；构建期先把目录交给 1000。
RUN mkdir -p /opt/data \
 && chown -R 1000:1000 /app /opt/data

ENV NODE_ENV=production
ENV DATA_DIR=/opt/data
ENV PORT=8642
ENV PYTHONUNBUFFERED=1
ENV MPLBACKEND=Agg
EXPOSE 8642 8080
HEALTHCHECK --interval=10s --timeout=5s --retries=20 --start-period=90s \
  CMD wget -qO- http://127.0.0.1:8642/health >/dev/null || exit 1

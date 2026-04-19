#!/usr/bin/env bash
# 千寻 · 一键编译 + 构建镜像 + 启动 + 验证
set -euo pipefail

HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "${HERE}/.."

if [ ! -f .env ]; then
  echo "[up] 未找到 .env，自动从 .env.example 复制"
  cp .env.example .env
fi

echo "[up] 启用 Docker BuildKit"
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

echo "[up] 构建镜像 + 启动容器（首次会拉取 maven/node/doris/hermes 镜像，可能较慢）"
docker compose up -d --build --remove-orphans

echo
echo "[up] 等待后端就绪（轮询 Docker healthcheck，最多 5 分钟）..."
timeout_s=300
interval_s=5
elapsed=0
ok=0

while [ "${elapsed}" -lt "${timeout_s}" ]; do
  health=$(docker inspect --format='{{.State.Health.Status}}' qianxun-backend 2>/dev/null || echo "missing")
  running=$(docker inspect --format='{{.State.Running}}'      qianxun-backend 2>/dev/null || echo "false")

  if [ "${health}" = "healthy" ]; then
    echo "[up] ✓ 后端已就绪（耗时 ${elapsed}s，healthcheck: healthy）"
    ok=1
    break
  fi

  if [ "${running}" != "true" ]; then
    echo "[up] ✗ 后端容器意外退出，最近日志："
    docker logs --tail 100 qianxun-backend || true
    exit 1
  fi

  printf "[up]   %3ds  healthcheck: %s\n" "${elapsed}" "${health}"
  sleep "${interval_s}"
  elapsed=$((elapsed + interval_s))
done

if [ "${ok}" -ne 1 ]; then
  echo "[up] ✗ 后端未在 ${timeout_s}s 内就绪，最近日志："
  docker logs --tail 100 qianxun-backend || true
  exit 1
fi

echo
echo "[up] 调用 verify.sh 做端到端验证"
"${HERE}/verify.sh"

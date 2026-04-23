#!/usr/bin/env bash
# 千寻 · 一键编译 + 构建镜像 + 启动 + 验证
set -euo pipefail

HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "${HERE}/.."

if [ ! -f .env ]; then
  echo "[up] 未找到 .env，自动从 .env.example 复制"
  cp .env.example .env
fi

# ── 创建 bind-mount 数据目录（幂等） ──────────────────────────────────────
echo "[up] 初始化持久化数据目录（./data/）..."
mkdir -p \
  data/doris/fe/meta \
  data/doris/fe/log  \
  data/doris/be/storage \
  data/doris/be/log  \
  data/hermes

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
echo "[up] 等待前端就绪（轮询 Docker healthcheck，最多 60s）..."
fe_ok=0
fe_elapsed=0
fe_timeout=60
while [ "${fe_elapsed}" -lt "${fe_timeout}" ]; do
  fe_health=$(docker inspect --format='{{.State.Health.Status}}' qianxun-frontend 2>/dev/null || echo "missing")
  fe_running=$(docker inspect --format='{{.State.Running}}' qianxun-frontend 2>/dev/null || echo "false")

  if [ "${fe_health}" = "healthy" ]; then
    echo "[up] ✓ 前端已就绪（耗时 ${fe_elapsed}s，healthcheck: healthy）"
    fe_ok=1
    break
  fi

  # 若容器根本没在跑，尝试手动启动一次（应对 compose 竞态）
  if [ "${fe_running}" != "true" ]; then
    echo "[up]   前端容器未运行（状态: ${fe_health}），尝试启动…"
    docker start qianxun-frontend 2>/dev/null || true
  fi

  printf "[up]   %3ds  前端 healthcheck: %s\n" "${fe_elapsed}" "${fe_health}"
  sleep 5
  fe_elapsed=$((fe_elapsed + 5))
done

if [ "${fe_ok}" -ne 1 ]; then
  echo "[up] ⚠ 前端未在 ${fe_timeout}s 内通过 healthcheck，继续验证（可能部分检查失败）"
  docker logs --tail 30 qianxun-frontend 2>/dev/null || true
fi

echo
echo "[up] 调用 verify.sh 做端到端验证"
# 给 Docker 网桥 iptables 规则 10s 沉降时间，
# 避免 compose up 刚完成时端口映射短暂中断导致 verify 误报。
sleep 10
"${HERE}/verify.sh"

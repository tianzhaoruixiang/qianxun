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
  data/tidb \
  data/doris/fe/meta \
  data/doris/fe/log  \
  data/doris/be/storage \
  data/doris/be/log  \
  data/minio \
  data/claudecode
chmod -R a+rwX data/claudecode 2>/dev/null || true

# s3fs 要把 FUSE 挂载传播给 claude-code：先把 data 绑成独立挂载点再标 rshared
DATA_DIR="$(pwd)/data"
make_data_rshared() {
  if ! findmnt -n "${DATA_DIR}" >/dev/null 2>&1; then
    mount --bind "${DATA_DIR}" "${DATA_DIR}" 2>/dev/null || return 1
  fi
  mount --make-rshared "${DATA_DIR}" 2>/dev/null
}

# 无 sudo 时，借 privileged 容器 + 宿主 nsenter 进入宿主编译命名空间
make_data_rshared_via_docker() {
  docker run --rm --privileged --pid=host \
    -v /usr/bin/nsenter:/nsenter:ro \
    alpine:3.20 \
    /nsenter -t 1 -m -- sh -c "
      mkdir -p '${DATA_DIR}/minio' '${DATA_DIR}/claudecode'
      if ! findmnt -n '${DATA_DIR}' >/dev/null 2>&1; then
        mount --bind '${DATA_DIR}' '${DATA_DIR}'
      fi
      mount --make-rshared '${DATA_DIR}'
    "
}

# Claude Code /opt/data：
#   默认 / HOST_S3FS=1   宿主 s3fs → MinIO 桶 claudecode（数据在对象存储）
#   HOST_S3FS=0          本地 ./data/claudecode（Windows / 无 FUSE 时用）
_qx_host_s3fs() {
  if [ -n "${HOST_S3FS:-}" ]; then
    printf '%s' "${HOST_S3FS}"
    return
  fi
  if [ -f .env ]; then
    local v
    v=$(grep -E "^HOST_S3FS=" .env 2>/dev/null | tail -n 1 | cut -d= -f2-)
    v=${v#\"}; v=${v%\"}; v=${v#\'}; v=${v%\'}
    if [ -n "${v}" ]; then
      printf '%s' "${v}"
      return
    fi
  fi
  printf '1'
}
HOST_S3FS="$(_qx_host_s3fs)"
export HOST_S3FS

if [ "${HOST_S3FS}" = "1" ]; then
  echo "[up] HOST_S3FS=1：将用宿主 s3fs 把 MinIO claudecode 桶挂到 ./data/claudecode"
  if make_data_rshared || make_data_rshared_via_docker; then
    echo "[up] 已将 ${DATA_DIR} 设为 rshared（s3fs 挂载可传播进容器）"
  else
    echo "[up] ⚠ 未能把 ${DATA_DIR} 设为 rshared，Claude Code 可能看不到 MinIO 挂载"
  fi
  export HERMES_SEED_MIGRATE=1
  export CLAUDE_SEED_MIGRATE=1
else
  echo "[up] Claude Code 使用本地 ./data/claudecode（HOST_S3FS=${HOST_S3FS}；用户上传仍走 MinIO）"
fi

echo "[up] 启用 Docker BuildKit"
export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1

# Claude 上游：anthropic 直连 | openai 经 LiteLLM（方案 A）
# shellcheck source=claude-upstream-env.sh
source "${HERE}/claude-upstream-env.sh"

echo "[up] 构建镜像 + 启动容器（首次会拉取 maven/node/tidb/litellm 镜像，可能较慢）"
COMPOSE_ARGS=(up -d --build --remove-orphans)

if [ "${HOST_S3FS}" = "1" ]; then
  # 先起 MinIO 并完成桶初始化/本地种子迁移，再挂宿主 s3fs，最后起 Claude Code
  echo "[up] 先启动 MinIO / minio-init…"
  docker compose up -d minio minio-init
  echo "[up] 等待 minio-init 完成…"
  init_wait=0
  while [ "${init_wait}" -lt 180 ]; do
    st=$(docker inspect --format='{{.State.Status}}' qianxun-minio-init 2>/dev/null || echo "missing")
    exit_code=$(docker inspect --format='{{.State.ExitCode}}' qianxun-minio-init 2>/dev/null || echo "1")
    if [ "${st}" = "exited" ] && [ "${exit_code}" = "0" ]; then
      echo "[up] ✓ minio-init 已完成"
      break
    fi
    if [ "${st}" = "exited" ] && [ "${exit_code}" != "0" ]; then
      echo "[up] ✗ minio-init 失败，日志：" >&2
      docker logs --tail 80 qianxun-minio-init || true
      exit 1
    fi
    sleep 2
    init_wait=$((init_wait + 2))
  done
  if [ "${init_wait}" -ge 180 ]; then
    echo "[up] ✗ minio-init 超时" >&2
    docker logs --tail 80 qianxun-minio-init || true
    exit 1
  fi
  "${HERE}/host-s3fs.sh" mount
fi

docker compose "${COMPOSE_ARGS[@]}"

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

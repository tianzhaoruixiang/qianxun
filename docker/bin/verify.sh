#!/usr/bin/env bash
# 千寻 · 一键验证：覆盖 docker 状态 / Claude Code / 后端 / 前端 / SSE 端到端
set -uo pipefail

HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "${HERE}/.."

# 与 up.sh 一致：openai 模式时覆盖 ANTHROPIC_* 指向 LiteLLM
# shellcheck source=claude-upstream-env.sh
source "${HERE}/claude-upstream-env.sh"

# 从 .env 提取需要的变量（不 source，避免值里含空格的行被 shell 解析）
api_body() {
  local json="${1:-{}}"
  printf '{"jsonArg":%s,"generalArgument":{"userId":"1","loginName":"admin","ip":"127.0.0.1","coralKey":""}}' "${json}"
}

read_env() {
  local key="$1" def="$2"
  local v
  v=$(grep -E "^${key}=" .env 2>/dev/null | tail -n 1 | cut -d= -f2-)
  v=${v#\"}; v=${v%\"}
  echo "${v:-$def}"
}
BACKEND_PORT=$(read_env QIANXUN_BACKEND_PORT 8080)
FRONTEND_PORT=$(read_env QIANXUN_FRONTEND_PORT 80)
CLAUDE_PORT=$(read_env CLAUDE_CODE_PORT 8642)
LITELLM_PORT=$(read_env LITELLM_PORT 4001)
UPSTREAM_MODE=$(read_env CLAUDE_UPSTREAM_MODE anthropic)
DORIS_HTTP=$(read_env DORIS_FE_HTTP_PORT 8030)
MINIO_API=$(read_env MINIO_API_PORT 9000)
GATEWAY_KEY=$(read_env CLAUDE_GATEWAY_KEY "")
SSE_TIMEOUT=$(read_env QIANXUN_VERIFY_SSE_TIMEOUT 60)

pass=0; fail=0
check() {
  local name="$1"; shift
  if "$@" >/tmp/qx-check.out 2>&1; then
    echo "  ✓ ${name}"
    pass=$((pass+1))
  else
    echo "  ✗ ${name}"
    sed 's/^/      /' /tmp/qx-check.out | head -n 8
    fail=$((fail+1))
  fi
}

echo "==> docker compose ps"
docker compose ps

# 等待所有关键服务端口可达（给容器内进程留缓冲时间，避免 verify.sh 跑太快）
echo
echo "==> 等待各服务端口可达（最多 90s）..."
# 第 5 个参数：Docker 容器名（可选）。
# 优先 HTTP 200 探测；若 Docker healthcheck 已显示 healthy，则直接信任，
# 避免 compose up 期间 iptables DNAT 规则刷新导致端口映射短暂失效。
wait_port() {
  local name="$1" host="$2" port="$3" path="${4:-/}" cname="${5:-}"
  local url="http://${host}:${port}${path}"
  for i in $(seq 1 18); do   # 最多 18 × 5s ≈ 90s
    # 1) HTTP 探测：必须 2xx
    if curl -fsS --max-time 4 "${url}" >/dev/null 2>&1; then
      echo "  ✓ ${name} 可达（HTTP 200）"
      return 0
    fi
    # 2) 兜底：Docker healthcheck 已通过 → 认为服务可用（iptables 规则可能还在刷新）
    if [ -n "${cname}" ]; then
      local h
      h=$(docker inspect --format='{{.State.Health.Status}}' "${cname}" 2>/dev/null || echo "")
      if [ "${h}" = "healthy" ]; then
        echo "  ✓ ${name} 可达（Docker healthcheck: healthy，HTTP 端口映射刷新中）"
        return 0
      fi
    fi
    sleep 5
  done
  echo "  ⚠ ${name} 90s 内未可达，继续验证..."
}
wait_port "MinIO"     127.0.0.1 "${MINIO_API}"     "/minio/health/live" "qianxun-minio"
if docker inspect qianxun-doris-fe >/dev/null 2>&1; then
  wait_port "Doris FE"  127.0.0.1 "${DORIS_HTTP}"    "/api/health"   "qianxun-doris-fe"
fi
if docker inspect qianxun-litellm >/dev/null 2>&1; then
  wait_port "LiteLLM" 127.0.0.1 "${LITELLM_PORT}" "/health/liveliness" "qianxun-litellm"
fi
wait_port "Claude Code" 127.0.0.1 "${CLAUDE_PORT}" "/health" "qianxun-claude-code"
wait_port "后端"      127.0.0.1 "${BACKEND_PORT}"  "/QianXunService/sessions/list" "qianxun-backend"
wait_port "前端"      127.0.0.1 "${FRONTEND_PORT}" "/"             "qianxun-frontend"

echo
echo "==> 服务健康"
check "MinIO /minio/health/live" curl -fsS --max-time 8 "http://127.0.0.1:${MINIO_API}/minio/health/live"
DATA_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
HOST_S3FS_EXPECT="${HOST_S3FS:-$(read_env HOST_S3FS 1)}"
if [ "${HOST_S3FS_EXPECT}" = "1" ]; then
  check "宿主 ./data/claudecode 为 s3fs" sh -c "findmnt -n '${DATA_ROOT}/data/claudecode' | grep -qiE 's3fs|fuse'"
  check "Claude Code /opt/data 为 MinIO(宿主 s3fs)" docker exec qianxun-claude-code sh -c "awk '\$2==\"/opt/data\" && \$1 ~ /s3fs|fuse/ {found=1} END{exit found?0:1}' /proc/mounts"
else
  echo "  · Claude Code /opt/data 为本地目录（HOST_S3FS=${HOST_S3FS_EXPECT}；用户上传仍走 MinIO）"
fi
if docker inspect qianxun-doris-fe >/dev/null 2>&1; then
  check "Doris FE /api/health"  curl -fsS --max-time 8 "http://127.0.0.1:${DORIS_HTTP}/api/health"
fi
if docker inspect qianxun-litellm >/dev/null 2>&1; then
  check "LiteLLM /health/liveliness" curl -fsS --max-time 8 "http://127.0.0.1:${LITELLM_PORT}/health/liveliness"
  echo "  · CLAUDE_UPSTREAM_MODE=${UPSTREAM_MODE}（openai 时 sidecar 经 LiteLLM）"
fi
check "Claude Code /health"   curl -fsS --max-time 8 "http://127.0.0.1:${CLAUDE_PORT}/health"
if [ -n "${GATEWAY_KEY}" ]; then
  check "Claude Code /v1/models" curl -fsS --max-time 8 -H "Authorization: Bearer ${GATEWAY_KEY}" "http://127.0.0.1:${CLAUDE_PORT}/v1/models"
else
  check "Claude Code /v1/models" curl -fsS --max-time 8 "http://127.0.0.1:${CLAUDE_PORT}/v1/models"
fi
check "后端 /QianXunService/sessions/list"    curl -fsS --max-time 8 -X POST -H 'Content-Type: application/json' -d "$(api_body '{}')" "http://127.0.0.1:${BACKEND_PORT}/QianXunService/sessions/list"
check "前端首页 200"           curl -fsS --max-time 8 -o /dev/null "http://127.0.0.1:${FRONTEND_PORT}/"
check "前端→后端 反向代理"      curl -fsS --max-time 8 -X POST -H 'Content-Type: application/json' -d "$(api_body '{}')" "http://127.0.0.1:${FRONTEND_PORT}/QianXunService/sessions/list"

echo
echo "==> 端到端 SSE 流式聊天（最多 ${SSE_TIMEOUT}s，看到 event:started / event:token 即视为通过）"
SID=$(curl -sS --max-time 10 -X POST "http://127.0.0.1:${BACKEND_PORT}/QianXunService/sessions/create" \
  -H 'Content-Type: application/json' -d "$(api_body '{}')" \
  | python3 -c "import sys,json; d=json.load(sys.stdin).get('data',{}); print(d.get('id',''))" 2>/dev/null || true)
echo "  session=${SID}"
if [ -z "${SID}" ]; then
  echo "  ✗ 无法创建会话（请检查后端日志）"
  fail=$((fail+1))
else
  curl -sS -N -X POST "http://127.0.0.1:${BACKEND_PORT}/QianXunService/sessions/${SID}/chat/stream" \
    -H 'Content-Type: application/json' -H 'Accept: text/event-stream' \
    -d '{"content":"帮我调研一下字节跳动的最新动态"}' \
    --max-time "${SSE_TIMEOUT}" -o /tmp/qx-sse.out 2>/dev/null || true
  echo "  -- 接收到的 SSE 事件类型："
  grep -E '^event:' /tmp/qx-sse.out | sort -u | sed 's/^/      /'
  if grep -qE '^event:(started|token|done)' /tmp/qx-sse.out; then
    echo "  ✓ 流式事件命中"
    pass=$((pass+1))
  else
    echo "  ✗ 未收到 started/token/done 事件（可调大 QIANXUN_VERIFY_SSE_TIMEOUT）"
    fail=$((fail+1))
  fi
fi

echo
if [ "${fail}" -eq 0 ]; then
  echo "✓ 验证完成：${pass} 通过 / 0 失败"
else
  echo "✗ 验证完成：${pass} 通过 / ${fail} 失败（请查看上方失败详情与 \`docker compose logs <服务名>\`）"
  exit 1
fi

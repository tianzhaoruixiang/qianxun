#!/usr/bin/env bash
# 千寻 · 一键验证：覆盖 docker 状态 / Doris / Hermes / 后端 / 前端 / SSE 端到端
set -uo pipefail

HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "${HERE}/.."

# 从 .env 提取需要的变量（不 source，避免值里含空格的行被 shell 解析）
read_env() {
  local key="$1" def="$2"
  local v
  v=$(grep -E "^${key}=" .env 2>/dev/null | tail -n 1 | cut -d= -f2-)
  v=${v#\"}; v=${v%\"}
  echo "${v:-$def}"
}
BACKEND_PORT=$(read_env QIANXUN_BACKEND_PORT 8080)
FRONTEND_PORT=$(read_env QIANXUN_FRONTEND_PORT 5173)
HERMES_PORT=$(read_env HERMES_API_PORT 8642)
DORIS_HTTP=$(read_env DORIS_FE_HTTP_PORT 8030)
TOKEN=$(read_env API_SERVER_KEY qianxun-local-dev-key)
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
echo "==> 等待各服务端口可达（最多 60s）..."
wait_port() {
  local name="$1" host="$2" port="$3"
  for i in $(seq 1 12); do
    if curl -fsS --max-time 3 "http://${host}:${port}" >/dev/null 2>&1 || \
       curl -fsS --max-time 3 "http://${host}:${port}/api/health" >/dev/null 2>&1 || \
       curl -fsS --max-time 3 "http://${host}:${port}/health" >/dev/null 2>&1 || \
       curl -o /dev/null -s --max-time 3 "http://${host}:${port}" >/dev/null 2>&1; then
      echo "  ✓ ${name} 可达"
      return 0
    fi
    sleep 5
  done
  echo "  ⚠ ${name} 60s 内未可达，继续验证..."
}
wait_port "Hermes:${HERMES_PORT}"  127.0.0.1 "${HERMES_PORT}"
wait_port "后端:${BACKEND_PORT}"   127.0.0.1 "${BACKEND_PORT}"

echo
echo "==> 服务健康"
check "Doris FE /api/health"  curl -fsS --max-time 8 "http://127.0.0.1:${DORIS_HTTP}/api/health"
check "Hermes /health"        curl -fsS --max-time 8 "http://127.0.0.1:${HERMES_PORT}/health"
check "Hermes /v1/models"     curl -fsS --max-time 8 -H "Authorization: Bearer ${TOKEN}" "http://127.0.0.1:${HERMES_PORT}/v1/models"
check "后端 /api/sessions"    curl -fsS --max-time 8 "http://127.0.0.1:${BACKEND_PORT}/api/sessions"
check "前端首页 200"           curl -fsS --max-time 8 -o /dev/null "http://127.0.0.1:${FRONTEND_PORT}/"
check "前端→后端 反向代理"      curl -fsS --max-time 8 "http://127.0.0.1:${FRONTEND_PORT}/api/intent-scenarios"

echo
echo "==> 默认意图场景（应该至少包含 org_research / person_research / general）"
scenario_json=$(curl -sS --max-time 8 "http://127.0.0.1:${BACKEND_PORT}/api/intent-scenarios?enabledOnly=true" 2>&1)
if echo "${scenario_json}" | python3 -c "import sys,json; d=json.load(sys.stdin); print('  共', len(d), '个场景:', [x['code'] for x in d])" 2>/dev/null; then
  :
else
  echo "  ✗ 无法获取场景列表（HTTP 响应：$(echo "${scenario_json}" | head -c 120)）"
fi

echo
echo "==> 端到端 SSE 流式聊天（最多 ${SSE_TIMEOUT}s，看到 event:analysis / event:token 即视为通过）"
SID=$(curl -sS -X POST "http://127.0.0.1:${BACKEND_PORT}/api/sessions" -H 'Content-Type: application/json' -d '{}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
echo "  session=${SID}"
curl -sS -N -X POST "http://127.0.0.1:${BACKEND_PORT}/api/sessions/${SID}/chat/stream" \
  -H 'Content-Type: application/json' -H 'Accept: text/event-stream' \
  -d '{"content":"帮我调研一下字节跳动的最新动态"}' \
  --max-time "${SSE_TIMEOUT}" -o /tmp/qx-sse.out 2>/dev/null || true
echo "  -- 接收到的 SSE 事件类型："
grep -E '^event:' /tmp/qx-sse.out | sort -u | sed 's/^/      /'
echo "  -- analysis 事件："
grep -A1 '^event:analysis' /tmp/qx-sse.out | sed 's/^/      /' | head -2

HERMES_ENABLED=$(read_env QIANXUN_HERMES_ENABLED true)
if grep -q '^event:analysis' /tmp/qx-sse.out; then
  echo "  ✓ NLU analysis 事件命中"
  pass=$((pass+1))
elif [ "${HERMES_ENABLED}" = "true" ] || [ "${HERMES_ENABLED}" = "1" ]; then
  echo "  ✗ 未收到 NLU analysis 事件（Hermes 通常 10~60s，可调大 QIANXUN_VERIFY_SSE_TIMEOUT）"
  fail=$((fail+1))
else
  echo "  • Hermes 未启用，跳过 NLU 检查"
fi

echo
if [ "${fail}" -eq 0 ]; then
  echo "✓ 验证完成：${pass} 通过 / 0 失败"
else
  echo "✗ 验证完成：${pass} 通过 / ${fail} 失败（请查看上方失败详情与 \`docker compose logs <服务名>\`）"
  exit 1
fi

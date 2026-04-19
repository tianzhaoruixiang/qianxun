#!/usr/bin/env bash
# 千寻 · Doris 初始化引导：等 FE 可登录、BE alive 后顺序执行 /init/*.sql
set -euo pipefail

FE_HOST="${FE_HOST:-doris-fe}"
FE_PORT="${FE_PORT:-9030}"
USER="${DORIS_USER:-root}"
PASSWORD="${DORIS_PASSWORD:-}"
RETRIES="${RETRIES:-60}"
SLEEP="${SLEEP:-3}"

mysql_args=( -h "${FE_HOST}" -P "${FE_PORT}" -u "${USER}" --protocol=tcp --default-character-set=utf8mb4 )
[ -n "${PASSWORD}" ] && mysql_args+=( -p"${PASSWORD}" )

log() { echo "[doris-init] $*"; }

log "Wait FE login on ${FE_HOST}:${FE_PORT}..."
for i in $(seq 1 "${RETRIES}"); do
  if mysql "${mysql_args[@]}" -N -e "SELECT 1" >/dev/null 2>&1; then
    log "FE login OK"
    break
  fi
  log "FE not ready yet (attempt ${i}/${RETRIES}), retrying in ${SLEEP}s..."
  sleep "${SLEEP}"
done

log "Wait BE alive..."
alive=0
for i in $(seq 1 "${RETRIES}"); do
  alive=$(mysql "${mysql_args[@]}" -N -B -e "SHOW BACKENDS\G" 2>/dev/null \
          | awk -F': *' '/^[[:space:]]*Alive:/ {print $2}' \
          | grep -c '^true$' || true)
  alive=${alive:-0}
  if [ "${alive}" -ge 1 ]; then
    log "BE alive=${alive}"
    break
  fi
  log "BE not alive yet (attempt ${i}/${RETRIES}, alive=${alive}), retrying in ${SLEEP}s..."
  sleep "${SLEEP}"
done

if [ "${alive}" -lt 1 ]; then
  log "WARNING: 仍未检测到存活的 BE，将继续尝试执行 SQL（建表可能因复制数失败）"
fi

shopt -s nullglob
files=(/init/*.sql)
shopt -u nullglob
if [ ${#files[@]} -eq 0 ]; then
  log "/init 目录无 .sql 文件，跳过"
  exit 0
fi

# 按文件名字典序顺序执行
IFS=$'\n' sorted=( $(printf "%s\n" "${files[@]}" | sort) )
warn_count=0
for f in "${sorted[@]}"; do
  log "Apply ${f} ..."
  # --force: 单条 SQL 报错（如列已存在）不中止，继续执行剩余语句（幂等场景）
  # if/else 捕获非零退出码，避免 set -e 提前终止脚本
  if mysql "${mysql_args[@]}" --force < "${f}"; then
    log "OK: ${f}"
  else
    log "WARN: ${f} - some SQL statements failed (likely idempotent: column/table already exists)"
    warn_count=$((warn_count + 1))
  fi
done

if [ "${warn_count}" -gt 0 ]; then
  log "完成（${warn_count} 个文件含 SQL warning，通常为幂等冲突，可安全忽略）"
else
  log "完成（所有 SQL 执行成功）"
fi

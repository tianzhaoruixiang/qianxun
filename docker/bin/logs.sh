#!/usr/bin/env bash
# 千寻 · 查看日志（可指定服务名，例如 ./bin/logs.sh qianxun-backend）
set -euo pipefail
HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "${HERE}/.."
if [ "$#" -eq 0 ]; then
  docker compose logs -f --tail=100
else
  docker compose logs -f --tail=200 "$@"
fi

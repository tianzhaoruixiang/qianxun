#!/usr/bin/env bash
# 千寻 · 关停（透传任意参数到 docker compose down，例如 `-v` 删除数据卷）
set -euo pipefail
HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "${HERE}/.."
docker compose down "$@"

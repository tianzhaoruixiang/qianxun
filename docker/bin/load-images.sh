#!/usr/bin/env bash
# 将 docker/image-out/ 中的镜像包载入本机 Docker（内网离线部署）。
#
# 用法：
#   ./docker/bin/load-images.sh [目录]
# 默认目录：<仓库>/docker/image-out
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
IN_DIR="${1:-${OUT_DIR:-$REPO_ROOT/docker/image-out}}"

if ! docker info >/dev/null 2>&1; then
  echo "错误：无法连接 Docker，请先启动 Docker 守护进程。" >&2
  exit 1
fi

if [[ ! -d "${IN_DIR}" ]]; then
  echo "错误：目录不存在：${IN_DIR}" >&2
  exit 1
fi

shopt -s nullglob
files=("${IN_DIR}"/*.tar.gz "${IN_DIR}"/*.tgz "${IN_DIR}"/*.tar)
if [[ ${#files[@]} -eq 0 ]]; then
  echo "错误：${IN_DIR} 下没有 .tar.gz / .tar 镜像包。" >&2
  exit 1
fi

echo "从 ${IN_DIR} 载入 ${#files[@]} 个文件…"
for f in "${files[@]}"; do
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "载入 $(basename "${f}") …"
  case "${f}" in
    *.tar.gz|*.tgz) gunzip -c "${f}" | docker load ;;
    *.tar) docker load -i "${f}" ;;
  esac
done

echo ""
echo "全部载入完成。接着可："
echo "  cd docker && ./bin/up.sh"

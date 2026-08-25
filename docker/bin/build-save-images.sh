#!/usr/bin/env bash
# 构建千寻前后端镜像（linux/amd64 与 linux/arm64），并保存为 tar.gz 到 docker/image-out/
#
# 用法（在仓库任意目录）：
#   ./docker/bin/build-save-images.sh [TAG]
#
# 环境变量：
#   OUT_DIR          输出目录，默认 <仓库>/docker/image-out
#   NO_COMPRESS=1    保存为 .tar 不压缩
#   BINFMT_INSTALL=1 先执行 tonistiigi/binfmt 安装（跨架构构建在部分主机上必需）
#   PRUNE_AFTER=1    保存后删除本地刚构建的镜像标签以省空间
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

TAG="${1:-$(date +%Y%m%d)-$(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo local)}"
OUT_DIR="${OUT_DIR:-$REPO_ROOT/docker/image-out}"
mkdir -p "$OUT_DIR"

if ! docker info >/dev/null 2>&1; then
  echo "错误：无法连接 Docker，请先启动 Docker 守护进程。" >&2
  exit 1
fi

docker buildx version >/dev/null 2>&1 || {
  echo "错误：需要 Docker Buildx，请升级 Docker Desktop / 安装 buildx 插件。" >&2
  exit 1
}

if [[ "${BINFMT_INSTALL:-0}" == "1" ]]; then
  echo "安装 binfmt（跨架构 QEMU 仿真）…"
  docker run --privileged --rm tonistiigi/binfmt --install all
fi

build_save() {
  local platform=$1
  local suffix=$2
  local name=$3
  local dockerfile=$4

  local image="qianxun/${name}:${TAG}-${suffix}"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "构建 ${image}（${platform}）…"

  docker buildx build \
    --platform "${platform}" \
    -f "${dockerfile}" \
    -t "${image}" \
    --load \
    "${REPO_ROOT}"

  local base="${OUT_DIR}/qianxun-${name}-${suffix}-${TAG}"
  local tmp="${base}.tar.tmp"
  if [[ "${NO_COMPRESS:-0}" == "1" ]]; then
    docker save -o "${tmp}" "${image}"
    mv -f "${tmp}" "${base}.tar"
    echo "已保存 ${base}.tar"
  else
    docker save "${image}" | gzip -1 > "${tmp}"
    mv -f "${tmp}" "${base}.tar.gz"
    echo "已保存 ${base}.tar.gz"
  fi

  if [[ "${PRUNE_AFTER:-0}" == "1" ]]; then
    docker rmi "${image}" >/dev/null 2>&1 || true
  fi
}

echo "输出目录: ${OUT_DIR}"
echo "镜像标签前缀: ${TAG}"
echo ""

for pair in linux/amd64:amd64 linux/arm64:arm64; do
  platform="${pair%%:*}"
  suffix="${pair##*:}"
  build_save "${platform}" "${suffix}" backend docker/backend.Dockerfile
  build_save "${platform}" "${suffix}" frontend docker/frontend.Dockerfile
done

echo ""
echo "全部完成。载入示例："
echo "  gunzip -c docker/image-out/qianxun-backend-amd64-${TAG}.tar.gz | docker load"
echo "  gunzip -c docker/image-out/qianxun-frontend-arm64-${TAG}.tar.gz | docker load"

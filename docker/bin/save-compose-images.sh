#!/usr/bin/env bash
# 将 docker-compose.yml 中的全部运行时镜像导出为 tar.gz，便于内网离线部署。
#
# 用法（在仓库任意目录）：
#   ./docker/bin/save-compose-images.sh
#
# 环境变量：
#   OUT_DIR          输出目录，默认 <仓库>/docker/image-out
#   NO_COMPRESS=1    保存为 .tar 不压缩
#   PULL=1           导出前先 docker compose pull（缺镜像时建议开启）
#
# 内网载入：
#   ./docker/bin/load-images.sh
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_DIR="${REPO_ROOT}/docker"
OUT_DIR="${OUT_DIR:-$REPO_ROOT/docker/image-out}"
mkdir -p "$OUT_DIR"

if ! docker info >/dev/null 2>&1; then
  echo "错误：无法连接 Docker，请先启动 Docker 守护进程。" >&2
  exit 1
fi

cd "$COMPOSE_DIR"
if [[ ! -f .env && -f .env.example ]]; then
  echo "未找到 .env，使用 .env.example 解析镜像标签。"
fi

if [[ "${PULL:-0}" == "1" ]]; then
  echo "拉取 compose 中的第三方镜像…"
  docker compose pull --ignore-buildable
fi

mapfile -t IMAGES < <(docker compose config --images | awk 'NF && !seen[$0]++')
if [[ ${#IMAGES[@]} -eq 0 ]]; then
  echo "错误：未能从 docker compose 解析到任何镜像。" >&2
  exit 1
fi

safe_name() {
  # pingcap/tidb:v7.5.3 → pingcap_tidb_v7.5.3
  echo "$1" | sed -e 's#[/:]#_#g' -e 's#[^A-Za-z0-9._-]#_#g'
}

MANIFEST="${OUT_DIR}/MANIFEST.txt"
{
  echo "# qianxun compose images"
  echo "# generated: $(date -Iseconds)"
  echo "# host-arch: $(docker version --format '{{.Server.Arch}}' 2>/dev/null || uname -m)"
  echo "#"
} > "${MANIFEST}.tmp"

echo "输出目录: ${OUT_DIR}"
echo "共 ${#IMAGES[@]} 个镜像"
echo ""

missing=0
for image in "${IMAGES[@]}"; do
  if ! docker image inspect "${image}" >/dev/null 2>&1; then
    echo "缺失: ${image}"
    missing=1
  fi
done
if [[ "${missing}" -eq 1 ]]; then
  echo "" >&2
  echo "错误：以上镜像本地不存在。可先构建前后端，或设置 PULL=1 再跑本脚本：" >&2
  echo "  docker compose -f ${COMPOSE_DIR}/docker-compose.yml build" >&2
  echo "  PULL=1 ./docker/bin/save-compose-images.sh" >&2
  rm -f "${MANIFEST}.tmp"
  exit 1
fi

for image in "${IMAGES[@]}"; do
  local_name="$(safe_name "${image}")"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "保存 ${image} …"
  size="$(docker image inspect -f '{{.Size}}' "${image}")"
  echo "  大小: $(numfmt --to=iec --suffix=B "${size}" 2>/dev/null || echo "${size} bytes")"

  if [[ "${NO_COMPRESS:-0}" == "1" ]]; then
    out="${OUT_DIR}/${local_name}.tar"
    tmp="${out}.tmp"
    docker save -o "${tmp}" "${image}"
    mv -f "${tmp}" "${out}"
  else
    out="${OUT_DIR}/${local_name}.tar.gz"
    tmp="${out}.tmp"
    docker save "${image}" | gzip -1 > "${tmp}"
    mv -f "${tmp}" "${out}"
  fi
  echo "已保存 ${out}"
  echo "${image}  $(basename "${out}")" >> "${MANIFEST}.tmp"
done

mv -f "${MANIFEST}.tmp" "${MANIFEST}"

echo ""
echo "全部完成。清单: ${MANIFEST}"
echo "内网载入："
echo "  ./docker/bin/load-images.sh"
echo "  # 或: for f in ${OUT_DIR}/*.tar.gz; do gunzip -c \"\$f\" | docker load; done"

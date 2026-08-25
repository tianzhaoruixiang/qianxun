#!/usr/bin/env bash
# 千寻 · 关停容器。默认保留数据（含 ./data/tidb 等 bind 目录）。
# 需要清空 TiDB 时请用 ./bin/destroy-data.sh --yes，不要对 down 传 -v。
set -euo pipefail
HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "${HERE}/.."

for arg in "$@"; do
  case "${arg}" in
    -v|--volumes)
      echo "[down] 拒绝 ${arg}：正常关停必须保留 ./data 下的持久化数据。" >&2
      echo "[down] 若确定要清空库，请执行：${HERE}/destroy-data.sh --yes" >&2
      exit 1
      ;;
  esac
done

# 先卸 s3fs，避免 compose down 后宿主留下陈旧 FUSE 挂载
CLAUDE_MNT="$(pwd)/data/claudecode"
if mountpoint -q "${CLAUDE_MNT}" 2>/dev/null || findmnt -n "${CLAUDE_MNT}" >/dev/null 2>&1; then
  echo "[down] 卸载 data/claudecode 上的 s3fs"
  if [[ -x "${HERE}/host-s3fs.sh" ]]; then
    "${HERE}/host-s3fs.sh" unmount || true
  fi
  fusermount -u "${CLAUDE_MNT}" 2>/dev/null || umount "${CLAUDE_MNT}" 2>/dev/null || \
    docker run --rm --privileged --pid=host alpine:3.20 sh -c "
      apk add --no-cache util-linux >/dev/null
      nsenter -t 1 -m -- umount '${CLAUDE_MNT}'
    " || true
fi

docker compose down "$@"

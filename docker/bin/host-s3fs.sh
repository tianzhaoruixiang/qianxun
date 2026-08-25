#!/usr/bin/env bash
# 用宿主 s3fs 将 MinIO claudecode 桶挂到 docker/data/claudecode（数据在 MinIO，目录仅为挂载点）。
#
# 用法：
#   ./docker/bin/host-s3fs.sh mount
#   ./docker/bin/host-s3fs.sh unmount
#   ./docker/bin/host-s3fs.sh status
#
set -euo pipefail

HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$(cd -- "${HERE}/.." && pwd)"
cd "${COMPOSE_DIR}"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

CLAUDE_MNT="${COMPOSE_DIR}/data/claudecode"
BUCKET="${MINIO_CLAUDE_BUCKET:-${MINIO_HERMES_BUCKET:-claudecode}}"
MINIO_API_PORT="${MINIO_API_PORT:-9000}"
MINIO_URL="${HOST_S3FS_URL:-http://127.0.0.1:${MINIO_API_PORT}}"
ACCESS_KEY="${MINIO_ROOT_USER:-qianxun}"
SECRET_KEY="${MINIO_ROOT_PASSWORD:-qianxun-minio-dev}"
PASSWD_FILE="${COMPOSE_DIR}/data/.s3fs-passwd-claudecode"
# up.sh 默认 HOST_S3FS=1，先起 MinIO 再挂本目录；HOST_S3FS=0 时不调用本脚本
S3FS_OPTS="${HOST_S3FS_OPTS:-use_path_request_style,allow_other,umask=000,complement_stat,nonempty,compat_dir}"

is_mounted() {
  findmnt -n "${CLAUDE_MNT}" >/dev/null 2>&1 || mountpoint -q "${CLAUDE_MNT}" 2>/dev/null
}

cmd_status() {
  if is_mounted; then
    findmnt -n -o SOURCE,FSTYPE,TARGET "${CLAUDE_MNT}" || mount | grep -F "${CLAUDE_MNT}" || true
    return 0
  fi
  echo "未挂载: ${CLAUDE_MNT}"
  return 1
}

cmd_unmount() {
  if ! is_mounted; then
    echo "[host-s3fs] ${CLAUDE_MNT} 未挂载"
    return 0
  fi
  echo "[host-s3fs] 卸载 ${CLAUDE_MNT}"
  if command -v fusermount3 >/dev/null 2>&1; then
    fusermount3 -u "${CLAUDE_MNT}" 2>/dev/null && return 0
  fi
  if command -v fusermount >/dev/null 2>&1; then
    fusermount -u "${CLAUDE_MNT}" 2>/dev/null && return 0
  fi
  umount "${CLAUDE_MNT}" 2>/dev/null && return 0
  umount -l "${CLAUDE_MNT}" 2>/dev/null || true
}

require_s3fs() {
  if command -v s3fs >/dev/null 2>&1; then
    return 0
  fi
  echo "[host-s3fs] 错误：宿主未找到 s3fs 命令。" >&2
  echo "[host-s3fs] OpenEuler 示例：sudo dnf install -y s3fs-fuse fuse" >&2
  echo "[host-s3fs] 并确保 /etc/fuse.conf 含：user_allow_other" >&2
  exit 1
}

cmd_mount() {
  require_s3fs
  mkdir -p "${CLAUDE_MNT}"

  if is_mounted; then
    echo "[host-s3fs] 已挂载，跳过"
    cmd_status || true
    return 0
  fi

  if ! grep -qE '^\s*user_allow_other\s*$' /etc/fuse.conf 2>/dev/null; then
    echo "[host-s3fs] 警告：/etc/fuse.conf 未见 user_allow_other，allow_other 可能失败" >&2
  fi

  umask 077
  printf '%s:%s\n' "${ACCESS_KEY}" "${SECRET_KEY}" > "${PASSWD_FILE}"
  chmod 600 "${PASSWD_FILE}"

  echo "[host-s3fs] 挂载 ${BUCKET} @ ${MINIO_URL} → ${CLAUDE_MNT}"
  # shellcheck disable=SC2086
  s3fs "${BUCKET}" "${CLAUDE_MNT}" \
    -o "passwd_file=${PASSWD_FILE}" \
    -o "url=${MINIO_URL}" \
    -o "${S3FS_OPTS}"

  # 简单探测
  if ! ls "${CLAUDE_MNT}" >/dev/null 2>&1; then
    echo "[host-s3fs] 错误：挂载后无法列出目录，请检查 MinIO 与 s3fs 日志（dmesg / journalctl）" >&2
    cmd_unmount || true
    exit 1
  fi
  echo "[host-s3fs] ✓ 已挂载（内容在 MinIO 桶 ${BUCKET}，非普通本地磁盘）"
  cmd_status || true
}

case "${1:-}" in
  mount) cmd_mount ;;
  unmount|umount) cmd_unmount ;;
  status) cmd_status ;;
  *)
    echo "用法: $0 mount|unmount|status" >&2
    exit 2
    ;;
esac

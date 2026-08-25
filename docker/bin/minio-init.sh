#!/bin/sh
# 创建 Claude Code 数据桶；若宿主 ./data/claudecode 仍有本地文件，先迁入桶再清空挂载点。
# 使用 minio/mc 镜像内可用命令（无 grep/find）。
set -eu

endpoint="${MINIO_ENDPOINT:-http://minio:9000}"
user="${MINIO_ROOT_USER:?MINIO_ROOT_USER 未设置}"
pass="${MINIO_ROOT_PASSWORD:?MINIO_ROOT_PASSWORD 未设置}"
bucket="${MINIO_CLAUDE_BUCKET:-${MINIO_HERMES_BUCKET:-claudecode}}"
uploads="${MINIO_UPLOAD_BUCKET:-qianxun}"
seed="${HERMES_SEED_DIR:-/seed}"
# 0 = 不迁移、不清空 seed（Claude Code 直接用宿主目录时必须如此，否则数据会被删）
migrate="${HERMES_SEED_MIGRATE:-1}"

is_s3fs_mount() {
  path="$1"
  while IFS= read -r line; do
    case "${line}" in
      s3fs*" ${path} "*|*" fuse.s3fs "*" ${path} "*|*" ${path} fuse.s3fs"*) return 0 ;;
    esac
  done < /proc/mounts
  return 1
}

seed_has_files() {
  [ -d "${seed}" ] || return 1
  for x in "${seed}"/* "${seed}"/.[!.]* "${seed}"/..?*; do
    [ -e "${x}" ] || continue
    return 0
  done
  return 1
}

clear_seed() {
  for x in "${seed}"/* "${seed}"/.[!.]* "${seed}"/..?*; do
    [ -e "${x}" ] || continue
    rm -rf "${x}"
  done
}

i=0
until mc alias set local "${endpoint}" "${user}" "${pass}"; do
  i=$((i + 1))
  if [ "${i}" -ge 30 ]; then
    echo "[minio-init] 无法连接 MinIO：${endpoint}" >&2
    exit 1
  fi
  sleep 2
done

mc mb -p "local/${bucket}"
mc mb -p "local/${uploads}"

if [ "${migrate}" != "1" ]; then
  echo "[minio-init] HERMES_SEED_MIGRATE=${migrate}，跳过 ${seed} 迁移（Claude Code 使用宿主目录）"
elif seed_has_files; then
  if is_s3fs_mount "${seed}"; then
    echo "[minio-init] ${seed} 已是 s3fs 挂载，跳过本地迁移"
  else
    echo "[minio-init] 将已有 Claude Code 本地数据迁入 bucket ${bucket}"
    mc mirror --overwrite --preserve "${seed}" "local/${bucket}"
    echo "[minio-init] 清空本地挂载点，供 s3fs 使用"
    clear_seed
  fi
fi

echo "[minio-init] bucket ${bucket} 对象数："
mc ls "local/${bucket}" | wc -l
echo "[minio-init] bucket ${bucket} / ${uploads} 已就绪"

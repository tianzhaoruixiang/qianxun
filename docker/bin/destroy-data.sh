#!/usr/bin/env bash
# 千寻 · 危险：关停并删除 TiDB 持久化数据（会话/用户/文件元数据等将丢失）。
# 正常重启请用 ./bin/down.sh && ./bin/up.sh，不要跑本脚本。
set -euo pipefail
HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "${HERE}/.."

YES=0
ALL=0
for arg in "$@"; do
  case "${arg}" in
    --yes|-y) YES=1 ;;
    --all) ALL=1 ;;
    -h|--help)
      echo "用法: $0 --yes [--all]"
      echo "  --yes   确认删除宿主 ./data/tidb"
      echo "  --all   同时删除宿主 ./data 下的 minio/claudecode/doris"
      exit 0
      ;;
    *)
      echo "[destroy-data] 未知参数: ${arg}（需要 --yes）" >&2
      exit 1
      ;;
  esac
done

if [ "${YES}" != "1" ]; then
  echo "[destroy-data] 将删除 ./data/tidb，库内数据不可恢复。" >&2
  echo "[destroy-data] 确认请加上 --yes。正常重启请用 ./bin/down.sh（不加 -v）。" >&2
  exit 1
fi

echo "[destroy-data] 关停容器（随后删除 ./data/tidb）"
"${HERE}/down.sh"

echo "[destroy-data] 删除 ./data/tidb"
rm -rf "${HERE}/../data/tidb"
# 兼容旧环境：若仍残留命名卷则一并清理
docker volume rm qianxun-tidb-data 2>/dev/null \
  || true

if [ "${ALL}" = "1" ]; then
  echo "[destroy-data] --all：删除 ./data 下 minio / claudecode / doris"
  rm -rf \
    "${HERE}/../data/minio" \
    "${HERE}/../data/claudecode" \
    "${HERE}/../data/hermes" \
    "${HERE}/../data/doris"
fi

echo "[destroy-data] 完成。下次 ./bin/up.sh 会得到空的 TiDB。"

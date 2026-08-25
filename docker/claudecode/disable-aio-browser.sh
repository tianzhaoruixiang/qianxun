#!/bin/sh
# 千寻 Claude Code 侧car 只需 Node 网关 + Python；无需 AIO 内置 Chrome/VNC 浏览器。
# 内网环境下 Chrome 访问 Google check-in 失败会导致 supervisord 反复 WARN。
set -eu

write_stub() {
  prog="$1"
  file="$2"
  cat >"$file" <<EOF
[program:${prog}]
command=/bin/true
autostart=false
autorestart=false
stdout_logfile=/dev/null
stderr_logfile=/dev/null
EOF
}

mkdir -p /opt/gem/supervisord
touch /opt/gem/mcp.disabled
write_stub browser /opt/gem/supervisord/browser.conf
write_stub mcp-server-browser /opt/gem/supervisord/supervisord.mcp.conf
rm -f /opt/gem/supervisord/mcp.conf

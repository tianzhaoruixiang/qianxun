# 千寻 · Docker 一键部署

本目录提供 Docker Compose 编排：在 `docker/` 下 **`docker compose up -d --build`** 即可起 TiDB / MinIO / LiteLLM / Claude Code sidecar / 后端 / 前端。

智能体运行器是独立容器里的 **Claude Agent SDK 网关**（HTTP REST + NDJSON）。Java 后端只当 HTTP 客户端，不再在后端镜像里装 Node / Claude CLI。

Claude Agent SDK **只认 Anthropic Messages 协议**。若上游是标准 OpenAI Compatible（`/v1/chat/completions`），通过 **LiteLLM** 做协议桥（方案 A）：

```
Claude Code sidecar  --Anthropic Messages-->  LiteLLM  --OpenAI Compatible-->  任意厂商 /v1
```

## 目录结构

```
仓库根目录
├── backend/                # 千寻 Java 后端
├── fronted/                # 千寻前端
├── claudecode/             # Claude Agent SDK sidecar（Node + Express）
│   ├── Dockerfile
│   ├── package.json
│   └── src/
├── litellm/                # LiteLLM 协议桥配置与回调
│   ├── config.yaml
│   └── merge_leading_system.py
└── docker/                 # 编排与数据（compose 仍在此目录执行）
    ├── docker-compose.yml
    ├── .env
    ├── backend.Dockerfile
    ├── frontend.Dockerfile
    └── bin/
```

在 `docker/` 下 **`docker compose up -d --build`** 即可起 TiDB / MinIO / LiteLLM / Claude Code sidecar / 后端 / 前端。compose 通过相对路径引用仓库根上的 `../claudecode`、`../litellm`。

## 服务总览

| 服务            | 容器名                | 宿主端口   | 说明 |
|-----------------|-----------------------|------------|------|
| tidb            | qianxun-tidb          | 4000/10080 | TiDB（MySQL 协议） |
| minio           | qianxun-minio         | 9000/9001  | 对象存储；控制台 9001 |
| minio-init      | qianxun-minio-init    | —          | 创建 `claudecode` / `qianxun` 桶；可选把本地种子迁入 |
| litellm         | qianxun-litellm       | 4001       | Anthropic↔OpenAI 协议桥（容器内 4000） |
| claude-code     | qianxun-claude-code   | 8642       | Claude Agent SDK：`/health`、`POST /v1/agent/stream`、profile/技能 REST |
| qianxun-backend | qianxun-backend       | 8080       | Spring Boot（Java 21），HTTP 调用 sidecar |
| qianxun-frontend| qianxun-frontend      | 80         | Vite 静态站 + nginx 反代 `/QianXunService` |

前端管理面路径仍是 `/QianXunService/hermes/*`，避免改 UI。

## 上游配置（OpenAI Compatible）

默认链路（**无需启动脚本**）：

```
Claude Code sidecar  --Anthropic Messages-->  LiteLLM:4000  --OpenAI Compatible-->  厂商 /v1
```

在 `docker/.env` 填写：

```bash
OPENAI_UPSTREAM_API_KEY=sk-xxx
OPENAI_UPSTREAM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
LITELLM_UPSTREAM_MODEL=openai/qwen3.6-plus
ANTHROPIC_BASE_URL=http://litellm:4000
ANTHROPIC_API_KEY=sk-litellm-local
ANTHROPIC_MODEL=sonnet
QIANXUN_CLAUDE_SDK_MODEL=sonnet
QIANXUN_CLAUDE_MODEL=qwen3.6-plus
QIANXUN_CLAUDE_THINKING=disabled
```

注意：
- 厂商密钥写在 `OPENAI_UPSTREAM_API_KEY`，**不要**写进 `ANTHROPIC_API_KEY`。
- `ANTHROPIC_BASE_URL` 必须是 `http://litellm:4000`（容器网），不要写成百炼 `/apps/anthropic`、厂商 `/v1`，也不要写成 `http://127.0.0.1:4001`（容器内 localhost 不是宿主）。
- `LITELLM_UPSTREAM_MODEL` 必须带 `openai/` 前缀。`ANTHROPIC_MODEL` / `QIANXUN_CLAUDE_SDK_MODEL` 用 Claude Code 短名（默认 `sonnet`），由 LiteLLM 转到上游。
- 系统设置可改 **上游 model / Base URL / API Key**（OpenAI Compatible）。未填时回退 `QIANXUN_CLAUDE_MODEL`、`OPENAI_UPSTREAM_BASE_URL`、`OPENAI_UPSTREAM_API_KEY`。不要填网关别名 `openai-default`。
- 进入对话时后端会按当前选中模型查注册表，并请求上游 `/models` 读取其声明的上下文窗口，再交给 sidecar 做自动压缩。不要设置 `DISABLE_AUTO_COMPACT`。

### 内网 DeepSeek（OpenAI Compatible）

Claude Agent SDK **只认 Anthropic Messages**，不能把 `ANTHROPIC_BASE_URL` 指到内网 DeepSeek 的 `/v1`。应保持 LiteLLM 桥：

```bash
OPENAI_UPSTREAM_BASE_URL=http://<内网网关>:<port>/v1
# 网关在宿主机本机：http://host.docker.internal:<port>/v1 （不要用 127.0.0.1）
LITELLM_UPSTREAM_MODEL=openai/DeepseekV4Flash
OPENAI_UPSTREAM_API_KEY=<内网密钥>
ANTHROPIC_BASE_URL=http://litellm:4000
ANTHROPIC_API_KEY=sk-litellm-local
ANTHROPIC_MODEL=sonnet
QIANXUN_CLAUDE_MODEL=DeepseekV4Flash
QIANXUN_CLAUDE_THINKING=disabled
```

改完后 `docker compose up -d litellm claude-code`。

若要 sidecar 直连 Anthropic Messages，把 `ANTHROPIC_BASE_URL` 改成厂商 Messages 地址，并把 `QIANXUN_CLAUDE_SDK_MODEL` / `ANTHROPIC_MODEL` 改成真实模型名。

## 一键启动

```bash
cd docker
cp -n .env.example .env   # 首次：填 OPENAI_UPSTREAM_API_KEY
docker compose up -d --build
```

无需 `./bin/up.sh`。`up.sh` 仅用于：宿主 s3fs 把工作区挂进 MinIO、以及跑 `verify.sh`。

在 `docker/.env` 中填写 `OPENAI_UPSTREAM_API_KEY`。未配密钥时健康检查仍可通过，但聊天会失败。

### Windows（Docker Desktop）

| 能力 | Windows |
|------|---------|
| TiDB / MinIO / LiteLLM / 后端 / 前端 / Claude Code（`HOST_S3FS=0` 本地目录） | ✅ 一般可用 |
| 工作区经 s3fs 进 MinIO（Linux 默认） | ❌ Docker Desktop 无可靠 `/dev/fuse`，设 `HOST_S3FS=0` |
| 用户上传进 MinIO `qianxun` 桶 | ✅（S3 API，与 s3fs 无关） |

镜像标签统一为 **`dev`**（不区分 ARM / x86）。本地 `docker compose up --build` 会按当前宿主机架构构建。

Windows 上在 `.env` 设 `HOST_S3FS=0` 再 `docker compose up -d`；工作区落在 `docker/data/claudecode` 本地盘。

`bin/up.sh` 额外做：宿主 s3fs、等待 healthcheck、跑 `verify.sh`。普通启动用 compose 即可。

## 单独验证

```bash
./bin/verify.sh
```

会依次检查：
- `docker compose ps` 所有容器状态
- LiteLLM `/health/liveliness`（若容器存在）
- Claude Code `/health` 与 `/v1/models`
- 后端 `/QianXunService/sessions`
- 前端首页 200、`/QianXunService` 反向代理可用
- **端到端 SSE 流式聊天**（创建会话 → 提问 → 接收 `event:started` / `event:token`）

## 关停 / 清理 / 日志

**重启不会清空 TiDB。** `tidb` 使用宿主目录 `./data/tidb`；`./bin/down.sh` 与 `./bin/up.sh` 只重建容器，目录里的库表会留下来。

```bash
./bin/down.sh                       # 关停容器，保留 ./data（拒绝 -v）
./bin/up.sh                         # 再启动；TiDB 数据仍在
./bin/destroy-data.sh --yes         # 可选：故意清空 ./data/tidb
./bin/destroy-data.sh --yes --all   # 连同 ./data 下 minio/claudecode/doris 一起删
./bin/logs.sh                       # 跟随所有服务日志
./bin/logs.sh qianxun-backend       # 只看后端
./bin/logs.sh qianxun-claude-code   # 只看 Claude Code sidecar
./bin/logs.sh qianxun-litellm       # 只看 LiteLLM
```

切勿对 `down.sh` 传 `-v`；需要擦库时只用 `destroy-data.sh`。

## 关键说明

### 本地数据目录（统一落在 `docker/data/`）

所有持久化 bind 挂载均位于 `docker/data/`：

| 宿主路径 | 服务 | 容器内路径 |
|----------|------|------------|
| `./data/tidb` | tidb | `/data/tidb` |
| `./data/minio` | minio | `/data` |
| `./data/claudecode` | claude-code / minio-init seed | `/opt/data`、`/seed` |

配置/脚本类只读挂载（`./bin/minio-init.sh`、`../litellm/config.yaml`、`../claudecode/disable-aio-browser.sh`）仍指向源码树，不属于运行时数据。

### TiDB 持久化
- 数据在宿主 `./data/tidb`（容器内 `-path=/data/tidb`）。`compose down` / `up.sh` **不会**删这个目录。
- 镜像 `ENTRYPOINT` 已是 `/tidb-server`，compose 的 `command` 只能写 `-store`/`-path` 等 flag。若把 `/tidb-server` 再写进 command，Go 会丢掉后面的 `-path`，数据落到容器可写层 `/tmp/tidb`，一重建容器就空库。
- 故意清空：`./bin/destroy-data.sh --yes`。

### MinIO 与 Claude Code 数据目录

Claude Code sidecar **只能写 POSIX 目录**（`/opt/data`），不能直接写 S3 API。compose 把宿主 `./data/claudecode` 绑到该路径。后端容器不再挂这份盘。

**默认（Linux）** 用宿主 s3fs 把 MinIO 桶 `claudecode` 挂到同一路径（目录是挂载点，不是第二份副本）：

```
claude-code:/opt/data  →  宿主 ./data/claudecode（s3fs）  →  MinIO bucket `claudecode`  →  ./data/minio
```

| 模式 | 启动方式 | 数据落点 | Windows |
|------|----------|----------|---------|
| 默认：宿主 s3fs → MinIO | `./bin/up.sh` | MinIO 桶 `claudecode` | ❌ 无原生 FUSE/s3fs |
| 本地目录 | `HOST_S3FS=0 ./bin/up.sh` | `./data/claudecode` 磁盘 | ✅ 可用 |

OpenEuler / Linux 需先装 FUSE：

```bash
sudo dnf install -y s3fs-fuse fuse   # 包名以发行版为准
grep -q user_allow_other /etc/fuse.conf || echo user_allow_other | sudo tee -a /etc/fuse.conf
cd docker && ./bin/down.sh && ./bin/up.sh
```

- 控制台：`http://127.0.0.1:9001`（默认账号 `qianxun` / `qianxun-minio-dev`，见 `.env`）
- 用户上传文档始终走 MinIO 桶 `qianxun`（S3 API），与 sidecar 目录是否 s3fs 无关
- `HOST_S3FS=1`（默认）时 `minio-init` 会把旧的本地文件迁入桶再挂载；`down.sh` 会卸掉 FUSE

### LiteLLM（方案 A）
- 配置：仓库根目录 `litellm/config.yaml`；SDK 用 `claude-sonnet-4-5` 等 Anthropic 名，LiteLLM 再改写成 `LITELLM_UPSTREAM_MODEL` 或系统设置中的上游 id。
- Claude sidecar 默认：`ANTHROPIC_BASE_URL=http://litellm:4000`。LiteLLM 内网不挂数据库、不校验 master_key；`ANTHROPIC_API_KEY` 只给 Claude SDK 用。
- 厂商 OpenAI Compatible key 只进 LiteLLM 的 `OPENAI_UPSTREAM_API_KEY`。
- **强制 Chat Completions**：`config.yaml` 已设 `use_chat_completions_url_for_anthropic_messages` 与 `use_chat_completions_api`，避免 LiteLLM 把 `/v1/messages` 转到上游 `/v1/responses`（内网 vLLM 会因 `chat_template_kwargs` 500）。改完后需 `docker compose up -d --force-recreate litellm`。
- **`QIANXUN_CLAUDE_THINKING=disabled`（openai 模式默认）**：关闭 extended thinking，减少 reasoning 参数映射。
- 宿主探活：`http://127.0.0.1:4001/health/liveliness`。

### Claude Code sidecar
- **聊天**：后端 `POST {QIANXUN_CLAUDE_BASE_URL}/v1/agent/stream`，响应 `application/x-ndjson`，映射到现有 SSE（`token` / `tool` / `usage`）。
- **会话续聊**：SDK `resume` session id 存在该会话 cwd 的 `.qianxun/claude-sessions/`（首次会从旧的用户级 workspace 映射回退），不靠长连接。
- **管理面**：profile / `CLAUDE.md`（兼写 `SOUL.md`）/ 技能 / 工具集走 REST。
- **数智干警**：镜像内置 `claudecode/templates/profiles/default/CLAUDE.md`。每个用户在 `/opt/data/{userId}/profiles/default/` 有独立副本（`{{USER_ID}}` 会替换成该用户 id）。平台模板在 `/opt/data/_templates/profiles/default/`；仅当灵魂仍是占位文案时才会用内置稿覆盖，不冲掉管理员或用户已改过的人设。
- **密钥**：视 `CLAUDE_UPSTREAM_MODE` 注入；可选 `CLAUDE_GATEWAY_KEY` 作为内网 Bearer。
- **权限**：当前 `bypassPermissions` + `allowedTools`。
- 路径：默认 profile `/opt/data/{userId}/profiles/default`，命名 profile `/opt/data/{userId}/profiles/{name}`，会话工作区 `/opt/data/{userId}/workspace/qx/{sessionId}`（子智能体 task 会话与父会话共用）。

### 前端 SSE
- nginx 已设 `proxy_buffering off / chunked_transfer_encoding off / X-Accel-Buffering: no`；浏览器对 `/QianXunService/*` 的请求由 nginx 透传至后端 8080，不存在跨域。

### 多架构镜像：构建并保存（千寻前后端）

在 **x86_64 与 ARM64** 上分别产出可离线分发的镜像包（`tar.gz`），默认写入 `docker/image-out/`（已加入 `.gitignore`）。

```bash
export BINFMT_INSTALL=1
./docker/bin/build-save-images.sh
./docker/bin/build-save-images.sh v1.2.3
```

目标机载入：

```bash
gunzip -c qianxun-backend-amd64-TAG.tar.gz | docker load
```

## 常见排错

| 现象 | 处理 |
|------|------|
| `source path does not exist .../data/claudecode` | 先 `mkdir -p data/claudecode data/minio data/tidb`，或用 `./bin/up.sh`；若曾挂 s3fs，删 data 前先 `./bin/down.sh` |
| 后端登录 403 / Invalid CORS request | 用内网 IP 打开前端时 Origin 不是 localhost。已默认放开 CORS；重建后端镜像后重试 |
| 聊天报「未配置 ANTHROPIC_API_KEY」 | 检查 sidecar 的 `ANTHROPIC_API_KEY` / `ANTHROPIC_AUTH_TOKEN`；改完后重建 `claude-code` |
| LiteLLM `No connected db` | 请求里的 key 不等于 master_key 时会去查库。当前 compose 已去掉 LiteLLM 的 master_key。`docker compose up -d --force-recreate litellm` |
| `API Error Connection Refused` / `firewall or proxy` | SDK 没连上 LiteLLM。确认 `ANTHROPIC_BASE_URL=http://litellm:4000`（不是厂商 `/v1`、不是 `127.0.0.1`）；`docker compose ps` 里 `qianxun-litellm` 为 healthy；sidecar 内 `wget -qO- http://litellm:4000/health/liveliness`。火山 AIO 镜像自带代理时 compose 已清空 `HTTP_PROXY`。上游在宿主机用 `host.docker.internal`，不要用 `127.0.0.1`。`./bin/logs.sh qianxun-claude-code` 与 `qianxun-litellm` |
| LiteLLM 401 / 上游报错 | 查 `OPENAI_UPSTREAM_API_KEY`、`OPENAI_UPSTREAM_BASE_URL`、`LITELLM_UPSTREAM_MODEL`（须 `openai/...`）；`./bin/logs.sh qianxun-litellm` |
| `thinking_budget` / `/v1/responses` 400/500（含 vLLM `chat_template_kwargs`） | 确认 LiteLLM `config.yaml` 已强制 completions；`docker compose up -d --force-recreate litellm`。openai 模式保持 `QIANXUN_CLAUDE_THINKING=disabled` 并重建 claude-code |
| 宿主 4000 端口冲突 | TiDB 占用 4000；LiteLLM 宿主默认 **4001**（`LITELLM_PORT`） |
| Claude Code 挂不上 MinIO / 找不到 s3fs | 宿主装 `s3fs-fuse` 并保证 `/etc/fuse.conf` 有 `user_allow_other`；无 FUSE 时设 `HOST_S3FS=0` |
| Windows 上跑不起来 | 见上文「Windows」；`.env` 设 `HOST_S3FS=0`，并匹配 **amd64** 镜像标签 |
| 后端无法连 sidecar | 确认 `QIANXUN_CLAUDE_BASE_URL=http://claude-code:8642`，并看 `./bin/logs.sh qianxun-claude-code` |
| 重复构建慢 | 第一次会拉镜像/装依赖；后续 `up.sh` 会复用 Maven/Node 缓存层 |

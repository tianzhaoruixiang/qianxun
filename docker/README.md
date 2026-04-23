# 千寻 · Docker 一键部署

本目录提供完整的 Docker Compose 编排，**一条命令编译后端 + 构建前端 + 起 Doris/Hermes/后端/前端 + 端到端验证**。

## 目录结构

```
docker/
├── docker-compose.yml      # 全部服务编排
├── .env                    # 环境变量（开发默认值，包含密钥；已 gitignore）
├── .env.example            # 模板（无密钥，可入库）
├── backend.Dockerfile      # 千寻后端：Maven 21 → JRE 21
├── frontend.Dockerfile     # 千寻前端：Node 20 → nginx alpine
├── nginx.conf              # 前端 nginx 配置（SSE 友好的反向代理 /QianXunService → 后端）
├── doris/
│   ├── init.sh             # Doris 初始化引导（mysql 客户端，等 BE alive 后 apply）
│   └── init/
│       └── 01_schema.sql   # DDL：库表结构（chat_session / chat_message / intent_scenario）
├── hermes/
│   └── config.yaml         # Hermes Agent 配置（Kimi 中国站 provider）
└── bin/
    ├── up.sh               # 一键编译+构建镜像+启动+验证
    ├── verify.sh           # 健康检查 + 接口探活 + SSE 端到端
    ├── down.sh             # 关停容器（-v 同时清理数据卷）
    └── logs.sh             # 查看日志（可指定服务）
```

## 服务总览

| 服务            | 容器名                | 宿主端口   | 说明 |
|-----------------|-----------------------|------------|------|
| doris-fe        | qianxun-doris-fe      | 8030/9030  | Doris FE，MySQL 协议 9030 |
| doris-be        | qianxun-doris-be      | 8040       | Doris BE |
| doris-init      | qianxun-doris-init    | —          | 一次性初始化（FE/BE healthy 后 apply 01_schema.sql） |
| hermes-agent    | qianxun-hermes-agent  | 8642       | OpenAI 兼容 API 网关，下游 Kimi |
| qianxun-backend | qianxun-backend       | 8080       | Spring Boot 3.3.2（Java 21） |
| qianxun-frontend| qianxun-frontend      | 5173       | Vite 静态站 + nginx 反代 `/QianXunService` |

## 一键启动

```bash
cd docker
# 已带开发用默认值（含 KIMI 密钥），直接起：
./bin/up.sh
```

### ARM64（Apple Silicon 等）主机

- Doris FE/BE 与 Hermes 镜像为 **linux/amd64**，本仓库 `docker-compose.yml` 已为这三项声明 `platform: linux/amd64`，由 Docker 走模拟执行。
- **必须先安装 binfmt**，否则容器可能崩溃（例如 `qianxun-doris-fe` 退出码 **139**）：

```bash
docker run --privileged --rm tonistiigi/binfmt --install all
```

- 首次 `docker compose up` 若曾失败，Doris 元数据可能不完整；可 `cd docker && ./bin/down.sh -v` 清卷后重新 `./bin/up.sh`（**会删 Doris/Hermes 本地数据**）。
- 孤儿容器提示：使用 `./bin/up.sh`（已带 `--remove-orphans`）或手动 `docker compose up -d --remove-orphans`。

`bin/up.sh` 做的事：

1. 检查 `.env`（不存在则自动从 `.env.example` 复制）
2. `docker compose up -d --build --remove-orphans`：
   - **编译后端 jar**（Maven 21，缓存 `~/.m2`）
   - **构建前端静态资源**（Node 20，缓存 `~/.npm`）
   - 拉起 Doris FE/BE，doris-init 自动建库建表
   - 拉起 Hermes Agent（OpenAI 兼容 8642，下游 Kimi 中国站）
   - 拉起后端 / 前端
3. 等待后端 `/QianXunService/sessions` 200
4. 调用 `bin/verify.sh` 做端到端验证

## 单独验证

```bash
./bin/verify.sh
```

会依次检查：
- `docker compose ps` 所有容器状态
- Doris FE `/api/health`
- Hermes `/health` 与 `/v1/models`
- 后端 `/QianXunService/sessions`、`/QianXunService/intent-scenarios`
- 前端首页 200、`/QianXunService` 反向代理可用
- 默认意图场景齐全（`org_research / person_research / general`）
- **端到端 SSE 流式聊天**（创建会话 → 提问 → 接收 `event:analysis` 与 `event:token`）

## 关停 / 清理 / 日志

```bash
./bin/down.sh           # 关停容器，保留数据卷
./bin/down.sh -v        # 同时清理数据卷（Doris/Hermes 数据将丢失）
./bin/logs.sh                       # 跟随所有服务日志
./bin/logs.sh qianxun-backend       # 只看后端
./bin/logs.sh qianxun-hermes-agent  # 只看 Hermes
```

## 关键说明

### Doris 初始化
- **DDL 来自 SQL**：`doris-init` 一次性容器在 FE/BE healthy 之后用 mysql 客户端执行 `doris/init/*.sql`。
- **种子数据来自后端**：默认意图场景（机构调研/人物调研/通用）由后端 `DorisSchemaInitializer` 在 `intent_scenario` 表为空时幂等写入；这样保证用户后续在线 CRUD 不会被升级覆盖。

### Hermes / Kimi
- 全部通过 docker-compose `environment:` 注入：`KIMI_API_KEY`、`KIMI_BASE_URL`、`API_SERVER_*`，**不再在镜像或代码仓库里出现密钥**。
- 改用国际站：`KIMI_BASE_URL=https://api.moonshot.ai/v1`；改用其他模型：编辑 `hermes/config.yaml` 的 `model.default`。

### 前端 SSE
- nginx 已设 `proxy_buffering off / chunked_transfer_encoding off / X-Accel-Buffering: no`；浏览器对 `/QianXunService/*` 的请求由 nginx 透传至后端 8080，不存在跨域。

### 平台架构
- Hermes 镜像目前仅 `linux/amd64`；arm64 主机会自动通过 QEMU 仿真运行（首次启动较慢，调用偏慢），生产建议放 amd64 主机或等多架构镜像。

## 常见排错

| 现象 | 排查 |
|------|------|
| 子网冲突：`Pool overlaps with other one on this address space` | 修改 `.env` 中 `DORIS_SUBNET` 与 `docker-compose.yml` 中的固定 IP |
| `Unknown provider 'xxx'` | 检查 `hermes/config.yaml` 的 `model.provider` 与镜像 `hermes_cli/providers.py` 的实际 ID |
| 后端 403/无 SSE | 确认 `.env` 中 `API_SERVER_KEY` 与后端 `HERMES_API_KEY` 一致；前端走 `/QianXunService` 默认无需配置 |
| 后端无法连 Doris | 前端 `verify.sh` 先 fail；查看 `./bin/logs.sh qianxun-doris-init` 是否成功执行 SQL |
| 重复构建慢 | 第一次会拉镜像/装依赖；后续 `up.sh` 会复用 BuildKit 的 `~/.m2`、`~/.npm` 缓存层 |

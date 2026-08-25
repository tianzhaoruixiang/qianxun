# 创建智能体失败（写入灵魂文件 / HTML 500）操作说明

按下面步骤做，即可消除「注册智能体」时出现的：

```text
写入 CLAUDE.md 失败: Unexpected character ('<' (code 60)) ...
<!DOCTYPE html> ... Internal Server Error
```

界面上也可能写成「写入 智能体.md 失败」，含义相同。

---

## 1. 原因（对照用，可跳过）

1. 前端保存智能体 → 后端 `POST /registry/agents/upsert`。
2. 后端先创建 profile，再请求 Claude Code 网关：`PUT /api/profiles/{name}/soul`。
3. 网关写完 `CLAUDE.md` / `SOUL.md` 后，会把整个 profile **复制**到 `/opt/data/_templates/profiles/{name}/`。
4. profile 里有一条运行时软链：

   `{profile}/.claude/.claude/skills` → `../skills`（指向目录）

5. 复制时把软链当成文件做 `copyFile`，Node 报 **`EISDIR`**。网关未捕获，返回 HTML 500。Java 按 JSON 解析，于是弹出上面那段报错。

代码修复：复制模板时 **跳过符号链接**（`docker/claudecode/src/store.js` 中 `copyProfileAssets`）。软链只用于运行时扫技能，会在下次 `createProfile` 时重建。

---

## 2. 确认仓库里已有修复

在仓库根目录执行：

```bash
grep -n "isSymbolicLink" docker/claudecode/src/store.js
```

应能看到 `copyProfileAssets` 循环里有：

```javascript
if (e.isSymbolicLink()) {
  continue;
}
```

若没有，把下面这段加到 `copyProfileAssets` 里、`TEMPLATE_SKIP_DIRS` 判断之后：

```javascript
if (e.isSymbolicLink()) {
  continue;
}
```

---

## 3. 让正在运行的环境生效（二选一）

compose **没有**把 `/app` 挂进容器，只改进仓库不够，必须让 **容器内** `/app/src/store.js` 带上同样改动。

工作目录按你的部署位置改，本文以仓库路径为例：

```text
/home/administrator/code/qianyu
```

容器名默认：`qianxun-claude-code`。

### 方案 A：热补丁（最快，不重新 build）

```bash
cd /home/administrator/code/qianyu

docker cp docker/claudecode/src/store.js qianxun-claude-code:/app/src/store.js

docker exec qianxun-claude-code grep -n "e.isSymbolicLink" /app/src/store.js

docker exec qianxun-claude-code supervisorctl restart qianxun-claude
```

`grep` 应能打出类似 `126:    if (e.isSymbolicLink()) {`。

### 方案 B：重新构建镜像（改动能进镜像，重启不丢）

```bash
cd /home/administrator/code/qianyu/docker
docker compose build claude-code
docker compose up -d claude-code
```

等 healthcheck 通过后再试注册。

---

## 4. 可选：清掉上次失败留下的半截模板

上次失败的 profile 名若是 `ceshi_agent`：

```bash
docker exec qianxun-claude-code rm -rf /opt/data/_templates/profiles/ceshi_agent
```

用户侧目录 `/opt/data/1/profiles/ceshi_agent` **可以保留**，再次用同一编码保存即可覆盖灵魂文件。若超市里没有这条智能体，直接再点「注册智能体」用同一编码即可。

---

## 5. 验证是否修好

1. 打开智能体超市，管理员账号点「注册智能体」。
2. 填写编码、名称、灵魂 `SOUL.md`，确定保存。
3. 应提示「注册成功」，列表出现该智能体。

网关日志应不再出现 `EISDIR`：

```bash
docker exec qianxun-claude-code tail -50 /var/log/gem/qianxun-claude.log
```

若仍失败，把该日志末尾堆栈保留下来对照：修好前典型堆栈是

```text
Error: EISDIR: illegal operation on a directory, copyfile
  '.../profiles/<name>/.claude/.claude/skills' -> '.../_templates/...'
    at async copyProfileAssets
    at async publishProfileTemplateFrom
    at async putSoul
```

---

## 6. 注意

- 热补丁（方案 A）在 **重建/换镜像** 后会丢失，需再 `docker cp` 一次，或改走方案 B。
- 不要改 `QIANXUN_CLAUDE_BASE_URL`；本故障不是地址配错（创建 profile 已经成功）。
- 不要改灵魂正文格式；本故障与 markdown 内容无关。

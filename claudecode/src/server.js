import express from "express";
import { DATA_DIR, sanitizeOwnerId } from "./paths.js";
import {
  createProfile,
  createSkill,
  deleteProfile,
  ensureDir,
  getSkillContent,
  getSoul,
  listDir,
  listProfiles,
  listSkills,
  migrateAllLegacyClaudeHomes,
  listToolsets,
  putSkillContent,
  publishProfileTemplate,
  putSoul,
  readFileBytes,
  readToolsets,
  syncToolsets,
  toggleSkill,
  toggleToolset,
  writeFileBytes,
} from "./store.js";
import {
  deleteManagedPath,
  deleteMcpServer,
  deletePlugin,
  listMcpServers,
  listPlugins,
  signalDelegationCancel,
  toggleMcpServer,
  togglePlugin,
  upsertMcpServer,
  upsertPlugin,
} from "./mcp.js";
import { streamTurn, stripProxyEnv, resolveAnthropicBaseUrl, migrateAllProfileClaudeSettings } from "./chat.js";

stripProxyEnv(process.env);
process.env.ANTHROPIC_BASE_URL = resolveAnthropicBaseUrl(process.env.ANTHROPIC_BASE_URL);

const PORT = Number(process.env.PORT || 8642);
const GATEWAY_KEY = (process.env.CLAUDE_GATEWAY_KEY || "").trim();
const ALLOW_INSECURE = String(process.env.CLAUDE_GATEWAY_ALLOW_INSECURE || "").trim().toLowerCase() === "true";
const MODEL = process.env.QIANXUN_CLAUDE_SDK_MODEL || process.env.ANTHROPIC_MODEL || "sonnet";

if (!GATEWAY_KEY && !ALLOW_INSECURE) {
  console.error("[claude-code] FATAL: 未设置 CLAUDE_GATEWAY_KEY。生产环境必须配置 Bearer 鉴权；"
    + "本地调试可设 CLAUDE_GATEWAY_ALLOW_INSECURE=true");
  process.exit(1);
}
if (!GATEWAY_KEY && ALLOW_INSECURE) {
  console.warn("[claude-code] WARNING: 网关未配置 CLAUDE_GATEWAY_KEY，所有 API 公开可访问");
}

const app = express();
app.disable("x-powered-by");
app.use(express.json({ limit: "12mb" }));

app.get("/health", (_req, res) => {
  res.json({ ok: true, runner: "claude-code" });
});

app.use((req, res, next) => {
  if (!GATEWAY_KEY) {
    return next();
  }
  const auth = req.headers.authorization || "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
  if (token === GATEWAY_KEY) {
    return next();
  }
  res.status(401).json({ error: "unauthorized" });
});

function qProfile(req) {
  return req.query.profile || req.body?.profile || "default";
}

function qSession(req) {
  return String(req.query.sessionId || req.body?.sessionId || "").trim();
}

function qUserId(req) {
  return req.query.userId || req.body?.userId || "";
}

function requireUserId(req, res) {
  const uid = sanitizeOwnerId(qUserId(req));
  if (!uid) {
    res.status(400).json({ ok: false, message: "缺少或无效 userId" });
    return null;
  }
  return uid;
}

app.get("/api/status", (_req, res) => {
  res.json({
    ok: true,
    runner: "claude-code",
    configured: Boolean((process.env.ANTHROPIC_API_KEY || process.env.ANTHROPIC_AUTH_TOKEN || "").trim()),
    model: MODEL,
    authRequired: Boolean(GATEWAY_KEY),
  });
});

app.get("/v1/models", (_req, res) => {
  res.json({
    object: "list",
    data: [{ id: MODEL, object: "model" }],
  });
});

app.get("/api/profiles", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  res.json({ profiles: await listProfiles(userId, MODEL) });
});

app.post("/api/profiles", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const r = await createProfile(userId, req.body?.name, req.body?.description);
  res.status(r.ok ? 200 : 400).json(r);
});

app.delete("/api/profiles/:name", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const r = await deleteProfile(userId, req.params.name);
  res.status(r.ok ? 200 : 400).json(r);
});

app.get("/api/profiles/:name/soul", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const r = await getSoul(userId, req.params.name);
  res.status(r.ok ? 200 : 400).json(r);
});

app.put("/api/profiles/:name/soul", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const r = await putSoul(userId, req.params.name, req.body?.content);
  res.status(r.ok ? 200 : 400).json(r);
});

app.post("/api/profiles/:name/publish-template", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const r = await publishProfileTemplate(userId, req.params.name);
  res.status(r.ok ? 200 : 400).json(r);
});

app.get("/api/skills", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  res.json({ skills: await listSkills(userId, qProfile(req)) });
});

app.get("/api/skills/content", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const r = await getSkillContent(userId, qProfile(req), req.query.name);
  res.status(r.ok ? 200 : 404).json(r);
});

app.put("/api/skills/content", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const r = await putSkillContent(userId, qProfile(req), req.body?.name, req.body?.content);
  res.status(r.ok ? 200 : 400).json(r);
});

app.post("/api/skills", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const r = await createSkill(userId, qProfile(req), req.body?.name, req.body?.content, req.body?.category);
  res.status(r.ok ? 200 : 400).json(r);
});

app.put("/api/skills/toggle", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const r = await toggleSkill(userId, qProfile(req), req.body?.name, Boolean(req.body?.enabled));
  res.status(r.ok ? 200 : 400).json(r);
});

app.get("/api/tools/toolsets", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  res.json({ toolsets: await listToolsets(userId, qProfile(req)) });
});

app.put("/api/tools/toolsets/:name", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const r = await toggleToolset(userId, qProfile(req), req.params.name, Boolean(req.body?.enabled));
  res.status(r.ok ? 200 : 400).json(r);
});

app.get("/api/config", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  res.json(await readToolsets(userId, qProfile(req)));
});

app.put("/api/config", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const enabled = req.body?.enabled || req.body?.config?.enabled;
  const disabled = req.body?.disabled || req.body?.config?.disabled;
  const r = await syncToolsets(userId, qProfile(req), enabled, disabled);
  res.status(r.ok ? 200 : 400).json(r);
});

function contentDispositionAttachment(filename) {
  const encoded = encodeURIComponent(filename || "download");
  return `attachment; filename="download"; filename*=UTF-8''${encoded}`;
}

app.get("/api/files", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const recursive = req.query.recursive === "1" || String(req.query.recursive || "").toLowerCase() === "true";
  const r = await listDir(userId, req.query.path, recursive, qSession(req), qProfile(req));
  res.status(r.ok ? 200 : 400).json(r);
});

app.post("/api/files/mkdir", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const r = await ensureDir(userId, req.body?.path, qSession(req), qProfile(req));
  res.status(r.ok ? 200 : 400).json(r);
});

app.post("/api/files/write", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const b64 = req.body?.contentBase64 || "";
  const bytes = b64 ? Buffer.from(b64, "base64") : Buffer.alloc(0);
  const r = await writeFileBytes(userId, req.body?.path, bytes, qSession(req), qProfile(req));
  res.status(r.ok ? 200 : 400).json(r);
});

app.get("/api/files/download", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const r = await readFileBytes(userId, req.query.path, qSession(req), qProfile(req));
  if (!r.ok) {
    res.status(404).json({ message: r.message });
    return;
  }
  res.setHeader("Content-Type", "application/octet-stream");
  res.setHeader("Content-Disposition", contentDispositionAttachment(r.filename));
  res.send(r.bytes);
});

app.get("/api/mcp/servers", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  res.json({ ok: true, servers: await listMcpServers(userId, qProfile(req)) });
});

app.post("/api/mcp/servers", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const r = await upsertMcpServer(userId, qProfile(req), req.body);
  res.status(r.ok ? 200 : 400).json(r);
});

app.put("/api/mcp/servers/:name/toggle", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const r = await toggleMcpServer(userId, qProfile(req), req.params.name, Boolean(req.body?.enabled));
  res.status(r.ok ? 200 : 400).json(r);
});

app.delete("/api/mcp/servers/:name", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const r = await deleteMcpServer(userId, qProfile(req), req.params.name);
  res.status(r.ok ? 200 : 404).json(r);
});

app.get("/api/plugins", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  res.json({ ok: true, plugins: await listPlugins(userId, qProfile(req)) });
});

app.post("/api/plugins", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const r = await upsertPlugin(userId, qProfile(req), req.body);
  res.status(r.ok ? 200 : 400).json(r);
});

app.delete("/api/plugins/:name", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const r = await deletePlugin(userId, qProfile(req), req.params.name);
  res.status(r.ok ? 200 : 404).json(r);
});

app.put("/api/plugins/:name/toggle", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const r = await togglePlugin(userId, qProfile(req), req.params.name, Boolean(req.body?.enabled));
  res.status(r.ok ? 200 : 400).json(r);
});

app.post("/api/delegation/cancel", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const r = await signalDelegationCancel(userId, qProfile(req), req.body?.delegationId);
  res.status(r.ok ? 200 : 400).json(r);
});

app.post("/api/files/delete", async (req, res) => {
  const userId = requireUserId(req, res);
  if (!userId) {
    return;
  }
  const r = await deleteManagedPath(userId, req.body?.path, qSession(req), qProfile(req));
  res.status(r.ok ? 200 : 400).json(r);
});

app.post("/v1/agent/stream", async (req, res) => {
  res.setHeader("Content-Type", "application/x-ndjson; charset=utf-8");
  res.setHeader("Cache-Control", "no-cache");
  res.setHeader("X-Accel-Buffering", "no");
  res.flushHeaders?.();
  req.setTimeout(0);
  res.setTimeout(0);
  req.socket?.setTimeout?.(0);
  const abort = new AbortController();
  const onClientGone = () => {
    if (!res.writableEnded && !abort.signal.aborted) {
      abort.abort();
    }
  };
  res.on("close", onClientGone);
  req.on("aborted", onClientGone);
  const writeLine = (obj) => {
    if (!res.writableEnded) {
      res.write(JSON.stringify(obj) + "\n");
    }
  };
  // 工具执行可能长时间无 SDK 事件；心跳避免后端 HttpClient / 中间层按空闲掐断 NDJSON
  const heartbeatMs = 15_000;
  const heartbeat = setInterval(() => {
    writeLine({ type: "heartbeat", ts: Date.now() });
  }, heartbeatMs);
  if (typeof heartbeat.unref === "function") {
    heartbeat.unref();
  }
  const started = Date.now();
  try {
    if (!(process.env.ANTHROPIC_API_KEY || process.env.ANTHROPIC_AUTH_TOKEN || "").trim()) {
      writeLine({ type: "error", error: "未配置 ANTHROPIC_API_KEY / ANTHROPIC_AUTH_TOKEN" });
      res.end();
      return;
    }
    console.log(`[claude-code] stream start sdkModel=${req.body?.model || process.env.QIANXUN_CLAUDE_SDK_MODEL || process.env.ANTHROPIC_MODEL} upstream=${req.body?.upstreamModel || process.env.QIANXUN_CLAUDE_MODEL || ""} session=${req.body?.sessionId || ""} user=${req.body?.userId || ""} api=${process.env.ANTHROPIC_BASE_URL || ""}`);
    await streamTurn(req.body || {}, writeLine, abort.signal);
    console.log(`[claude-code] stream ok ${Date.now() - started}ms`);
  } catch (ex) {
    const msg = ex?.message || String(ex);
    console.error(`[claude-code] stream error ${Date.now() - started}ms:`, msg);
    if (!res.writableEnded) {
      writeLine({ type: "error", error: msg });
    }
  } finally {
    clearInterval(heartbeat);
    res.off?.("close", onClientGone);
    if (!res.writableEnded) {
      res.end();
    }
  }
});

const server = app.listen(PORT, "0.0.0.0", async () => {
  console.log(`[claude-code] listening on ${PORT} dataDir=${DATA_DIR}`);
  try {
    const n = await migrateAllLegacyClaudeHomes();
    if (n > 0) {
      console.log(`[claude-code] migrated legacy .claude-home for ${n} profile(s)`);
    }
    const settingsN = await migrateAllProfileClaudeSettings();
    if (settingsN > 0) {
      console.log(`[claude-code] rewritten Claude settings ANTHROPIC_BASE_URL in ${settingsN} file(s)`);
    }
  } catch (ex) {
    console.warn("[claude-code] legacy migration:", ex?.message || ex);
  }
});
server.timeout = 0;
server.keepAliveTimeout = 0;
server.headersTimeout = 0;
server.requestTimeout = 0;

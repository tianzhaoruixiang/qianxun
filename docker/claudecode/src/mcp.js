import fs from "node:fs/promises";
import path from "node:path";
import { claudeHome, profileHome, sanitizeOwnerId } from "./paths.js";

function mcpFile(home) {
  return `${claudeHome(home)}/mcp-servers.json`;
}

function pluginsFile(home) {
  return `${claudeHome(home)}/plugins.json`;
}

async function readJson(file, fallback) {
  try {
    const raw = await fs.readFile(file, "utf8");
    return JSON.parse(raw);
  } catch {
    return fallback;
  }
}

async function writeJson(file, obj) {
  await fs.mkdir(path.dirname(file), { recursive: true });
  await fs.writeFile(file, JSON.stringify(obj, null, 2), "utf8");
}

function sanitizeName(raw) {
  const s = String(raw ?? "").trim().toLowerCase().replace(/[^a-z0-9_-]+/g, "-").replace(/^-+|-+$/g, "");
  return s.length > 64 ? s.slice(0, 64) : s;
}

export async function listMcpServers(userId, profile) {
  const uid = sanitizeOwnerId(userId);
  if (!uid) {
    return [];
  }
  const doc = await readJson(mcpFile(profileHome(profile, uid)), { servers: [] });
  return Array.isArray(doc.servers) ? doc.servers : [];
}

export async function upsertMcpServer(userId, profile, body) {
  const uid = sanitizeOwnerId(userId);
  if (!uid) {
    return { ok: false, message: "用户标识无效" };
  }
  const name = sanitizeName(body?.name);
  if (!name) {
    return { ok: false, message: "name 不能为空" };
  }
  const file = mcpFile(profileHome(profile, uid));
  const doc = await readJson(file, { servers: [] });
  const servers = Array.isArray(doc.servers) ? [...doc.servers] : [];
  const idx = servers.findIndex((s) => s && String(s.name).toLowerCase() === name);
  const entry = {
    name,
    command: body?.command || "",
    args: Array.isArray(body?.args) ? body.args : [],
    env: body?.env && typeof body.env === "object" ? body.env : {},
    enabled: body?.enabled !== false,
    description: body?.description || "",
    transport: body?.transport || "stdio",
    url: body?.url || "",
  };
  if (idx >= 0) {
    servers[idx] = { ...servers[idx], ...entry };
  } else {
    servers.push(entry);
  }
  await writeJson(file, { servers });
  return { ok: true, name, server: entry, message: "" };
}

export async function deleteMcpServer(userId, profile, rawName) {
  const uid = sanitizeOwnerId(userId);
  const name = sanitizeName(rawName);
  if (!uid || !name) {
    return { ok: false, message: "参数无效" };
  }
  const file = mcpFile(profileHome(profile, uid));
  const doc = await readJson(file, { servers: [] });
  const before = Array.isArray(doc.servers) ? doc.servers : [];
  const servers = before.filter((s) => s && String(s.name).toLowerCase() !== name);
  if (servers.length === before.length) {
    return { ok: false, message: "MCP Server 不存在", alreadyGone: true };
  }
  await writeJson(file, { servers });
  return { ok: true, name, message: "已删除" };
}

export async function toggleMcpServer(userId, profile, rawName, enabled) {
  const uid = sanitizeOwnerId(userId);
  const name = sanitizeName(rawName);
  if (!uid || !name) {
    return { ok: false, message: "参数无效" };
  }
  const file = mcpFile(profileHome(profile, uid));
  const doc = await readJson(file, { servers: [] });
  const servers = Array.isArray(doc.servers) ? [...doc.servers] : [];
  let found = false;
  for (const s of servers) {
    if (s && String(s.name).toLowerCase() === name) {
      s.enabled = Boolean(enabled);
      found = true;
      break;
    }
  }
  if (!found) {
    return { ok: false, message: "MCP Server 不存在" };
  }
  await writeJson(file, { servers });
  return { ok: true, name, enabled: Boolean(enabled), message: "" };
}

export async function listPlugins(userId, profile) {
  const uid = sanitizeOwnerId(userId);
  if (!uid) {
    return [];
  }
  const doc = await readJson(pluginsFile(profileHome(profile, uid)), { plugins: [] });
  return Array.isArray(doc.plugins) ? doc.plugins : [];
}

export async function upsertPlugin(userId, profile, body) {
  const uid = sanitizeOwnerId(userId);
  if (!uid) {
    return { ok: false, message: "用户标识无效" };
  }
  const name = sanitizeName(body?.name);
  if (!name) {
    return { ok: false, message: "name 不能为空" };
  }
  const file = pluginsFile(profileHome(profile, uid));
  const doc = await readJson(file, { plugins: [] });
  const plugins = Array.isArray(doc.plugins) ? [...doc.plugins] : [];
  const idx = plugins.findIndex((p) => p && String(p.name).toLowerCase() === name);
  const entry = {
    name,
    path: body?.path || "",
    version: body?.version || "",
    enabled: body?.enabled !== false,
    description: body?.description || "",
    manifest: body?.manifest && typeof body.manifest === "object" ? body.manifest : {},
  };
  if (idx >= 0) {
    plugins[idx] = { ...plugins[idx], ...entry };
  } else {
    plugins.push(entry);
  }
  await writeJson(file, { plugins });
  return { ok: true, name, plugin: entry, message: "" };
}

export async function deletePlugin(userId, profile, rawName) {
  const uid = sanitizeOwnerId(userId);
  const name = sanitizeName(rawName);
  if (!uid || !name) {
    return { ok: false, message: "参数无效" };
  }
  const file = pluginsFile(profileHome(profile, uid));
  const doc = await readJson(file, { plugins: [] });
  const before = Array.isArray(doc.plugins) ? doc.plugins : [];
  const plugins = before.filter((p) => p && String(p.name).toLowerCase() !== name);
  if (plugins.length === before.length) {
    return { ok: false, message: "插件不存在", alreadyGone: true };
  }
  await writeJson(file, { plugins });
  return { ok: true, name, message: "已删除" };
}

/**
 * 将千寻 MCP 条目转为 Claude Agent SDK mcpServers 配置。
 * @returns {{ servers: Record<string, object>, allowedTools: string[] }}
 */
export function buildSdkMcpConfig(servers) {
  const out = {};
  const allowedTools = [];
  for (const s of servers || []) {
    if (!s || s.enabled === false) {
      continue;
    }
    const name = String(s.name || "").trim();
    if (!name) {
      continue;
    }
    const transport = String(s.transport || "stdio").trim().toLowerCase();
    if (transport === "http" || transport === "sse" || transport === "streamable-http") {
      const entry = {
        type: transport === "streamable-http" ? "http" : transport,
        url: String(s.url || "").trim(),
      };
      if (!entry.url) {
        continue;
      }
      if (s.env && typeof s.env === "object" && Object.keys(s.env).length) {
        entry.headers = { ...s.env };
      }
      out[name] = entry;
    } else {
      const cmd = String(s.command || "").trim();
      if (!cmd) {
        continue;
      }
      out[name] = {
        command: cmd,
        args: Array.isArray(s.args) ? s.args : [],
        env: s.env && typeof s.env === "object" ? s.env : {},
      };
    }
    allowedTools.push(`mcp__${name}__*`);
  }
  return { servers: out, allowedTools };
}

/** 同步 Claude Code 项目级 .mcp.json（供 settingSources 与 /mcp 命令读取） */
export async function syncMcpJsonFile(home, mcpServers) {
  const file = `${home}/.mcp.json`;
  await writeJson(file, { mcpServers: mcpServers || {} });
}

/** 同步已启用插件清单（供观测与 settingSources 读取） */
export async function syncPluginsManifest(home, plugins) {
  const enabled = (plugins || []).filter((p) => p && p.enabled !== false);
  await writeJson(`${claudeHome(home)}/qianxun-plugins.json`, {
    plugins: enabled.map((p) => ({
      name: p.name,
      path: p.path || "",
      version: p.version || "",
      description: p.description || "",
    })),
  });
}

export async function togglePlugin(userId, profile, rawName, enabled) {
  const uid = sanitizeOwnerId(userId);
  const name = sanitizeName(rawName);
  if (!uid || !name) {
    return { ok: false, message: "参数无效" };
  }
  const file = pluginsFile(profileHome(profile, uid));
  const doc = await readJson(file, { plugins: [] });
  const plugins = Array.isArray(doc.plugins) ? [...doc.plugins] : [];
  let found = false;
  for (const p of plugins) {
    if (p && String(p.name).toLowerCase() === name) {
      p.enabled = Boolean(enabled);
      found = true;
      break;
    }
  }
  if (!found) {
    return { ok: false, message: "插件不存在" };
  }
  await writeJson(file, { plugins });
  return { ok: true, name, enabled: Boolean(enabled), message: "" };
}

/** 写入委派取消信号，供子进程轮询（最佳努力，不保证即时中断） */
export async function signalDelegationCancel(userId, profile, delegationId) {
  const uid = sanitizeOwnerId(userId);
  const id = String(delegationId || "").trim();
  if (!uid || !id || !/^deleg_[a-f0-9]{6,16}$/i.test(id)) {
    return { ok: false, message: "参数无效" };
  }
  const dir = `${profileHome(profile, uid)}/cache/delegation/live/${id}`;
  const file = `${dir}/.abort`;
  try {
    await fs.mkdir(dir, { recursive: true });
    await fs.writeFile(file, new Date().toISOString(), "utf8");
    return { ok: true, delegationId: id, path: file, message: "已发送取消信号" };
  } catch (ex) {
    return { ok: false, message: ex?.message || "写入取消信号失败" };
  }
}

export async function deleteManagedPath(userId, absPath) {
  const uid = sanitizeOwnerId(userId);
  if (!uid) {
    return { ok: false, message: "用户标识无效" };
  }
  const { resolveManaged } = await import("./paths.js");
  const p = resolveManaged(absPath, uid);
  try {
    await fs.rm(p, { recursive: true, force: true });
    return { ok: true, path: p, message: "已删除" };
  } catch (ex) {
    return { ok: false, path: p, message: ex?.message || "删除失败" };
  }
}

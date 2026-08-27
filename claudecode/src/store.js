import fs from "node:fs/promises";
import path from "node:path";
import {
  CLAUDE_RUNTIME_DIRS,
  CLAUDE_RUNTIME_FILES,
  DATA_DIR,
  claudeHome,
  claudeMd,
  disabledSkillsFile,
  filenameOf,
  normalizeProfileName,
  profileHome,
  profilesRoot,
  resolveManaged,
  sanitizeOwnerId,
  sanitizeProfileName,
  sanitizeSkillName,
  skillDir,
  skillsDir,
  soulMd,
  TEMPLATE_PROFILES_ROOT,
  templateProfileHome,
  toolsetsFile,
  userRoot,
  workspace as userWorkspace,
} from "./paths.js";
import { CATALOG, DEFAULT_ENABLED, expandEnabledToolsets, isCatalogName, lowerSet, toInfos } from "./toolsets.js";

function requireUserId(userId) {
  const uid = sanitizeOwnerId(userId);
  if (!uid) {
    throw new Error("用户标识无效");
  }
  return uid;
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

function descriptionFromSkillMd(md) {
  const t = String(md || "").trim();
  if (!t.startsWith("---")) {
    return firstLine(t);
  }
  const end = t.indexOf("\n---", 3);
  if (end < 0) {
    return firstLine(t);
  }
  const fm = t.slice(3, end);
  for (const line of fm.split("\n")) {
    const s = line.trim();
    if (s.toLowerCase().startsWith("description:")) {
      return s.slice("description:".length).trim().replace(/^['"]|['"]$/g, "");
    }
  }
  return firstLine(t.slice(end + 4));
}

function firstLine(t) {
  for (const line of String(t || "").split("\n")) {
    const s = line.trim();
    if (s && !s.startsWith("#") && !s.startsWith("---")) {
      return s.length > 200 ? s.slice(0, 200) : s;
    }
  }
  return "";
}

async function collectSkills(dir, disabled, provenance, byName) {
  let entries;
  try {
    entries = await fs.readdir(dir, { withFileTypes: true });
  } catch {
    return;
  }
  for (const e of entries) {
    if (!e.isDirectory()) {
      continue;
    }
    const md = path.join(dir, e.name, "SKILL.md");
    try {
      const content = await fs.readFile(md, "utf8");
      if (byName.has(e.name)) {
        continue;
      }
      const enabled = ![...disabled].some((n) => n.toLowerCase() === e.name.toLowerCase());
      byName.set(e.name, {
        name: e.name,
        description: descriptionFromSkillMd(content),
        category: "",
        enabled,
        provenance,
      });
    } catch {
      /* skip */
    }
  }
}

const TEMPLATE_SKIP_DIRS = new Set([".claude-home", "cache"]);

async function copyProfileAssets(src, dest, { skipExisting = false, insideClaude = false } = {}) {
  let entries;
  try {
    entries = await fs.readdir(src, { withFileTypes: true });
  } catch {
    return false;
  }
  await fs.mkdir(dest, { recursive: true });
  for (const e of entries) {
    if (TEMPLATE_SKIP_DIRS.has(e.name)) {
      continue;
    }
    // Dirent 对「指向目录的软链」isDirectory() 为 false，copyFile 会 EISDIR。
    // 技能发现软链由 ensureDiscoverableSkillsLayout 在用户 profile 侧重建，不必进模板。
    if (e.isSymbolicLink()) {
      continue;
    }
    if (insideClaude) {
      if (e.isDirectory() && CLAUDE_RUNTIME_DIRS.has(e.name)) {
        continue;
      }
      if (!e.isDirectory() && CLAUDE_RUNTIME_FILES.has(e.name)) {
        continue;
      }
    }
    const from = path.join(src, e.name);
    const to = path.join(dest, e.name);
    if (e.isDirectory()) {
      await copyProfileAssets(from, to, {
        skipExisting,
        insideClaude: insideClaude || e.name === ".claude",
      });
      continue;
    }
    if (skipExisting) {
      try {
        await fs.access(to);
        continue;
      } catch {
        /* copy */
      }
    }
    await fs.mkdir(path.dirname(to), { recursive: true });
    await fs.copyFile(from, to);
  }
  return true;
}

/** 把目录里尚未出现在 disabled 中的工具集补进 enabled 并落盘。 */
async function migrateToolsetsEnableMissing(home) {
  const file = toolsetsFile(home);
  const doc = await readJson(file, null);
  if (!doc) {
    try {
      await fs.access(home);
    } catch {
      return;
    }
    await writeJson(file, { enabled: [...DEFAULT_ENABLED], disabled: [] });
    return;
  }
  let enabled = Array.isArray(doc.enabled) ? doc.enabled : [];
  let disabled = Array.isArray(doc.disabled) ? doc.disabled : [];
  if (!enabled.length && Array.isArray(doc.platform_toolsets?.api_server)) {
    enabled = doc.platform_toolsets.api_server;
  }
  if (!disabled.length && Array.isArray(doc.agent?.disabled_toolsets)) {
    disabled = doc.agent.disabled_toolsets;
  }
  const expanded = expandEnabledToolsets(enabled, disabled);
  const off = CATALOG.map((d) => d.name).filter((n) => lowerSet(disabled).has(n));
  const beforeOn = [...enabled].map((s) => String(s).toLowerCase()).sort().join("\0");
  const afterOn = expanded.map((s) => String(s).toLowerCase()).sort().join("\0");
  const beforeOff = [...disabled].map((s) => String(s).toLowerCase()).sort().join("\0");
  const afterOff = off.map((s) => String(s).toLowerCase()).sort().join("\0");
  if (beforeOn === afterOn && beforeOff === afterOff) {
    return;
  }
  await writeJson(file, { enabled: expanded, disabled: off });
}

/**
 * Claude Code 扫技能的路径是 {@code $HOME/.claude/skills} 与
 * {@code $CLAUDE_CONFIG_DIR/skills}。对话时 HOME 必须是 profile 根目录，
 * 这样 {@code ~/.claude/skills} 才等于托管的 {@code profile/.claude/skills}。
 * 若 HOME 仍被设成 profile/.claude，再补一层嵌套软链兜底。
 */
export async function ensureDiscoverableSkillsLayout(home) {
  const real = skillsDir(home);
  await fs.mkdir(real, { recursive: true });
  const nested = path.join(claudeHome(home), ".claude", "skills");
  await fs.mkdir(path.dirname(nested), { recursive: true });
  try {
    const st = await fs.lstat(nested);
    if (st.isSymbolicLink()) {
      try {
        await fs.access(nested);
        return;
      } catch {
        await fs.unlink(nested);
      }
    } else {
      return;
    }
  } catch {
    /* create */
  }
  try {
    await fs.symlink("../skills", nested);
  } catch (ex) {
    if (!ex || ex.code !== "EEXIST") {
      /* 软链失败不阻断对话 */
    }
  }
}

/** 将旧版 profile/.claude-home 合并进 profile/.claude，并删除遗留目录 */
export async function migrateLegacyClaudeHome(home) {
  await migrateToolsetsEnableMissing(home);
  await ensureDiscoverableSkillsLayout(home);
  const legacy = `${home}/.claude-home`;
  const cfg = claudeHome(home);
  try {
    await fs.access(legacy);
  } catch {
    return;
  }
  await fs.mkdir(cfg, { recursive: true });
  const entries = await fs.readdir(legacy, { withFileTypes: true });
  for (const e of entries) {
    const from = path.join(legacy, e.name);
    const to = path.join(cfg, e.name);
    if (e.name === "skills") {
      try {
        const st = await fs.lstat(from);
        if (st.isSymbolicLink()) {
          await fs.unlink(from);
          continue;
        }
      } catch {
        continue;
      }
    }
    try {
      await fs.access(to);
      continue;
    } catch {
      await fs.rename(from, to);
    }
  }
  try {
    const left = await fs.readdir(legacy);
    if (left.length === 0) {
      await fs.rmdir(legacy);
    }
  } catch {
    /* 非空则保留，避免误删 */
  }
}

const LEGACY_DATA_ROOT_SKIP = new Set(["_templates", "workspace", "profiles", "cache"]);

/** 启动时扫描所有用户 profile，合并遗留 .claude-home */
export async function migrateAllLegacyClaudeHomes() {
  let entries;
  try {
    entries = await fs.readdir(DATA_DIR, { withFileTypes: true });
  } catch {
    return 0;
  }
  let count = 0;
  for (const e of entries) {
    if (!e.isDirectory() || e.name.startsWith(".") || LEGACY_DATA_ROOT_SKIP.has(e.name)) {
      continue;
    }
    const profiles = `${DATA_DIR}/${e.name}/profiles`;
    let profileEntries;
    try {
      profileEntries = await fs.readdir(profiles, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const p of profileEntries) {
      if (p.isDirectory() && !p.name.startsWith(".")) {
        await migrateLegacyClaudeHome(`${profiles}/${p.name}`);
        count += 1;
      }
    }
  }
  return count;
}

async function publishProfileTemplateFrom(userId, profileName) {
  const name = normalizeProfileName(profileName);
  const src = profileHome(name, userId);
  const dest = templateProfileHome(name);
  await copyProfileAssets(src, dest, { skipExisting: false });
  return dest;
}

async function seedSoulFromTemplate(userHome, name, fallback) {
  try {
    const existing = (await fs.readFile(claudeMd(userHome), "utf8")).trim();
    if (existing) {
      return;
    }
  } catch {
    /* missing */
  }
  const tpl = templateProfileHome(name);
  for (const file of [claudeMd(tpl), soulMd(tpl)]) {
    try {
      const text = (await fs.readFile(file, "utf8")).trim();
      if (text) {
        await fs.mkdir(userHome, { recursive: true });
        await fs.writeFile(claudeMd(userHome), text, "utf8");
        await fs.writeFile(soulMd(userHome), text, "utf8");
        return;
      }
    } catch {
      /* next */
    }
  }
  if (fallback) {
    await fs.mkdir(userHome, { recursive: true });
    await fs.writeFile(claudeMd(userHome), fallback, "utf8");
    await fs.writeFile(soulMd(userHome), fallback, "utf8");
  }
}

async function seedProfileFromTemplate(userHome, profileName, fallbackSoul) {
  const tpl = templateProfileHome(profileName);
  try {
    await fs.access(tpl);
    await copyProfileAssets(tpl, userHome, { skipExisting: true });
  } catch {
    /* 尚无平台模板 */
  }
  await seedSoulFromTemplate(userHome, profileName, fallbackSoul);
}

export async function publishProfileTemplate(userId, rawName) {
  requireUserId(userId);
  const name = normalizeProfileName(rawName);
  const src = profileHome(name, userId);
  try {
    await fs.access(src);
  } catch {
    return { ok: false, name, path: "", message: "profile 不存在，无法发布模板" };
  }
  const dest = await publishProfileTemplateFrom(userId, name);
  return { ok: true, name, path: dest, message: "已发布平台模板" };
}

async function listTemplateProfileNames() {
  try {
    const entries = await fs.readdir(TEMPLATE_PROFILES_ROOT, { withFileTypes: true });
    return entries
      .filter((e) => e.isDirectory() && !e.name.startsWith("."))
      .map((e) => normalizeProfileName(e.name));
  } catch {
    return [];
  }
}

export async function listProfiles(userId, model) {
  requireUserId(userId);
  const root = profilesRoot(userId);
  await fs.mkdir(root, { recursive: true });
  await fs.mkdir(userWorkspace(userId), { recursive: true });
  const names = new Set(["default"]);
  for (const n of await listTemplateProfileNames()) {
    names.add(n);
  }
  let entries = [];
  try {
    entries = await fs.readdir(root, { withFileTypes: true });
  } catch {
    /* empty */
  }
  for (const e of entries) {
    if (e.isDirectory() && !e.name.startsWith(".")) {
      names.add(e.name);
    }
  }
  const out = [];
  for (const name of names) {
    const home = profileHome(name, userId);
    const fallback = name === "default" ? "# default\n\n默认智能体\n" : `# ${name}\n`;
    await seedProfileFromTemplate(home, name, fallback);
    out.push({
      name,
      description: name === "default" ? "默认智能体" : "",
      model,
      active: name === "default",
      path: home,
      context_window: 0,
    });
  }
  out.sort((a, b) => {
    if (a.name === "default") {
      return -1;
    }
    if (b.name === "default") {
      return 1;
    }
    return a.name.localeCompare(b.name);
  });
  return out;
}

export async function createProfile(userId, rawName, description) {
  requireUserId(userId);
  const profileName = normalizeProfileName(rawName);
  const home = profileHome(profileName, userId);
  let alreadyExists = false;
  try {
    await fs.access(claudeMd(home));
    alreadyExists = true;
  } catch {
    /* create */
  }
  await fs.mkdir(skillsDir(home), { recursive: true });
  await fs.mkdir(claudeHome(home), { recursive: true });
  await migrateLegacyClaudeHome(home);
  await fs.mkdir(userWorkspace(userId), { recursive: true });
  const desc = (description || "").trim();
  const fallback = desc ? `# ${profileName}\n\n${desc}\n` : `# ${profileName}\n`;
  await seedProfileFromTemplate(home, profileName, fallback);
  try {
    await fs.access(toolsetsFile(home));
  } catch {
    await writeJson(toolsetsFile(home), { enabled: DEFAULT_ENABLED, disabled: [] });
  }
  return {
    ok: true,
    name: profileName,
    path: home,
    message: alreadyExists ? "profile 已存在，已补齐模板内容" : "已创建 Claude Code profile",
    alreadyExists,
  };
}

export async function getSoul(userId, rawName) {
  requireUserId(userId);
  const name = normalizeProfileName(rawName);
  const home = profileHome(name, userId);
  try {
    const content = await fs.readFile(claudeMd(home), "utf8");
    return { ok: true, content, exists: true, message: "" };
  } catch {
    try {
      const content = await fs.readFile(soulMd(home), "utf8");
      return { ok: true, content, exists: true, message: "" };
    } catch {
      return { ok: true, content: "", exists: false, message: "" };
    }
  }
}

export async function putSoul(userId, rawName, content) {
  requireUserId(userId);
  const name = normalizeProfileName(rawName);
  const text = content == null ? "" : String(content);
  if (text.length > 80_000) {
    return { ok: false, content: "", exists: false, message: "SOUL.md 过长（最多 80000 字）" };
  }
  const home = profileHome(name, userId);
  await fs.mkdir(home, { recursive: true });
  await fs.writeFile(claudeMd(home), text, "utf8");
  await fs.writeFile(soulMd(home), text, "utf8");
  await publishProfileTemplateFrom(userId, name);
  return { ok: true, content: text, exists: true, message: "已写入 CLAUDE.md" };
}

export async function deleteProfile(userId, rawName) {
  requireUserId(userId);
  const name = normalizeProfileName(rawName);
  if (name === "default") {
    return { ok: true, name, alreadyGone: false, message: "跳过默认 profile" };
  }
  const home = profileHome(name, userId);
  try {
    await fs.rm(home, { recursive: true, force: true });
    return { ok: true, name, alreadyGone: false, message: "已删除 profile" };
  } catch (ex) {
    return { ok: false, name, alreadyGone: false, message: `删除 profile 失败: ${ex.message}` };
  }
}

export async function listSkills(userId, profile) {
  requireUserId(userId);
  const home = profileHome(profile, userId);
  const disabledDoc = await readJson(disabledSkillsFile(home), { disabled: [] });
  const disabled = new Set(disabledDoc.disabled || []);
  const byName = new Map();
  await collectSkills(skillsDir(home), disabled, "agent", byName);
  await collectSkills(`${home}/skills`, disabled, "legacy", byName);
  return [...byName.values()];
}

async function skillMdPath(userId, profile, name) {
  const home = profileHome(profile, userId);
  const modern = path.join(skillDir(home, name), "SKILL.md");
  try {
    await fs.access(modern);
    return modern;
  } catch {
    /* try legacy */
  }
  const legacy = path.join(`${home}/skills`, name, "SKILL.md");
  try {
    await fs.access(legacy);
    return legacy;
  } catch {
    return modern;
  }
}

export async function getSkillContent(userId, profile, skillName) {
  requireUserId(userId);
  const name = sanitizeSkillName(skillName);
  if (!name) {
    return { ok: false, name: "", content: "", path: "", message: "技能名称无效" };
  }
  const md = await skillMdPath(userId, profile, name);
  try {
    const content = await fs.readFile(md, "utf8");
    return { ok: true, name, content, path: md, message: "" };
  } catch {
    return { ok: false, name, content: "", path: "", message: "技能不存在" };
  }
}

export async function putSkillContent(userId, profile, skillName, content) {
  requireUserId(userId);
  const name = sanitizeSkillName(skillName);
  if (!name) {
    return { ok: false, name: "", path: "", message: "技能名称无效" };
  }
  const text = content == null ? "" : String(content);
  if (text.length > 1_048_576) {
    return { ok: false, name, path: "", message: "SKILL.md 过长（最多 1MiB）" };
  }
  const dir = skillDir(profileHome(profile, userId), name);
  await fs.mkdir(dir, { recursive: true });
  const md = path.join(dir, "SKILL.md");
  await fs.writeFile(md, text, "utf8");
  return { ok: true, name, path: md, message: "" };
}

export async function createSkill(userId, profile, skillName, content, category) {
  const name = sanitizeSkillName(skillName);
  if (!name) {
    return { ok: false, name: "", path: "", message: "技能名称无效" };
  }
  let text = content == null ? "" : String(content);
  if (!text.trim()) {
    return { ok: false, name, path: "", message: "SKILL.md 不能为空" };
  }
  if (!text.startsWith("---") && category && String(category).trim()) {
    text = `---\nname: ${name}\ndescription: ${String(category).trim()}\n---\n\n${text}`;
  }
  return putSkillContent(userId, profile, name, text);
}

export async function toggleSkill(userId, profile, skillName, enabled) {
  requireUserId(userId);
  const name = sanitizeSkillName(skillName);
  if (!name) {
    return { ok: false, name: "", path: "", message: "技能名称无效" };
  }
  const home = profileHome(profile, userId);
  const doc = await readJson(disabledSkillsFile(home), { disabled: [] });
  const disabled = new Set(doc.disabled || []);
  const keys = [...disabled];
  if (enabled) {
    for (const n of keys) {
      if (n.toLowerCase() === name.toLowerCase()) {
        disabled.delete(n);
      }
    }
  } else {
    disabled.add(name);
  }
  await writeJson(disabledSkillsFile(home), { disabled: [...disabled] });
  const md = await skillMdPath(userId, profile, name);
  return { ok: true, name, path: md, message: "" };
}

export async function readToolsets(userId, profile) {
  requireUserId(userId);
  const file = toolsetsFile(profileHome(profile, userId));
  const doc = await readJson(file, null);
  if (!doc) {
    return {
      ok: true,
      enabled: [...DEFAULT_ENABLED],
      disabled: [],
      apiServerConfigured: true,
      message: "",
    };
  }
  let enabled = Array.isArray(doc.enabled) ? doc.enabled : [];
  let disabled = Array.isArray(doc.disabled) ? doc.disabled : [];
  if (!enabled.length && Array.isArray(doc.platform_toolsets?.api_server)) {
    enabled = doc.platform_toolsets.api_server;
  }
  if (!disabled.length && Array.isArray(doc.agent?.disabled_toolsets)) {
    disabled = doc.agent.disabled_toolsets;
  }
  enabled = expandEnabledToolsets(enabled, disabled);
  const configured = Array.isArray(doc.enabled)
    || Array.isArray(doc.platform_toolsets?.api_server);
  return { ok: true, enabled, disabled, apiServerConfigured: configured, message: "" };
}

export async function listToolsets(userId, profile) {
  const gw = await readToolsets(userId, profile);
  const on = gw.enabled && gw.enabled.length ? gw.enabled : DEFAULT_ENABLED;
  return toInfos(on);
}

export async function syncToolsets(userId, profile, enabled, disabled) {
  requireUserId(userId);
  const on = Array.isArray(enabled)
    ? enabled.filter((n) => n && n !== "no_mcp" && isCatalogName(n))
    : [];
  const off = Array.isArray(disabled)
    ? disabled.filter((n) => n && isCatalogName(n))
    : [];
  const current = await readToolsets(userId, profile);
  const same = JSON.stringify([...(current.enabled || [])].map((s) => String(s).toLowerCase()).sort())
    === JSON.stringify(on.map((s) => String(s).toLowerCase()).sort())
    && JSON.stringify([...(current.disabled || [])].map((s) => String(s).toLowerCase()).sort())
    === JSON.stringify(off.map((s) => String(s).toLowerCase()).sort());
  if (same && current.ok) {
    return { ok: true, message: "already-synced" };
  }
  await writeJson(toolsetsFile(profileHome(profile, userId)), { enabled: on, disabled: off });
  return { ok: true, message: "" };
}

export async function toggleToolset(userId, profile, toolsetName, enabled) {
  requireUserId(userId);
  const name = String(toolsetName || "").trim().toLowerCase().replace(/[^a-z0-9_-]+/g, "-");
  if (!name) {
    return { ok: false, name: "", enabled, message: "工具集名称无效" };
  }
  if (!isCatalogName(name)) {
    return { ok: false, name, enabled, message: "未知的 Claude Code 工具集" };
  }
  const current = await readToolsets(userId, profile);
  const on = new Set(expandEnabledToolsets(current.enabled, current.disabled));
  const keys = [...on];
  for (const n of keys) {
    if (String(n).toLowerCase() === name) {
      on.delete(n);
    }
  }
  if (enabled) {
    on.add(name);
  }
  const disabled = CATALOG.map((d) => d.name).filter((n) => ![...on].some((e) => e.toLowerCase() === n));
  const w = await syncToolsets(userId, profile, [...on], disabled);
  if (!w.ok) {
    return { ok: false, name, enabled, message: w.message };
  }
  return { ok: true, name, enabled, message: "" };
}

export async function ensureDir(userId, absPath) {
  requireUserId(userId);
  const p = resolveManaged(absPath, userId);
  await fs.mkdir(p, { recursive: true });
  return { ok: true, path: p, message: "" };
}

const SKIP_WALK_DIRS = new Set(["node_modules", ".git", ".qianxun"]);

export async function listDir(userId, absPath, recursive = false) {
  requireUserId(userId);
  const p = resolveManaged(absPath, userId);
  return walkDir(p, Boolean(recursive), 0, 5);
}

async function walkDir(dir, recursive, depth, maxDepth) {
  let entries;
  try {
    entries = await fs.readdir(dir, { withFileTypes: true });
  } catch {
    return { ok: false, path: dir, entries: [], message: "不是目录" };
  }
  const out = [];
  for (const e of entries) {
    if (e.name.startsWith(".") || e.name.startsWith("~")) {
      continue;
    }
    const full = path.join(dir, e.name);
    let size = null;
    let mtimeMs = null;
    try {
      const st = await fs.stat(full);
      if (!e.isDirectory()) {
        size = st.size;
      }
      mtimeMs = Number.isFinite(st.mtimeMs) ? st.mtimeMs : null;
    } catch {
      /* ignore */
    }
    out.push({
      name: e.name,
      path: full,
      is_directory: e.isDirectory(),
      size,
      mtimeMs,
    });
    if (
      recursive
      && e.isDirectory()
      && depth < maxDepth
      && !SKIP_WALK_DIRS.has(e.name)
    ) {
      const child = await walkDir(full, true, depth + 1, maxDepth);
      if (child.ok && Array.isArray(child.entries)) {
        out.push(...child.entries);
      }
    }
  }
  return { ok: true, path: dir, entries: out, message: "" };
}

export async function writeFileBytes(userId, absPath, bytes) {
  requireUserId(userId);
  const p = resolveManaged(absPath, userId);
  const data = bytes || Buffer.alloc(0);
  if (data.length > 8 * 1024 * 1024) {
    return { ok: false, path: p, message: "单文件超过 8MiB" };
  }
  await fs.mkdir(path.dirname(p), { recursive: true });
  await fs.writeFile(p, data);
  return { ok: true, path: p, message: "" };
}

export async function readFileBytes(userId, absPath) {
  requireUserId(userId);
  const p = resolveManaged(absPath, userId);
  try {
    const bytes = await fs.readFile(p);
    return { ok: true, bytes, filename: filenameOf(p), message: "" };
  } catch {
    return { ok: false, bytes: null, filename: filenameOf(absPath), message: "文件不存在" };
  }
}

export { sanitizeOwnerId, userRoot };

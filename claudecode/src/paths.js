import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

export const DATA_DIR = process.env.DATA_DIR || "/opt/data";

const APP_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");

/** 镜像内置的 profile 模板（数智干警 default 等），发布到 DATA_DIR 前的源 */
export const BUNDLED_PROFILES_ROOT = process.env.QIANXUN_BUNDLED_PROFILES
  || `${APP_ROOT}/templates/profiles`;

/** 平台级 profile 模板（管理员 upsert 写入，用户首次 ensure 时复制） */
export const TEMPLATE_PROFILES_ROOT = `${DATA_DIR}/_templates/profiles`;

/** Claude Agent SDK 运行时目录内不纳入模板发布的子目录/文件 */
export const CLAUDE_RUNTIME_DIRS = new Set([
  "sessions",
  "projects",
  "session-env",
  "shell-snapshots",
  "backups",
]);

export const CLAUDE_RUNTIME_FILES = new Set([".claude.json", ".last-cleanup"]);

export function sanitizeOwnerId(userId) {
  const id = String(userId ?? "").trim();
  if (!id || id.includes("..") || id.includes("/") || id.includes("\\")) {
    return "";
  }
  return id;
}

export function userRoot(userId) {
  const uid = sanitizeOwnerId(userId);
  if (!uid) {
    throw new Error("用户标识无效");
  }
  return `${DATA_DIR}/${uid}`;
}

export function profilesRoot(userId) {
  return `${userRoot(userId)}/profiles`;
}

export function normalizeProfileName(profile) {
  const raw = String(profile ?? "default").trim();
  if (!raw || raw === "default" || raw.toLowerCase() === "hermes-agent" || raw.toLowerCase() === "claude-code") {
    return "default";
  }
  const name = raw.toLowerCase().replace(/[^a-z0-9_-]/g, "-").replace(/-+/g, "-").replace(/^-|-$/g, "");
  if (!name) {
    return "default";
  }
  return name.charAt(0) >= "0" && name.charAt(0) <= "9" ? `p-${name}` : name;
}

export function sanitizeProfileName(rawName) {
  const name = normalizeProfileName(rawName);
  return name === "default" ? "" : name;
}

export function isDefaultProfile(profile) {
  return normalizeProfileName(profile) === "default";
}

/** 智能体 profile 根目录：/opt/data/{userId}/profiles/{profileName} */
export function profileHome(profile, userId) {
  const name = normalizeProfileName(profile);
  return `${profilesRoot(userId)}/${name}`;
}

/** 用户工作区根（不再作为 cwd）：/opt/data/{userId}/workspace */
export function workspace(userId) {
  return `${userRoot(userId)}/workspace`;
}

export const SESSION_DIR = "qx";

export function sanitizeSessionId(sessionId) {
  return sanitizeOwnerId(sessionId);
}

/** 会话独立 cwd：/opt/data/{userId}/workspace/qx/{workspaceSessionId} */
export function sessionCwd(userId, workspaceSessionId) {
  const sid = sanitizeSessionId(workspaceSessionId) || "default";
  return `${workspace(userId)}/${SESSION_DIR}/${sid}`;
}

export function templateProfileHome(profile) {
  const name = normalizeProfileName(profile);
  return `${TEMPLATE_PROFILES_ROOT}/${name}`;
}

export function bundledProfileHome(profile) {
  const name = normalizeProfileName(profile);
  return `${BUNDLED_PROFILES_ROOT}/${name}`;
}

export function claudeMd(home) {
  return `${home}/CLAUDE.md`;
}

export function soulMd(home) {
  return `${home}/SOUL.md`;
}

/**
 * Claude Agent SDK 配置根（CLAUDE_CONFIG_DIR）。
 * 对话时 HOME 必须是 profile 根目录，SDK 才会在 {@code ~/.claude/skills} 发现技能。
 */
export function claudeHome(home) {
  return `${home}/.claude`;
}

export function skillsDir(home) {
  return `${claudeHome(home)}/skills`;
}

export function legacySkillsDir(home) {
  return `${home}/skills`;
}

export function sanitizeSkillName(raw) {
  let s = String(raw ?? "").trim();
  if (!s) {
    return "";
  }
  s = s.replace(/\\/g, "/");
  const slash = s.lastIndexOf("/");
  if (slash >= 0) {
    s = s.slice(slash + 1);
  }
  s = s.replace(/[^A-Za-z0-9._-]+/g, "-").replace(/^[.-]+|[.-]+$/g, "");
  if (!s || s === "." || s === "..") {
    return "";
  }
  return s.length > 64 ? s.slice(0, 64) : s;
}

export function skillDir(home, skillName) {
  return `${skillsDir(home)}/${sanitizeSkillName(skillName)}`;
}

export function toolsetsFile(home) {
  return `${claudeHome(home)}/qianxun-toolsets.json`;
}

export function disabledSkillsFile(home) {
  return `${claudeHome(home)}/qianxun-skills-disabled.json`;
}

export function sessionFile(cwd, sessionId) {
  let id = String(sessionId ?? "").trim() || "default";
  const safe = id.replace(/[^A-Za-z0-9._-]+/g, "_");
  const key = safe.length > 80 ? safe.slice(0, 80) : safe;
  return `${cwd}/.qianxun/claude-sessions/${key}`;
}

export function filenameOf(p) {
  const s = String(p ?? "").trim().replace(/\\/g, "/");
  const slash = s.lastIndexOf("/");
  return slash >= 0 ? s.slice(slash + 1) : s;
}

export function resolveManaged(absPath, userId, workspaceSessionId) {
  const raw = String(absPath ?? "").trim();
  if (!raw) {
    throw new Error("路径不能为空");
  }
  const uid = sanitizeOwnerId(userId);
  if (!uid) {
    throw new Error("用户标识无效");
  }
  const root = userRoot(uid);
  const sid = sanitizeSessionId(workspaceSessionId);
  const workspaceDir = sid ? sessionCwd(uid, sid) : workspace(uid);
  let resolved;
  if (path.isAbsolute(raw)) {
    resolved = path.resolve(raw);
  } else {
    const rel = raw.replace(/\\/g, "/").replace(/^\.\//, "");
    resolved = rel === "workspace" || rel.startsWith("workspace/")
      ? path.resolve(root, rel)
      : path.resolve(workspaceDir, rel);
  }
  const rel = path.relative(root, resolved);
  if (rel.startsWith("..") || path.isAbsolute(rel)) {
    throw new Error("路径超出用户数据目录");
  }
  return resolved;
}

export function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true });
}

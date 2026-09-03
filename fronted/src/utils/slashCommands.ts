import type { AgentRegistryItem } from '@/api/registry'
import type { HermesSkillItem } from '@/api/hermes'
import {
  displayAgentName,
  isDefaultHermesProfile,
  looksLikeTechnicalId,
} from '@/utils/agentDisplay'
import { DEFAULT_BRAND_NAME, brandCopy } from '@/utils/brandCopy'
import { getSystemName, isDigitalOfficerDisplayName } from '@/utils/systemName'
import { sanitizeUserFacingText } from '@/utils/userFacingCopy'
import {
  DEFAULT_PLAN_CREATE_TASK,
  DEFAULT_PLAN_EXECUTE_TASK,
  isPlanExecuteToken,
  PLAN_EXECUTE_SKILL,
  PLAN_SKILL,
} from '@/utils/planCommands'

export type SlashMode = 'root' | 'agents' | 'skill' | 'goal' | 'plan'

export interface SlashContext {
  mode: SlashMode
  /** 「/」或「@」在原文中的下标 */
  start: number
  query: string
  /** @ 专门用于指定本轮智能体；/ 保留原有命令兼容 */
  trigger: '/' | '@'
}

export type SlashItem =
  | {
      kind: 'command'
      id:
        | 'agents'
        | 'agents-status'
        | 'mcp'
        | 'plugin'
        | 'compact'
        | 'tasks'
        | 'skill'
        | 'goal'
        | 'goal-set'
        | 'goal-clear'
        | 'plan'
        | 'plan-create'
        | 'plan-execute'
      label: string
      caption: string
    }
  | {
      kind: 'agent'
      id: string
      label: string
      caption: string
      agent: AgentRegistryItem | null
      current?: boolean
    }
  | {
      kind: 'skill'
      id: string
      label: string
      caption: string
      skill: HermesSkillItem
      disabled: boolean
    }

export function detectSlashContext(text: string, cursor: number): SlashContext | null {
  const pos = Math.max(0, Math.min(cursor, text.length))
  const before = text.slice(0, pos)
  const trigger = lastCommandTrigger(before)
  if (!trigger) return null
  const after = before.slice(trigger.start + 1)
  if (after.includes('\n')) return null
  if (trigger.char === '@') {
    // @ 后只筛选智能体；输入空格后结束候选，后续文本作为交给该智能体的任务。
    if (/\s/.test(after)) return null
    return { mode: 'agents', start: trigger.start, query: after, trigger: '@' }
  }
  // Hermes：/agents，别名 /tasks、/task（查子智能体与运行中任务）
  const agentsHead = /^(agents|tasks|task)(\s+|$)/i.exec(after)
  if (agentsHead) {
    return { mode: 'agents', start: trigger.start, query: after.slice(agentsHead[0].length), trigger: '/' }
  }
  const skillHead = /^(skill)(\s+|$)/i.exec(after)
  if (skillHead) {
    return { mode: 'skill', start: trigger.start, query: after.slice(skillHead[0].length), trigger: '/' }
  }
  const planHead = /^(plan)(\s+|$)/i.exec(after)
  if (planHead) {
    return { mode: 'plan', start: trigger.start, query: after.slice(planHead[0].length), trigger: '/' }
  }
  const goalHead = /^(goal)(\s+|$)/i.exec(after)
  if (goalHead) {
    return { mode: 'goal', start: trigger.start, query: after.slice(goalHead[0].length), trigger: '/' }
  }
  if (/\s/.test(after)) return null
  return { mode: 'root', start: trigger.start, query: after, trigger: '/' }
}

function lastCommandTrigger(before: string): { start: number; char: '/' | '@' } | null {
  for (let i = before.length - 1; i >= 0; i--) {
    const char = before[i]
    if (char === '/' || char === '@') {
      // 智能体指定是整条消息的前导指令，避免正文中的邮箱或普通 @ 被误当成切换。
      if (char === '@' && before.slice(0, i).trim()) return null
      if (i === 0 || /\s/.test(before[i - 1])) return { start: i, char }
      return null
    }
    if (before[i] === '\n') return null
  }
  return null
}

export function matchesQuery(haystack: string, query: string): boolean {
  const q = query.trim().toLowerCase()
  if (!q) return true
  return haystack.toLowerCase().includes(q)
}

export function buildRootItems(
  agents: AgentRegistryItem[],
  query: string,
  currentAgentCode?: string | null,
  hasGoal?: boolean,
): SlashItem[] {
  const items: SlashItem[] = []
  if (matchesQuery('agents 智能体 切换', query)) {
    items.push({
      kind: 'command',
      id: 'agents',
      label: '/agents',
      caption: '切换当前会话智能体',
    })
  }
  if (matchesQuery('mcp config', query)) {
    items.push({
      kind: 'command',
      id: 'mcp',
      label: '/mcp',
      caption: 'MCP Server 管理',
    })
  }
  if (matchesQuery('plugin 插件', query)) {
    items.push({
      kind: 'command',
      id: 'plugin',
      label: '/plugin',
      caption: '插件管理',
    })
  }
  if (matchesQuery('compact 压缩上下文', query)) {
    items.push({
      kind: 'command',
      id: 'compact',
      label: '/compact',
      caption: '压缩会话上下文',
    })
  }
  if (matchesQuery('task tasks 任务 子智能体 子任务 运行', query)) {
    items.push({
      kind: 'command',
      id: 'tasks',
      label: '/task',
      caption: '状态与运行日志；/task log <id> 看详情',
    })
  }
  if (matchesQuery('skill 技能', query)) {
    items.push({
      kind: 'command',
      id: 'skill',
      label: '/skill',
      caption: '选用当前智能体已启用的技能',
    })
  }
  if (matchesQuery('plan 计划 规划 实施', query)) {
    items.push({
      kind: 'command',
      id: 'plan',
      label: '/plan',
      caption: '生成实施计划，或按计划执行',
    })
  }
  if (matchesQuery('goal 目标 长程任务', query)) {
    items.push({
      kind: 'command',
      id: 'goal',
      label: '/goal',
      caption: '设定可验证的完成条件，持续推进直到达成',
    })
  }
  if (hasGoal && matchesQuery('goal clear 清除目标', query)) {
    items.push({
      kind: 'command',
      id: 'goal-clear',
      label: '清除目标',
      caption: '取消当前会话的长程目标',
    })
  }
  // 根菜单仍保留智能体快捷项，便于输入 / 后直接切换
  items.push(...buildAgentItems(agents, query, currentAgentCode, { includeStatus: false }))
  return items
}

/** 根菜单在已输入筛选字时附带已启用技能，支持官方 /{slug} 用法。 */
export function buildRootItemsWithSkills(
  agents: AgentRegistryItem[],
  query: string,
  currentAgentCode: string | null | undefined,
  hasGoal: boolean,
  skills: HermesSkillItem[],
): SlashItem[] {
  const items = buildRootItems(agents, query, currentAgentCode, hasGoal)
  if (!query.trim()) {
    return items
  }
  items.push(...buildSkillItems(skills, query))
  return items
}

export function buildAgentItems(
  agents: AgentRegistryItem[],
  query: string,
  currentAgentCode?: string | null,
  opts?: { includeStatus?: boolean },
): SlashItem[] {
  const items: SlashItem[] = []
  if (opts?.includeStatus !== false && matchesQuery('status 状态 任务 task tasks 子智能体 运行', query)) {
    items.push({
      kind: 'command',
      id: 'agents-status',
      label: '查看运行中的任务',
      caption: '状态概览与运行日志索引',
    })
  }
  const officerName = getSystemName()
  const officerCurrent = !currentAgentCode
  if (
    matchesQuery(officerName, query)
    || matchesQuery(DEFAULT_BRAND_NAME, query)
    || matchesQuery('默认', query)
  ) {
    items.push({
      kind: 'agent',
      id: '__officer__',
      label: officerName,
      caption: officerCurrent ? brandCopy.slashOfficerCurrent : brandCopy.slashOfficerSwitch,
      agent: null,
      current: officerCurrent,
    })
  }
  const enabled = agents.filter((a) => a.enabled)
  for (const agent of enabled) {
    if (isDefaultHermesProfile(agent.hermesProfile)) continue
    const rawName = (agent.name || '').trim()
    if (isDigitalOfficerDisplayName(rawName)) continue
    const label = sanitizeUserFacingText(
      displayAgentName({
        agentCode: agent.code,
        agentName: agent.name,
        hermesProfile: agent.hermesProfile,
        agents,
      }),
    )
    const shown = label && !looksLikeTechnicalId(label) ? label : rawName || agent.code
    if (
      !matchesQuery(shown, query)
      && !matchesQuery(rawName, query)
      && !matchesQuery(agent.code, query)
    ) {
      continue
    }
    const current = currentAgentCode === agent.code
    items.push({
      kind: 'agent',
      id: `agent:${agent.code}`,
      label: shown,
      caption: current
        ? '当前智能体'
        : agent.description
          ? sanitizeUserFacingText(agent.description)
          : '子智能体',
      agent,
      current,
    })
  }
  return items
}

export function buildSkillItems(skills: HermesSkillItem[], query: string): SlashItem[] {
  const sorted = [...skills].sort((a, b) => Number(b.enabled) - Number(a.enabled)
    || a.name.localeCompare(b.name, 'zh-CN'))
  const items: SlashItem[] = []
  for (const skill of sorted) {
    const label = skill.name
    const caption = sanitizeUserFacingText(skill.description || skill.category || '')
    if (!matchesQuery(label, query) && !matchesQuery(caption, query)) continue
    if (!skill.enabled) continue
    items.push({
      kind: 'skill',
      id: `skill:${skill.name}`,
      label,
      caption: caption || '已启用技能',
      skill,
      disabled: false,
    })
  }
  return items
}

export function buildGoalItems(query: string, hasGoal: boolean): SlashItem[] {
  const items: SlashItem[] = []
  if (matchesQuery('设定 目标 长程任务', query)) {
    items.push({
      kind: 'command',
      id: 'goal-set',
      label: '设定目标',
      caption: '完成条件、验收方式与可选轮次上限',
    })
  }
  if (matchesQuery('clear 清除 取消', query)) {
    items.push({
      kind: 'command',
      id: 'goal-clear',
      label: '清除目标',
      caption: hasGoal ? '结束当前长程任务' : '当前没有长程目标',
    })
  }
  return items
}

export function buildPlanItems(query: string): SlashItem[] {
  const items: SlashItem[] = []
  if (matchesQuery('生成 计划 规划 create write', query)) {
    items.push({
      kind: 'command',
      id: 'plan-create',
      label: '生成计划',
      caption: '只写计划到工作区 plans/，不执行改动',
    })
  }
  if (matchesQuery('执行 开始 动手 execute run', query)) {
    items.push({
      kind: 'command',
      id: 'plan-execute',
      label: '执行计划',
      caption: '按最新计划用子智能体逐步实施',
    })
  }
  return items
}

/** 光标处正在编辑的斜杠片段长度（含 `/skill` 后的筛选字） */
export function slashCommandLength(text: string, ctx: SlashContext): number {
  if (ctx.trigger === '@') {
    return Math.min(1 + ctx.query.length, Math.max(0, text.length - ctx.start))
  }
  let head = '/'
  if (ctx.mode === 'agents') {
    const afterSlash = text.slice(ctx.start + 1)
    const m = /^(agents|tasks|task)(\s|$)/i.exec(afterSlash)
    head = m ? `/${m[1].toLowerCase()}` : '/agents'
  } else if (ctx.mode === 'skill') {
    head = '/skill'
  } else if (ctx.mode === 'plan') {
    head = '/plan'
  } else if (ctx.mode === 'goal') {
    head = '/goal'
  }
  let n = head.length
  const rest = text.slice(ctx.start + n)
  if (ctx.mode !== 'root' && /^\s/.test(rest)) n += 1
  n += ctx.query.length
  return Math.min(Math.max(n, 1), Math.max(0, text.length - ctx.start))
}

export function replaceSlashToken(text: string, ctx: SlashContext, insertion: string): string {
  const tokenLen = slashCommandLength(text, ctx)
  const tail = text.slice(ctx.start + tokenLen).replace(/^[ \t]+/, '')
  const pad = insertion && !insertion.endsWith(' ') && tail && !/^\n/.test(tail) ? ' ' : ''
  return text.slice(0, ctx.start) + insertion + pad + tail
}

/**
 * 发送前解析输入：`@智能体 任务内容` 指定智能体运行，旧 `/智能体` 语法继续兼容；
 * 千寻菜单仍用 `/skill` 选技能、`/agents` 查子任务；
 * `/task`（及 `/tasks`、裸 `/agents`）查子任务状态。
 * 真正下发时后端走 Dashboard 原生斜杠（技能 `/{slug}`，状态 `/agents`）。
 * 发送时同时识别 `/skill 名称` 与官方 `/{slug} [任务]`，避免正文被 Hermes 当成未知斜杠。
 */
export function parseSendSlash(opts: {
  text: string
  skills: HermesSkillItem[]
  agents: AgentRegistryItem[]
  pendingSkillName?: string
}): {
  content: string
  skillName?: string
  agent: AgentRegistryItem | null | undefined
  goalAction?: 'open' | 'clear'
  agentsStatus?: boolean
  slashCommand?: string
} {
  let raw = opts.text.trim()
  let skillName = opts.pendingSkillName?.trim() || undefined
  let agent: AgentRegistryItem | null | undefined = undefined
  let goalAction: 'open' | 'clear' | undefined
  let agentsStatus: boolean | undefined

  const goalParsed = takeLeadingGoal(raw)
  if (goalParsed) {
    goalAction = goalParsed.action
    raw = goalParsed.rest
  }

  const planParsed = takeLeadingPlan(raw)
  if (planParsed) {
    skillName = planParsed.skillName
    raw = planParsed.rest
  }

  const agentsParsed = takeLeadingAgents(raw, opts.agents)
  if (agentsParsed) {
    if (agentsParsed.status) {
      agentsStatus = true
      raw = agentsParsed.rest
    } else {
      agent = agentsParsed.agent
      raw = agentsParsed.rest
    }
  }

  const skillParsed = takeLeadingSkill(raw, opts.skills)
  if (skillParsed) {
    skillName = skillParsed.skillName || skillName
    raw = skillParsed.rest
  }
  if (!skillName) {
    const bare = takeLeadingBareSkill(raw, opts.skills)
    if (bare) {
      skillName = bare.skillName
      raw = bare.rest
    }
  }
  if (!skillName) skillName = undefined

  const slashCommand = extractSlashCommand(opts.text)

  if (agent === undefined && !agentsStatus) {
    const agentParsed = takeLeadingAgent(raw, opts.agents)
    if (agentParsed) {
      agent = agentParsed.agent
      raw = agentParsed.rest
    }
  }

  return { content: raw.trim(), skillName, agent, goalAction, agentsStatus, slashCommand }
}

const PASSTHROUGH_ROOTS = new Set([
  'compact', 'clear', 'help', 'memory', 'model', 'review', 'rewind', 'cost', 'doctor',
  'login', 'logout', 'permissions', 'status', 'config', 'mcp', 'vim', 'diff', 'export',
  'init', 'resume', 'stats', 'usage', 'upgrade', 'bash', 'ide',
])

function extractSlashCommand(text: string): string {
  const t = text.trim()
  if (!t.startsWith('/')) return ''
  const head = t.split('\n', 1)[0]?.trim() || ''
  const root = head.slice(1).split(/\s+/, 1)[0]?.toLowerCase() || ''
  if (PASSTHROUGH_ROOTS.has(root)) return head
  if (/^[a-z0-9]+(?:-[a-z0-9]+)+$/i.test(root)) return head
  return ''
}

function takeLeadingPlan(text: string): { skillName: string; rest: string } | null {
  const m = /^\/plan(?:\s+|$)(.*)$/is.exec(text)
  if (!m) return null
  const rest = (m[1] || '').trim()
  if (!rest) {
    return { skillName: PLAN_SKILL, rest: DEFAULT_PLAN_CREATE_TASK }
  }
  const first = rest.split(/\s+/, 1)[0] || ''
  if (isPlanExecuteToken(first)) {
    const after = rest.slice(first.length).trim()
    return {
      skillName: PLAN_EXECUTE_SKILL,
      rest: after || DEFAULT_PLAN_EXECUTE_TASK,
    }
  }
  return { skillName: PLAN_SKILL, rest }
}

function takeLeadingGoal(text: string): { action: 'open' | 'clear'; rest: string } | null {
  const m = /^\/goal(?:\s+|$)(.*)$/is.exec(text)
  if (!m) return null
  const rest = (m[1] || '').trim()
  if (/^(clear|清除|取消)(?:\s+|$)/i.test(rest) || /^(clear|清除)$/i.test(rest)) {
    return { action: 'clear', rest: rest.replace(/^(clear|清除|取消)\s*/i, '').trim() }
  }
  return { action: 'open', rest }
}

function takeLeadingAgents(
  text: string,
  agents: AgentRegistryItem[],
): { status?: boolean; agent?: AgentRegistryItem | null; rest: string } | null {
  const m = /^\/(?:agents|tasks|task)(?:\s+|$)(.*)$/is.exec(text)
  if (!m) return null
  const cmd = text.slice(1).trim().split(/\s+/, 1)[0].toLowerCase()
  const rest = (m[1] || '').trim()
  // /task、/tasks：专查子任务状态（Hermes /agents 别名）；rest 可带 log <deleg_id> [n]
  if (cmd === 'task' || cmd === 'tasks') {
    return { status: true, rest }
  }
  if (!rest || /^(status|状态|任务|task|tasks|list|ls)$/i.test(rest)) {
    return { status: true, rest: '' }
  }
  if (/^(status|状态|task|tasks)\s+/i.test(rest)) {
    return { status: true, rest: rest.replace(/^(status|状态|task|tasks)\s+/i, '').trim() }
  }
  const officerName = getSystemName()
  const enabled = agents.filter((a) => a.enabled && !isDefaultHermesProfile(a.hermesProfile))
  const names = [
    officerName,
    DEFAULT_BRAND_NAME,
    ...enabled.map((a) => (a.name || '').trim()).filter(Boolean),
    ...enabled.map((a) => a.code).filter(Boolean),
  ]
  const hit = matchNamedPrefix(rest, names)
  if (hit) {
    if (isDigitalOfficerDisplayName(hit.name)) {
      return { agent: null, rest: hit.rest }
    }
    const byName = enabled.find((a) => (a.name || '').trim().toLowerCase() === hit.name.toLowerCase())
    if (byName) return { agent: byName, rest: hit.rest }
    const byCode = enabled.find((a) => a.code === hit.name)
    if (byCode) return { agent: byCode, rest: hit.rest }
  }
  // 未匹配到智能体名：整段当作状态查询（与 Hermes 裸 /agents 一致）
  return { status: true, rest: '' }
}

const RESERVED_SLASH = /^(skill|skills|goal|agents|tasks|task|plan)$/i
const SKILL_SLUG = /^[a-z0-9]+(?:-[a-z0-9]+)+$/i

function takeLeadingSkill(
  text: string,
  skills: HermesSkillItem[],
): { skillName: string; rest: string } | null {
  const m = /^\/skill(?:\s+|$)(.*)$/is.exec(text)
  if (!m) return null
  const rest = m[1].trim()
  const enabled = skills.filter((s) => s.enabled)
  const hit = matchNamedPrefix(rest, skillMatchNames(enabled))
  if (hit) {
    return { skillName: resolveSkillName(enabled, hit.name), rest: hit.rest }
  }
  const token = rest.split(/\s+/, 1)[0] || ''
  if (!token) return { skillName: '', rest: '' }
  return { skillName: token, rest: rest.slice(token.length).trim() }
}

/** 官方 Hermes 技能斜杠：`/smart-charts-600 画一张图` */
function takeLeadingBareSkill(
  text: string,
  skills: HermesSkillItem[],
): { skillName: string; rest: string } | null {
  if (!text.startsWith('/')) return null
  const body = text.slice(1).trim()
  if (!body) return null
  const first = body.split(/\s+/, 1)[0] || ''
  if (RESERVED_SLASH.test(first)) return null
  const enabled = skills.filter((s) => s.enabled)
  const hit = matchNamedPrefix(body, skillMatchNames(enabled))
  if (hit) {
    return { skillName: resolveSkillName(enabled, hit.name), rest: hit.rest }
  }
  if (SKILL_SLUG.test(first)) {
    return { skillName: first, rest: body.slice(first.length).trim() }
  }
  return null
}

function skillMatchNames(skills: HermesSkillItem[]): string[] {
  const names: string[] = []
  for (const s of skills) {
    const name = (s.name || '').trim()
    if (!name) continue
    names.push(name)
    const slug = toSkillSlug(name)
    if (slug && slug !== name) names.push(slug)
  }
  return names
}

function resolveSkillName(skills: HermesSkillItem[], want: string): string {
  const needle = (want || '').trim()
  const exact = skills.find((s) => (s.name || '').trim().toLowerCase() === needle.toLowerCase())
  if (exact) return exact.name
  const slug = toSkillSlug(needle)
  const bySlug = skills.find((s) => toSkillSlug(s.name) === slug)
  return bySlug?.name || needle
}

function toSkillSlug(name: string): string {
  return (name || '')
    .trim()
    .toLowerCase()
    .replace(/[ _]+/g, '-')
    .replace(/[^a-z0-9-]+/g, '')
    .replace(/-+/g, '-')
    .replace(/^-+|-+$/g, '')
}

function takeLeadingAgent(
  text: string,
  agents: AgentRegistryItem[],
): { agent: AgentRegistryItem | null; rest: string } | null {
  const trigger = text[0]
  if (trigger !== '@' && trigger !== '/') return null
  const body = text.slice(1).trim()
  if (!body) return null
  const firstToken = body.split(/\s+/, 1)[0] || ''
  if (trigger === '/' && /^(skill|goal|agents|tasks|task|plan)$/i.test(firstToken)) return null

  const officerName = getSystemName()
  const enabled = agents.filter((a) => a.enabled && !isDefaultHermesProfile(a.hermesProfile))
  const candidates: Array<{ name: string; agent: AgentRegistryItem | null }> = [
    { name: officerName, agent: null },
    { name: DEFAULT_BRAND_NAME, agent: null },
  ]
  for (const a of enabled) {
    const display = displayAgentName({
      agentCode: a.code,
      agentName: a.name,
      hermesProfile: a.hermesProfile,
      agents,
    })
    const names = [
      (a.name || '').trim(),
      a.code,
      display,
      sanitizeUserFacingText(display),
    ].filter(Boolean)
    for (const name of new Set(names)) candidates.push({ name, agent: a })
  }
  const hit = matchNamedPrefix(body, candidates.map((c) => c.name))
  if (!hit) return null
  const resolved = candidates.find((c) => c.name.toLowerCase() === hit.name.toLowerCase())
  if (resolved) return { agent: resolved.agent, rest: hit.rest }
  return null
}

function matchNamedPrefix(text: string, names: string[]): { name: string; rest: string } | null {
  const sorted = [...names].sort((a, b) => b.length - a.length)
  for (const name of sorted) {
    if (!name) continue
    if (text === name) return { name, rest: '' }
    if (text.startsWith(name) && /^\s/.test(text.slice(name.length))) {
      return { name, rest: text.slice(name.length).trim() }
    }
    if (text.toLowerCase() === name.toLowerCase()) return { name, rest: '' }
    if (
      text.toLowerCase().startsWith(name.toLowerCase())
      && /^\s/.test(text.slice(name.length))
    ) {
      return { name, rest: text.slice(name.length).trim() }
    }
  }
  return null
}

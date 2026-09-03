import type { ToolCallInfo } from '@/types/chat'
import { claudeToolIconKind } from '@/utils/claudeToolLabels'

const KINDS = new Set([
  'search',
  'extract',
  'code',
  'terminal',
  'browser',
  'file',
  'vision',
  'image',
  'video',
  'memory',
  'todo',
  'speak',
  'agent',
  'skill',
  'clock',
  'ask',
  'computer',
  'home',
  'social',
  'gear',
])

/** iconKind → AppGlyph name（与市场/侧栏同一套彩色扁平图标） */
const KIND_TO_GLYPH: Record<string, string> = {
  search: 'search',
  extract: 'document',
  code: 'analysis',
  terminal: 'terminal',
  browser: 'browser',
  file: 'file',
  vision: 'preview',
  image: 'image',
  video: 'preview',
  memory: 'cluster',
  todo: 'grid',
  speak: 'chat',
  agent: 'agent',
  skill: 'skill',
  clock: 'goal',
  ask: 'chat',
  computer: 'desktop',
  home: 'market',
  social: 'chat',
  gear: 'tool',
}

export function resolveToolIconKind(tool: Pick<ToolCallInfo, 'iconKind' | 'toolName'>): string {
  const sent = tool.iconKind?.trim()
  if (sent && KINDS.has(sent)) return sent
  const claude = claudeToolIconKind(tool.toolName)
  if (claude && KINDS.has(claude)) return claude
  const code = (tool.toolName || '').toLowerCase()
  if (!code) return 'gear'
  if (code === 'bash' || code === 'powershell' || code === 'monitor' || code === 'bashoutput') return 'terminal'
  if (code === 'websearch' || code === 'glob' || code === 'grep' || code === 'toolsearch') return 'search'
  if (code === 'webfetch' || code === 'read' || code === 'readmcpresourcetool') return 'extract'
  if (code === 'write' || code === 'enterworktree' || code === 'exitworktree' || code === 'artifact') return 'file'
  if (code === 'edit' || code === 'notebookedit' || code === 'multiedit' || code === 'lsp') return 'code'
  if (code === 'agent' || code === 'task' || code === 'sendmessage' || code === 'workflow') return 'agent'
  if (code.startsWith('task') || code === 'todowrite') return 'todo'
  if (code === 'skill') return 'skill'
  if (code === 'askuserquestion' || code.includes('planmode')) return 'ask'
  if (code.includes('cron') || code === 'schedulewakeup' || code === 'remotetrigger') return 'clock'
  if (code.includes('search') || code.includes('find')) return 'search'
  if (code.startsWith('browser_') || code.includes('navigate')) return 'browser'
  if (code.includes('terminal') || code.includes('process') || code.includes('console')) return 'terminal'
  if (code.includes('code') || code.includes('patch')) return 'code'
  if (code.includes('read') || code.includes('extract') || code.includes('snapshot')) return 'extract'
  if (code.includes('write') || code.includes('file') || code.includes('attach')) return 'file'
  if (code.includes('vision') || code.includes('image')) return code.includes('generat') ? 'image' : 'vision'
  if (code.includes('video')) return 'video'
  if (code.includes('speech') || code.includes('tts') || code.includes('spotify')) return 'speak'
  if (code.includes('memory')) return 'memory'
  if (code.includes('todo') || code.includes('kanban')) return 'todo'
  if (code.includes('skill')) return 'skill'
  if (code.includes('cron') || code.includes('heartbeat')) return 'clock'
  if (code.includes('clarif') || code.includes('dialog')) return 'ask'
  if (code.includes('delegate') || code.includes('subagent') || code.includes('agent')) return 'agent'
  if (code.includes('computer') || code.includes('window')) return 'computer'
  if (code.startsWith('ha_') || code.includes('home')) return 'home'
  if (code.includes('discord') || code.includes('feishu') || code.startsWith('yb_')) return 'social'
  return 'gear'
}

export function toolKindToGlyph(kind: string): string {
  return KIND_TO_GLYPH[kind] || 'tool'
}

export function toolStatusToGlyph(status: 'running' | 'completed' | 'error'): string {
  if (status === 'error') return 'fail'
  if (status === 'completed') return 'success'
  return 'reload'
}

import type { ToolCallInfo } from '@/types/chat'
import { useBootstrapStore } from '@/stores/useBootstrapStore'
import { claudeToolLabel } from '@/utils/claudeToolLabels'

/** 键值行 */
export type ToolKvRow = {
  label: string
  value: string
  mono?: boolean
  multiline?: boolean
}

/** 搜索/列表卡片 */
export type ToolCardItem = {
  title: string
  subtitle?: string
  url?: string
  description?: string
}

export type ToolStatusChip = {
  label: string
  tone?: 'ok' | 'warn' | 'err' | 'muted'
}

export type ToolDetailSection =
  | { kind: 'kv'; title?: string; rows: ToolKvRow[] }
  | { kind: 'cards'; title?: string; items: ToolCardItem[] }
  | { kind: 'list'; title?: string; items: string[] }
  | { kind: 'text'; title?: string; text: string; mono?: boolean; error?: boolean }
  | { kind: 'status'; title?: string; items: ToolStatusChip[] }

export type ToolDetailModel = {
  argRows: ToolKvRow[]
  resultSections: ToolDetailSection[]
}

/**
 * Hermes 工具 JSON 字段 → 中文标签（覆盖官方 web/file/terminal/browser 等返回结构）。
 * 展示层禁止直接露出英文 key。
 */
const FIELD_LABELS: Record<string, string> = {
  // 通用
  query: '关键词',
  q: '关键词',
  keyword: '关键词',
  keywords: '关键词',
  search: '关键词',
  search_query: '搜索词',
  input: '内容',
  text: '文本',
  content: '正文',
  raw_content: '原始正文',
  body: '正文',
  markdown: '正文',
  html: '网页源码',
  prompt: '提示词',
  message: '消息',
  messages: '消息',
  url: '链接',
  urls: '链接列表',
  href: '链接',
  link: '链接',
  links: '链接列表',
  path: '路径',
  file: '文件',
  filename: '文件名',
  file_path: '路径',
  filepath: '路径',
  target: '目标',
  dest: '目标路径',
  destination: '目标路径',
  source: '来源',
  expression: '表达式',
  pattern: '匹配规则',
  pattern_str: '匹配规则',
  command: '命令',
  cmd: '命令',
  sql: '查询语句',
  code: '代码',
  script: '脚本',
  language: '语言',
  lang: '语言',
  limit: '数量上限',
  count: '数量',
  num: '数量',
  max_results: '数量上限',
  top_k: '数量上限',
  timeout: '超时（秒）',
  timeout_seconds: '超时（秒）',
  working_directory: '工作目录',
  cwd: '工作目录',
  directory: '目录',
  dir: '目录',
  recursive: '递归',
  overwrite: '覆盖已有',
  force: '强制执行',
  mode: '模式',
  action: '操作',
  method: '方法',
  headers: '请求头',
  encoding: '编码',
  offset: '起始行',
  next_offset: '下一起始行',
  start: '起始',
  end: '结束',
  label: '标签',
  title: '标题',
  description: '摘要',
  snippet: '摘要',
  reason: '原因',
  model: '模型',
  skill: '技能',
  skill_name: '技能',
  name: '名称',
  selector: '选择器',
  element: '元素',
  ref: '元素引用',
  wait: '等待',
  wait_for: '等待条件',
  screenshot: '截图',
  screenshot_path: '截图路径',
  image: '图片',
  images: '图片',
  image_url: '图片链接',
  question: '问题',
  answer: '回答',
  todos: '待办',
  task: '任务',
  tasks: '任务',
  goal: '目标',
  steps: '步骤',
  home: '首页路径',
  format: '格式',
  char_limit: '字数上限',
  full: '完整内容',
  background: '后台运行',
  session_id: '会话编号',
  notify_on_complete: '完成时通知',
  cross_profile: '跨配置写入',
  context: '上下文行数',
  output_mode: '输出模式',
  case_insensitive: '忽略大小写',
  include: '包含',
  exclude: '排除',
  glob: '文件匹配',
  // 状态 / 结果
  success: '是否成功',
  ok: '是否成功',
  status: '状态',
  state: '状态',
  error: '错误',
  error_message: '错误信息',
  errors: '错误列表',
  warning: '警告',
  warnings: '警告',
  stdout: '标准输出',
  stderr: '错误输出',
  output: '输出',
  exit_code: '退出码',
  exitCode: '退出码',
  returncode: '退出码',
  bytes: '字节数',
  size: '大小',
  size_bytes: '字节数',
  lines: '行数',
  line_count: '行数',
  duration: '耗时',
  duration_s: '耗时（秒）',
  position: '排序',
  provider: '服务来源',
  engine: '搜索引擎',
  backend: '后端',
  score: '相关度',
  published: '发布时间',
  author: '作者',
  id: '编号',
  pid: '进程号',
  signal: '信号',
  entity_id: '设备',
  service: '服务',
  domain: '域',
  data: '数据',
  web: '网页结果',
  results: '结果列表',
  items: '条目',
  hits: '命中',
  organic: '搜索结果',
  documents: '文档',
  entries: '条目',
  matches: '匹配项',
  summary: '摘要',
  response: '响应',
  payload: '载荷',
  result: '结果',
  verified: '已校验',
  bytes_written: '写入字节',
  truncated: '已截断',
  stdout_truncated: '输出已截断',
  stdout_bytes_captured: '捕获字节',
  stdout_bytes_total: '总字节',
  stdout_bytes_omitted: '省略字节',
  blocked_by_policy: '策略拦截',
  pending_dialogs: '待处理对话框',
  frame_tree: '页面框架',
  snapshot: '页面快照',
  page_url: '页面链接',
  page_title: '页面标题',
  total: '总计',
  results_count: '结果数量',
  pages_extracted: '提取页数',
  task_index: '任务序号',
  taskIndex: '任务序号',
  task_count: '任务总数',
  taskCount: '任务总数',
  tool: '工具',
  tool_name: '工具',
  childToolName: '调用工具',
  subagent_id: '子智能体编号',
  parent_id: '父会话',
  parentId: '父会话',
  child_session_id: '子会话编号',
  childSessionId: '子会话编号',
  api_calls: '模型调用次数',
  apiCalls: '模型调用次数',
  tool_count: '工具调用次数',
  toolCount: '工具调用次数',
  delegation_id: '派工编号',
  note: '说明',
  preview: '预览',
  tool_preview: '调用预览',
  thinking: '思考',
  old_string: '原文',
  new_string: '替换为',
  diff: '差异',
  patch: '补丁',
  heading: '标题',
  permalink: '永久链接',
  site: '站点',
  date: '日期',
  caption: '说明',
  analysis: '分析',
  options: '选项',
  questions: '问题',
  goals: '目标列表',
  profile: '配置',
  skills: '技能',
  arguments: '参数',
  params: '参数',
  metadata: '附加信息',
  extra: '附加信息',
  details: '详情',
  info: '信息',
  children: '子项',
  child: '子项',
  parent: '上级',
  role: '角色',
  user: '用户',
  assistant: '助手',
  system: '系统',
}

/** 英文单词片段 → 中文（用于未知 snake_case 字段兜底，避免露出英文 key） */
const WORD_ZH: Record<string, string> = {
  web: '网页',
  search: '搜索',
  extract: '提取',
  result: '结果',
  results: '结果',
  data: '数据',
  url: '链接',
  urls: '链接',
  title: '标题',
  content: '正文',
  description: '摘要',
  snippet: '摘要',
  query: '关键词',
  limit: '上限',
  max: '最大',
  min: '最小',
  count: '数量',
  total: '总计',
  success: '成功',
  error: '错误',
  errors: '错误',
  status: '状态',
  state: '状态',
  output: '输出',
  stdout: '输出',
  stderr: '错误输出',
  exit: '退出',
  code: '码',
  path: '路径',
  file: '文件',
  filename: '文件名',
  dir: '目录',
  directory: '目录',
  cwd: '工作目录',
  working: '工作',
  command: '命令',
  cmd: '命令',
  timeout: '超时',
  seconds: '秒',
  bytes: '字节',
  size: '大小',
  written: '写入',
  read: '读取',
  write: '写入',
  verified: '已校验',
  truncated: '已截断',
  captured: '已捕获',
  omitted: '已省略',
  position: '排序',
  provider: '来源',
  engine: '引擎',
  backend: '后端',
  format: '格式',
  char: '字符',
  page: '页面',
  pages: '页面',
  extracted: '已提取',
  blocked: '已拦截',
  policy: '策略',
  by: '',
  raw: '原始',
  next: '下一',
  offset: '偏移',
  line: '行',
  lines: '行',
  image: '图片',
  images: '图片',
  screenshot: '截图',
  snapshot: '快照',
  browser: '浏览器',
  selector: '选择器',
  element: '元素',
  ref: '引用',
  full: '完整',
  session: '会话',
  notify: '通知',
  complete: '完成',
  on: '',
  background: '后台',
  recursive: '递归',
  force: '强制',
  overwrite: '覆盖',
  include: '包含',
  exclude: '排除',
  pattern: '规则',
  target: '目标',
  mode: '模式',
  action: '操作',
  message: '消息',
  prompt: '提示',
  model: '模型',
  skill: '技能',
  task: '任务',
  todo: '待办',
  goal: '目标',
  answer: '回答',
  question: '问题',
  author: '作者',
  score: '相关度',
  published: '发布',
  id: '编号',
  pid: '进程号',
  signal: '信号',
  domain: '域',
  service: '服务',
  entity: '设备',
  home: '首页',
  cross: '跨',
  profile: '配置',
  case: '大小写',
  insensitive: '不敏感',
  glob: '匹配',
  context: '上下文',
  pending: '待处理',
  dialogs: '对话框',
  frame: '框架',
  tree: '树',
  warning: '警告',
  warnings: '警告',
  ok: '成功',
  href: '链接',
  link: '链接',
  links: '链接',
  items: '条目',
  hits: '命中',
  organic: '结果',
  documents: '文档',
  entries: '条目',
  matches: '匹配',
  summary: '摘要',
  response: '响应',
  payload: '数据',
  markdown: '正文',
  html: '源码',
  body: '正文',
  text: '文本',
  input: '输入',
  language: '语言',
  lang: '语言',
  script: '脚本',
  sql: '查询',
  headers: '请求头',
  encoding: '编码',
  start: '起始',
  end: '结束',
  label: '标签',
  name: '名称',
  reason: '原因',
  wait: '等待',
  for: '',
  num: '数量',
  top: '前',
  k: '条',
  duration: '耗时',
  s: '秒',
  ms: '毫秒',
  type: '类型',
  key: '名称',
  value: '内容',
  values: '内容',
  list: '列表',
  info: '信息',
  meta: '附加信息',
  index: '序号',
  number: '编号',
  bool: '是否',
  boolean: '是否',
  true: '是',
  false: '否',
  child: '子',
  parent: '父',
  call: '调用',
  calls: '调用',
  api: '模型',
  preview: '预览',
  thinking: '思考',
  note: '说明',
  old: '原',
  new: '新',
  string: '文本',
  diff: '差异',
  patch: '补丁',
  role: '角色',
  argument: '参数',
  arguments: '参数',
  param: '参数',
  params: '参数',
  option: '选项',
  options: '选项',
  schema: '结构',
  property: '属性',
  properties: '属性',
  required: '必填',
  field: '字段',
  fields: '字段',
  metadata: '附加信息',
  extra: '附加',
  detail: '详情',
  details: '详情',
  log: '日志',
  logs: '日志',
  childsession: '子会话',
  taskindex: '任务序号',
  taskcount: '任务总数',
  navigate: '打开',
  click: '点击',
  press: '按键',
  scroll: '滚动',
  console: '控制台',
  generate: '生成',
  analyze: '分析',
  dialog: '对话框',
  delegate: '委派',
  execute: '执行',
  process: '进程',
  clarify: '澄清',
  manage: '管理',
  cron: '定时',
  job: '任务',
  agent: '智能体',
  subagent: '子智能体',
  kanban: '看板',
  feishu: '飞书',
  spawn: '拉起',
  requested: '已请求',
  request: '请求',
  vision: '视觉',
  terminal: '命令行',
  files: '文件',
  amount: '幅度',
  direction: '方向',
  keys: '按键',
  destination: '目标',
  dest: '目标',
  expression: '表达式',
  get: '获取',
  goals: '目标',
  tasks: '任务',
}

const MONO_KEYS = new Set([
  'command', 'cmd', 'sql', 'code', 'script', 'expression', 'pattern', 'pattern_str',
  'selector', 'path', 'file', 'filename', 'file_path', 'filepath', 'cwd', 'working_directory',
  'directory', 'dir', 'dest', 'destination', 'source', 'url', 'urls', 'href', 'link',
  'stdout', 'stderr', 'output', 'content', 'raw_content', 'body', 'markdown', 'html', 'headers',
  'snapshot', 'screenshot_path', 'ref',
])

const META_SKIP_KEYS = new Set([
  'function', 'call_id', 'tool_call_id', 'toolCallId', 'preview', 'type', 'event',
])

/** 各工具优先展示的入参字段（对齐 Hermes schema） */
const ARG_KEY_ORDER: Record<string, string[]> = {
  web_search: ['query', 'limit'],
  websearch: ['query', 'limit'],
  web_extract: ['urls', 'url', 'char_limit', 'format'],
  webfetch: ['url', 'urls', 'prompt', 'query'],
  x_search: ['query', 'limit'],
  terminal: ['command', 'cmd', 'cwd', 'working_directory', 'timeout', 'background', 'session_id', 'notify_on_complete'],
  bash: ['command', 'cmd', 'cwd', 'working_directory', 'timeout', 'description', 'run_in_background'],
  powershell: ['command', 'cmd', 'timeout'],
  monitor: ['command', 'cmd', 'description'],
  process: ['action', 'command', 'cmd', 'pid', 'signal', 'session_id'],
  execute_code: ['code', 'language', 'lang', 'timeout'],
  read_file: ['path', 'offset', 'limit'],
  read: ['path', 'file_path', 'offset', 'limit', 'pages'],
  write_file: ['path', 'content', 'cross_profile'],
  write: ['path', 'file_path', 'content'],
  patch: ['path', 'file', 'old_string', 'new_string', 'diff', 'patch', 'content'],
  edit: ['path', 'file_path', 'old_string', 'new_string', 'replace_all'],
  notebookedit: ['notebook_path', 'cell_id', 'new_source', 'cell_type', 'edit_mode'],
  search_files: ['pattern', 'target', 'path', 'output_mode', 'context', 'case_insensitive', 'include', 'exclude', 'glob'],
  glob: ['pattern', 'path'],
  grep: ['pattern', 'path', 'glob', 'output_mode', 'context', 'case_insensitive'],
  browser_navigate: ['url'],
  browser_snapshot: ['full'],
  browser_click: ['ref', 'selector', 'element'],
  browser_type: ['ref', 'selector', 'text', 'content'],
  browser_press: ['key', 'keys', 'ref'],
  browser_scroll: ['direction', 'ref', 'amount'],
  browser_console: ['expression', 'code'],
  browser_vision: ['prompt', 'question'],
  browser_get_images: ['selector', 'limit'],
  browser_dialog: ['action', 'text', 'prompt'],
  vision_analyze: ['image', 'images', 'path', 'url', 'prompt', 'question'],
  image_generate: ['prompt', 'model', 'size'],
  todo: ['action', 'todos', 'tasks', 'task', 'content', 'text'],
  todowrite: ['todos', 'tasks', 'merge'],
  taskcreate: ['subject', 'description', 'activeForm'],
  taskupdate: ['taskId', 'status', 'subject', 'description'],
  clarify: ['question', 'questions', 'prompt', 'options'],
  askuserquestion: ['questions', 'question', 'prompt', 'options'],
  memory: ['action', 'query', 'content', 'text', 'key'],
  session_search: ['query', 'limit'],
  skill_view: ['skill', 'skill_name', 'name', 'path'],
  skill_manage: ['action', 'skill', 'skill_name', 'name'],
  skill: ['skill', 'args', 'name'],
  delegate_task: ['goal', 'goals', 'tasks', 'task', 'prompt', 'context', 'profile', 'background'],
  agent: ['description', 'prompt', 'subagent_type', 'model'],
  task: ['description', 'prompt', 'subagent_type'],
  sendmessage: ['to', 'message', 'summary'],
  subagent: ['goal', 'task_index', 'task_count', 'tool'],
}

const LONG_ARG_KEYS = new Set(['content', 'code', 'script', 'body', 'markdown', 'prompt', 'text', 'diff', 'patch', 'html', 'snapshot'])

function normalizeToolName(name?: string): string {
  return (name || '').trim().toLowerCase()
}

function humanizeKey(key: string): string {
  const direct = FIELD_LABELS[key] || FIELD_LABELS[key.toLowerCase()]
  if (direct) return direct

  const parts = key
    .replace(/([a-z])([A-Z])/g, '$1_$2')
    .split(/[_-]+/)
    .filter(Boolean)
  if (!parts.length) return '附加信息'

  const zh = parts
    .map((p) => WORD_ZH[p.toLowerCase()] ?? '')
    .filter((x) => x.length > 0)
  const joined = zh.join('')
  return joined || '附加信息'
}

function compactOneLine(text: string, max = 160): string {
  const line = text.replace(/\s+/g, ' ').trim()
  if (!line) return ''
  return line.length > max ? `${line.slice(0, max)}…` : line
}

function looksLikeCode(text: string): boolean {
  if (!text) return false
  if (text.includes('\n') && /[{};=<>]|^\s*\$|^\s*#|^\s*\/\//m.test(text)) return true
  if (/^(https?:\/\/|\/|\.\/|~\/|[A-Za-z]:\\)/.test(text) && text.length < 260) return true
  return false
}

function formatBool(v: boolean): string {
  return v ? '是' : '否'
}

const STATUS_ZH: Record<string, string> = {
  dispatched: '已派工',
  background: '后台执行',
  running: '进行中',
  started: '已启动',
  completed: '已完成',
  complete: '已完成',
  success: '成功',
  ok: '成功',
  failed: '失败',
  error: '失败',
  cancelled: '已取消',
  interrupted: '已中断',
  timeout: '超时',
  pending: '等待中',
  awaiting: '等待中',
  idle: '空闲',
}

function humanizeStatus(raw: string): string {
  const t = raw.trim()
  if (!t) return ''
  return STATUS_ZH[t.toLowerCase()] || (/^[a-z0-9_ -]+$/i.test(t) ? humanizeKey(t) : t)
}

const TOOL_NAME_KEYS = new Set(['tool', 'tool_name', 'toolName', 'childToolName'])

function zhToolName(code: string): string {
  const n = (code || '').trim()
  if (!n) return ''
  try {
    const mapped = useBootstrapStore().toolLabel(n)
    if (mapped && mapped !== n && mapped !== 'unknown_tool') return mapped
  } catch {
    // Pinia 未就绪时走本地中文启发式
  }
  const claude = claudeToolLabel(n)
  if (claude) return claude
  if (n.startsWith('mcp__')) return `MCP · ${n.slice('mcp__'.length).replace(/__/g, ' · ')}`
  if (n.startsWith('browser_')) return `浏览器 · ${humanizeKey(n.slice('browser_'.length))}`
  if (n.startsWith('kanban_')) return `看板 · ${humanizeKey(n.slice('kanban_'.length))}`
  if (n.startsWith('feishu_')) return `飞书 · ${humanizeKey(n.slice('feishu_'.length))}`
  if (n.startsWith('spotify_')) return `Spotify · ${humanizeKey(n.slice('spotify_'.length))}`
  if (n.startsWith('ha_')) return `智能家居 · ${humanizeKey(n.slice('ha_'.length))}`
  const h = humanizeKey(n)
  return h === '附加信息' ? '工具' : h
}

function formatFieldValue(key: string | undefined, value: unknown): { text: string; mono: boolean; multiline: boolean } {
  if (key === 'task_index' || key === 'taskIndex') {
    if (typeof value === 'number' && Number.isFinite(value)) {
      return { text: `第 ${value + 1} 项`, mono: false, multiline: false }
    }
  }
  if (key === 'task_count' || key === 'taskCount') {
    if (typeof value === 'number' && Number.isFinite(value)) {
      return { text: `共 ${value} 项`, mono: false, multiline: false }
    }
  }
  if (key && TOOL_NAME_KEYS.has(key) && typeof value === 'string') {
    const zh = zhToolName(value)
    if (zh) return { text: zh, mono: false, multiline: false }
  }
  if (key === 'status' || key === 'state' || key === 'mode') {
    const formatted = formatDetailValue(value)
    if (formatted.text) return { ...formatted, text: humanizeStatus(formatted.text), mono: false }
  }
  return formatDetailValue(value)
}

function coerceJson(value: unknown): unknown {
  if (typeof value !== 'string') return value
  let t = value.trim()
  if (!t) return value
  const fenced = t.match(/^```(?:json)?\s*([\s\S]*?)\s*```$/i)
  if (fenced?.[1]) t = fenced[1].trim()
  if ((t.startsWith('{') && t.endsWith('}')) || (t.startsWith('[') && t.endsWith(']'))) {
    const parsed = parseJson(t)
    if (parsed != null) return parsed
  }
  return value
}

function formatDetailValue(
  value: unknown,
  opts?: { max?: number; forceFull?: boolean },
): { text: string; mono: boolean; multiline: boolean } {
  const max = opts?.forceFull ? 0 : (opts?.max ?? 0)
  if (value == null) return { text: '', mono: false, multiline: false }
  if (typeof value === 'boolean') return { text: formatBool(value), mono: false, multiline: false }
  if (typeof value === 'number') return { text: String(value), mono: false, multiline: false }
  if (typeof value === 'string') {
    const t = value.trim()
    const text = max > 0 ? compactOneLine(t, max) : t
    const multiline = text.includes('\n') || text.length > 80
    return { text, mono: multiline || looksLikeCode(t), multiline }
  }
  if (Array.isArray(value) && value.every((v) => v == null || typeof v !== 'object')) {
    if (!value.length) return { text: '（空）', mono: false, multiline: false }
    const text = value.map((v) => (typeof v === 'boolean' ? formatBool(v) : String(v))).join(max > 0 ? '、' : '\n')
    const clipped = max > 0 ? compactOneLine(text, max) : text
    return { text: clipped, mono: false, multiline: max <= 0 && (value.length > 1 || text.length > 80) }
  }
  return { text: '', mono: false, multiline: false }
}

function parseJson(raw?: string): unknown {
  if (!raw?.trim()) return null
  try {
    return JSON.parse(raw)
  } catch {
    return null
  }
}

function asRecord(value: unknown): Record<string, unknown> | null {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>
  }
  return null
}

function firstString(obj: Record<string, unknown>, keys: string[]): string {
  for (const key of keys) {
    const v = obj[key]
    if (typeof v === 'string' && v.trim()) return v.trim()
    if (typeof v === 'number' || typeof v === 'boolean') return String(v)
  }
  return ''
}

function flattenToRows(
  value: unknown,
  prefix?: string,
  sourceKey?: string,
  depth = 0,
): ToolKvRow[] {
  const coerced = coerceJson(value)
  if (coerced == null || coerced === '') return []
  if (depth > 10) return []

  if (typeof coerced !== 'object') {
    const formatted = formatFieldValue(sourceKey, coerced)
    if (!formatted.text || !prefix) return []
    return [{
      label: prefix,
      value: formatted.text,
      mono: formatted.mono || (!!sourceKey && MONO_KEYS.has(sourceKey)),
      multiline: formatted.multiline,
    }]
  }

  if (Array.isArray(coerced)) {
    if (!coerced.length) return []
    if (coerced.every((v) => v == null || typeof v !== 'object')) {
      const formatted = formatDetailValue(coerced)
      if (!formatted.text) return []
      return [{
        label: prefix || '列表',
        value: formatted.text,
        mono: formatted.mono,
        multiline: formatted.multiline,
      }]
    }
    const rows: ToolKvRow[] = []
    coerced.forEach((item, i) => {
      const rec = asRecord(item)
      const itemLabel = `${prefix || '条目'} ${i + 1}`
      if (rec) rows.push(...flattenToRows(rec, itemLabel, undefined, depth + 1))
      else {
        const formatted = formatDetailValue(item)
        if (formatted.text) {
          rows.push({
            label: itemLabel,
            value: formatted.text,
            mono: formatted.mono,
            multiline: formatted.multiline,
          })
        }
      }
    })
    return rows
  }

  const obj = asRecord(coerced)
  if (!obj) return []
  const rows: ToolKvRow[] = []
  for (const [key, raw] of Object.entries(obj)) {
    if (META_SKIP_KEYS.has(key)) continue
    const label = prefix ? `${prefix} · ${humanizeKey(key)}` : humanizeKey(key)
    rows.push(...flattenToRows(raw, label, key, depth + 1))
  }
  return rows
}

function pickRows(
  obj: Record<string, unknown>,
  preferred: string[],
  opts?: { skip?: Set<string>; forceFullKeys?: Set<string> },
): ToolKvRow[] {
  const skip = opts?.skip || new Set<string>()
  const forceFull = opts?.forceFullKeys || LONG_ARG_KEYS
  const used = new Set<string>()
  const rows: ToolKvRow[] = []

  const push = (key: string, raw: unknown) => {
    if (used.has(key) || skip.has(key) || META_SKIP_KEYS.has(key)) return
    if (raw == null || raw === '') return
    used.add(key)
    const coerced = coerceJson(raw)
    if (typeof coerced === 'object' && coerced) {
      rows.push(...flattenToRows(coerced, humanizeKey(key), key))
      return
    }
    const full = forceFull.has(key)
    const formatted = formatFieldValue(key, coerced)
    if (!formatted.text) return
    rows.push({
      label: humanizeKey(key),
      value: formatted.text,
      mono: formatted.mono || MONO_KEYS.has(key),
      multiline: formatted.multiline || (full && formatted.text.length > 60),
    })
  }

  for (const key of preferred) {
    if (key in obj) push(key, obj[key])
  }
  for (const [key, raw] of Object.entries(obj)) {
    push(key, raw)
  }
  return rows
}

/** 从 Hermes 搜索结构中抽取命中列表：data.web / results / web / … */
function extractHitList(value: unknown): Record<string, unknown>[] {
  if (Array.isArray(value)) {
    return value.filter((v): v is Record<string, unknown> => !!asRecord(v))
  }
  const obj = asRecord(value)
  if (!obj) return []

  // web_search: { success, data: { web: [...] } }
  const data = asRecord(obj.data)
  if (data) {
    for (const key of ['web', 'results', 'organic', 'items', 'hits', 'documents', 'entries', 'matches']) {
      if (Array.isArray(data[key])) {
        return (data[key] as unknown[]).filter((v): v is Record<string, unknown> => !!asRecord(v))
      }
    }
  }

  for (const key of ['web', 'results', 'organic', 'items', 'hits', 'data', 'documents', 'entries', 'matches']) {
    const nested = obj[key]
    if (Array.isArray(nested)) {
      return nested.filter((v): v is Record<string, unknown> => !!asRecord(v))
    }
  }
  return []
}

function toSearchCards(hits: Record<string, unknown>[]): ToolCardItem[] {
  return hits.slice(0, 20).map((hit, i) => {
    const title = firstString(hit, ['title', 'name', 'heading', 'label']) || `结果 ${i + 1}`
    const url = firstString(hit, ['url', 'href', 'link', 'source', 'permalink'])
    // Hermes web_search 用 description；其它后端可能用 snippet/content
    const description = firstString(hit, ['description', 'snippet', 'content', 'summary', 'text', 'body'])
    const subtitleParts = [
      firstString(hit, ['provider', 'engine', 'site', 'author']),
      firstString(hit, ['published', 'date']),
    ].filter(Boolean)
    const pos = hit.position
    if (typeof pos === 'number') subtitleParts.unshift(`第 ${pos} 条`)
    return {
      title: compactOneLine(title, 120),
      url: url || undefined,
      subtitle: subtitleParts.length ? compactOneLine(subtitleParts.join(' · '), 80) : undefined,
      description: description ? compactOneLine(description, 360) : undefined,
    }
  }).filter((c) => c.title || c.url || c.description)
}

function statusFromRecord(obj: Record<string, unknown>): ToolStatusChip[] {
  const chips: ToolStatusChip[] = []
  if (typeof obj.ok === 'boolean') {
    chips.push({ label: obj.ok ? '成功' : '失败', tone: obj.ok ? 'ok' : 'err' })
  } else if (typeof obj.success === 'boolean') {
    chips.push({ label: obj.success ? '成功' : '失败', tone: obj.success ? 'ok' : 'err' })
  }
  const exit = obj.exit_code ?? obj.exitCode ?? obj.returncode
  if (typeof exit === 'number') {
    chips.push({ label: `退出码 ${exit}`, tone: exit === 0 ? 'ok' : 'warn' })
  }
  if (typeof obj.verified === 'boolean') {
    chips.push({ label: obj.verified ? '已校验写入' : '未校验', tone: obj.verified ? 'ok' : 'muted' })
  }
  if (typeof obj.truncated === 'boolean' && obj.truncated) {
    chips.push({ label: '内容已截断', tone: 'warn' })
  }
  if (typeof obj.blocked_by_policy === 'boolean' && obj.blocked_by_policy) {
    chips.push({ label: '被策略拦截', tone: 'err' })
  }
  const status = firstString(obj, ['status', 'state'])
  if (status) {
    const zh = humanizeStatus(status)
    if (!chips.some((c) => c.label.includes(zh) || c.label.includes(status))) {
      const lower = status.toLowerCase()
      const tone = /ok|success|done|completed|running/.test(lower)
        ? 'ok'
        : /fail|error|err/.test(lower)
          ? 'err'
          : 'muted'
      chips.push({ label: zh, tone })
    }
  }
  for (const key of ['count', 'total', 'bytes', 'size', 'size_bytes', 'bytes_written', 'lines', 'pid', 'results_count', 'pages_extracted']) {
    const v = obj[key]
    if (typeof v === 'number') chips.push({ label: `${humanizeKey(key)} ${v}`, tone: 'muted' })
    else if (typeof v === 'string' && v.trim()) chips.push({ label: `${humanizeKey(key)} ${v.trim()}`, tone: 'muted' })
  }
  return chips
}

function adaptWebSearch(obj: Record<string, unknown>): ToolDetailSection[] {
  const sections: ToolDetailSection[] = []
  const chips = statusFromRecord(obj)
  const hits = extractHitList(obj)
  const cards = toSearchCards(hits)
  const answer = firstString(obj, ['answer', 'summary', 'message'])

  if (chips.length) sections.push({ kind: 'status', title: '状态', items: chips })
  if (answer) sections.push({ kind: 'text', title: '摘要', text: answer })
  if (cards.length) {
    sections.push({
      kind: 'cards',
      title: `搜索结果（${cards.length}${hits.length > cards.length ? '+' : ''}）`,
      items: cards,
    })
  }

  const err = firstString(obj, ['error', 'error_message'])
  if (err) sections.push({ kind: 'text', title: '错误', text: err, error: true })

  return sections
}

function adaptWebExtract(obj: Record<string, unknown>): ToolDetailSection[] {
  const sections: ToolDetailSection[] = []
  const chips = statusFromRecord(obj)
  if (chips.length) sections.push({ kind: 'status', title: '状态', items: chips })

  const list = extractHitList(obj)
  const pages = list.length ? list : []

  if (!pages.length) {
    // 偶发单页对象
    if (firstString(obj, ['content', 'url', 'title'])) {
      pages.push(obj)
    }
  }

  if (!pages.length) {
    const err = firstString(obj, ['error', 'error_message'])
    sections.push({
      kind: 'text',
      title: err ? '错误' : '提取结果',
      text: err || '未提取到页面正文',
      error: !!err,
    })
    return sections
  }

  pages.forEach((page, i) => {
    const title = firstString(page, ['title', 'name']) || `页面 ${i + 1}`
    const url = firstString(page, ['url', 'href', 'link'])
    const content = firstString(page, ['content', 'raw_content', 'text', 'body', 'markdown'])
    const error = firstString(page, ['error', 'error_message'])
    const blocked = page.blocked_by_policy === true

    const metaRows: ToolKvRow[] = []
    if (url) metaRows.push({ label: '链接', value: url, mono: true })
    if (title) metaRows.push({ label: '标题', value: title })
    if (blocked) metaRows.push({ label: '策略拦截', value: '是' })

    if (metaRows.length) {
      sections.push({ kind: 'kv', title: pages.length > 1 ? `页面 ${i + 1}` : '页面信息', rows: metaRows })
    }
    if (error) {
      sections.push({ kind: 'text', title: '错误', text: error, error: true })
    }
    if (content) {
      const clipped = content.length > 8000 ? `${content.slice(0, 8000)}\n…` : content
      sections.push({
        kind: 'text',
        title: '正文',
        text: clipped,
        mono: looksLikeCode(content) || content.split('\n').length > 8,
      })
    } else if (!error) {
      sections.push({ kind: 'text', title: '正文', text: '（无正文）' })
    }
  })

  return sections
}

function adaptTerminalResult(obj: Record<string, unknown>): ToolDetailSection[] {
  const sections: ToolDetailSection[] = []
  const chips = statusFromRecord(obj)
  if (chips.length) sections.push({ kind: 'status', title: '状态', items: chips })

  const stdout = firstString(obj, ['output', 'stdout', 'result', 'text', 'content'])
  const stderr = firstString(obj, ['stderr', 'error', 'error_message'])
  if (stdout) sections.push({ kind: 'text', title: '输出', text: stdout, mono: true })
  if (stderr && stderr !== stdout) {
    sections.push({ kind: 'text', title: '错误输出', text: stderr, mono: true, error: true })
  }

  const rows = pickRows(obj, ['cwd', 'working_directory', 'command', 'cmd', 'pid', 'session_id'], {
    skip: new Set([
      'output', 'stdout', 'result', 'text', 'content', 'stderr', 'error', 'error_message',
      'ok', 'success', 'status', 'exit_code', 'exitCode', 'returncode',
      'stdout_truncated', 'stdout_bytes_captured', 'stdout_bytes_total', 'stdout_bytes_omitted',
    ]),
  })
  if (rows.length) sections.push({ kind: 'kv', title: '环境', rows })
  return sections
}

function adaptFileResult(obj: Record<string, unknown>): ToolDetailSection[] {
  const sections: ToolDetailSection[] = []
  const chips = statusFromRecord(obj)
  if (chips.length) sections.push({ kind: 'status', title: '状态', items: chips })

  const rows = pickRows(obj, [
    'path', 'file', 'filename', 'file_path', 'filepath',
    'bytes', 'size', 'size_bytes', 'bytes_written', 'lines', 'line_count',
    'encoding', 'offset', 'next_offset', 'limit',
  ], {
    skip: new Set([
      'content', 'text', 'body', 'markdown', 'data', 'ok', 'success', 'status',
      'stdout', 'stderr', 'verified', 'error', 'error_message',
    ]),
  })
  if (rows.length) sections.push({ kind: 'kv', title: '文件信息', rows })

  const content = firstString(obj, ['content', 'text', 'body', 'markdown', 'preview', 'stdout'])
  if (content) {
    sections.push({
      kind: 'text',
      title: '内容',
      text: content.length > 6000 ? `${content.slice(0, 6000)}\n…` : content,
      mono: looksLikeCode(content) || content.split('\n').length > 3,
    })
  }
  const err = firstString(obj, ['error', 'error_message'])
  if (err) sections.push({ kind: 'text', title: '错误', text: err, error: true })
  return sections
}

function adaptBrowserResult(obj: Record<string, unknown>): ToolDetailSection[] {
  const sections: ToolDetailSection[] = []
  const chips = statusFromRecord(obj)
  if (chips.length) sections.push({ kind: 'status', title: '状态', items: chips })

  const rows = pickRows(obj, ['url', 'page_url', 'title', 'page_title', 'selector', 'ref', 'path', 'action', 'screenshot_path'], {
    skip: new Set([
      'content', 'text', 'body', 'markdown', 'snapshot', 'html',
      'ok', 'success', 'status', 'screenshot', 'error', 'error_message',
    ]),
  })
  if (rows.length) sections.push({ kind: 'kv', title: '页面信息', rows })

  const content = firstString(obj, ['snapshot', 'content', 'text', 'body', 'markdown', 'html'])
  if (content) {
    sections.push({
      kind: 'text',
      title: '页面内容',
      text: content.length > 6000 ? `${content.slice(0, 6000)}\n…` : content,
      mono: false,
    })
  }
  const err = firstString(obj, ['error', 'error_message'])
  if (err) sections.push({ kind: 'text', title: '错误', text: err, error: true })
  return sections
}

function adaptTodoResult(obj: Record<string, unknown>): ToolDetailSection[] {
  const sections: ToolDetailSection[] = []
  const chips = statusFromRecord(obj)
  if (chips.length) sections.push({ kind: 'status', items: chips })

  const list = extractHitList(obj)
  const source = list.length
    ? list
    : Array.isArray(obj.todos)
      ? (obj.todos as unknown[]).map((t) => asRecord(t) || { text: String(t) })
      : Array.isArray(obj.tasks)
        ? (obj.tasks as unknown[]).map((t) => asRecord(t) || { text: String(t) })
        : []

  const items = source.map((t, i) => {
    const rec = asRecord(t) || {}
    const text = firstString(rec, ['text', 'content', 'title', 'task', 'label']) || `事项 ${i + 1}`
    const status = firstString(rec, ['status', 'state'])
    return status ? `${text}（${status}）` : text
  }).filter(Boolean)

  if (items.length) sections.push({ kind: 'list', title: '任务', items })
  const rows = pickRows(obj, ['action', 'message', 'summary'], {
    skip: new Set(['todos', 'tasks', 'items', 'results', 'ok', 'success', 'status']),
  })
  if (rows.length) sections.push({ kind: 'kv', rows })
  return sections
}

/** 通用：递归把任意 JSON 转成中文标签区块，绝不暴露英文 key 或原始 JSON */
function adaptGenericObject(obj: Record<string, unknown>, depth = 0): ToolDetailSection[] {
  if (depth > 8) {
    const rows = flattenToRows(obj)
    return rows.length ? [{ kind: 'kv', title: '详情', rows }] : []
  }

  // 形态优先
  if (extractHitList(obj).some((h) => firstString(h, ['url', 'href', 'title', 'description', 'snippet']))) {
    if (obj.data || obj.web || obj.results || obj.organic || obj.hits) {
      return adaptWebSearch(obj)
    }
  }
  if ('stdout' in obj || 'exit_code' in obj || 'exitCode' in obj || 'returncode' in obj || ('output' in obj && ('exit_code' in obj || 'returncode' in obj))) {
    return adaptTerminalResult(obj)
  }
  if (('path' in obj || 'filename' in obj) && ('content' in obj || 'bytes_written' in obj || 'verified' in obj)) {
    return adaptFileResult(obj)
  }
  if ('snapshot' in obj || ('url' in obj && ('title' in obj || 'html' in obj))) {
    return adaptBrowserResult(obj)
  }

  const sections: ToolDetailSection[] = []
  const chips = statusFromRecord(obj)
  if (chips.length) sections.push({ kind: 'status', title: '状态', items: chips })

  const nestedHits = extractHitList(obj)
  if (nestedHits.length >= 1 && nestedHits.some((h) => firstString(h, ['url', 'title', 'description', 'content']))) {
    if (nestedHits.some((h) => (firstString(h, ['content', 'raw_content']).length > 80))) {
      return [...sections, ...adaptWebExtract(obj)]
    }
    const cards = toSearchCards(nestedHits)
    if (cards.length) sections.push({ kind: 'cards', title: '结果', items: cards })
  }

  const skip = new Set<string>([
    'ok', 'success', 'status', 'verified', 'truncated', 'blocked_by_policy',
    'results', 'organic', 'items', 'hits', 'data', 'web', 'documents', 'todos', 'tasks',
    'exit_code', 'exitCode', 'returncode',
  ])

  for (const key of [
    'content', 'text', 'body', 'markdown', 'stdout', 'output', 'answer', 'summary',
    'message', 'stderr', 'error', 'error_message', 'snapshot', 'html',
  ]) {
    const v = obj[key]
    if (typeof v !== 'string' || !v.trim()) continue
    const coerced = coerceJson(v)
    skip.add(key)
    if (typeof coerced === 'object' && coerced) {
      const rec = asRecord(coerced)
      const nested = rec
        ? adaptGenericObject(rec, depth + 1)
        : adaptResultByTool('', coerced)
      if (nested.length) {
        for (const s of nested) {
          sections.push({
            ...s,
            title: s.title ? `${humanizeKey(key)} · ${s.title}` : humanizeKey(key),
          })
        }
        continue
      }
      const rows = flattenToRows(coerced, humanizeKey(key))
      if (rows.length) {
        sections.push({ kind: 'kv', title: humanizeKey(key), rows })
        continue
      }
      continue
    }
    sections.push({
      kind: 'text',
      title: humanizeKey(key),
      text: v.trim().length > 6000 ? `${v.trim().slice(0, 6000)}\n…` : v.trim(),
      mono: key === 'stdout' || key === 'stderr' || key === 'html' || looksLikeCode(v),
      error: key === 'stderr' || key === 'error' || key === 'error_message',
    })
  }

  for (const [key, raw] of Object.entries(obj)) {
    if (skip.has(key) || META_SKIP_KEYS.has(key)) continue
    const nested = asRecord(coerceJson(raw))
    if (!nested) continue
    skip.add(key)
    const child = adaptGenericObject(nested, depth + 1)
    if (child.length) {
      const kvRows = child
        .filter((s): s is Extract<ToolDetailSection, { kind: 'kv' }> => s.kind === 'kv')
        .flatMap((s) => s.rows)
      if (kvRows.length) {
        sections.push({ kind: 'kv', title: humanizeKey(key), rows: kvRows })
      }
      for (const s of child) {
        if (s.kind === 'kv') continue
        sections.push({
          ...s,
          title: s.title ? `${humanizeKey(key)} · ${s.title}` : humanizeKey(key),
        })
      }
    }
  }

  const rows = pickRows(obj, Object.keys(obj), { skip })
  if (rows.length) sections.push({ kind: 'kv', title: sections.length ? '其它信息' : '详情', rows })
  return sections
}

function sectionsFromUnknown(raw: unknown, toolName = ''): ToolDetailSection[] {
  const coerced = coerceJson(raw)
  return adaptResultByTool(toolName, coerced)
}

function adaptResultByTool(toolName: string, raw: unknown): ToolDetailSection[] {
  if (raw == null) return []

  if (typeof raw === 'string' || typeof raw === 'number' || typeof raw === 'boolean') {
    const coerced = coerceJson(raw)
    if (typeof coerced === 'object' && coerced) {
      return adaptResultByTool(toolName, coerced)
    }
    if (typeof raw === 'string') {
      const t = raw.trim()
      if (t) {
        return [{
          kind: 'text',
          text: t,
          mono: t.startsWith('{') || t.startsWith('[') || t.startsWith('```') || looksLikeCode(t),
        }]
      }
    }
    const text = typeof raw === 'boolean' ? formatBool(raw) : String(raw)
    return [{ kind: 'text', text, mono: typeof raw === 'string' && looksLikeCode(text) }]
  }

  if (Array.isArray(raw)) {
    if (raw.every((v) => v == null || typeof v === 'string' || typeof v === 'number' || typeof v === 'boolean')) {
      return [{ kind: 'list', items: raw.map((v) => (typeof v === 'boolean' ? formatBool(v) : String(v))) }]
    }
    const records = raw.filter((v): v is Record<string, unknown> => !!asRecord(v))
    if (records.some((h) => firstString(h, ['content', 'raw_content']).length > 40)) {
      return adaptWebExtract({ results: records })
    }
    const cards = toSearchCards(records)
    if (cards.length) return [{ kind: 'cards', title: '结果列表', items: cards }]
    const rows = flattenToRows(records, '条目')
    return rows.length ? [{ kind: 'kv', title: '结果列表', rows }] : []
  }

  const obj = asRecord(raw)
  if (!obj) return []

  // Claude Code PascalCase（normalize 后小写）→ 复用 Hermes 结果适配
  if (toolName === 'websearch') {
    return adaptResultByTool('web_search', raw)
  }
  if (toolName === 'webfetch') {
    return adaptResultByTool('web_extract', raw)
  }
  if (toolName === 'bash' || toolName === 'powershell' || toolName === 'monitor' || toolName === 'bashoutput') {
    return adaptResultByTool('terminal', raw)
  }
  if (toolName === 'read') {
    return adaptResultByTool('read_file', raw)
  }
  if (toolName === 'write') {
    return adaptResultByTool('write_file', raw)
  }
  if (toolName === 'edit' || toolName === 'notebookedit' || toolName === 'multiedit') {
    return adaptResultByTool('patch', raw)
  }
  if (toolName === 'glob' || toolName === 'grep') {
    return adaptResultByTool('search_files', raw)
  }
  if (toolName === 'todowrite' || (toolName.startsWith('task') && toolName !== 'task')) {
    return adaptResultByTool('todo', raw)
  }
  if (toolName === 'askuserquestion') {
    return adaptResultByTool('clarify', raw)
  }
  if (toolName === 'agent' || toolName === 'task' || toolName === 'sendmessage' || toolName === 'workflow') {
    return adaptResultByTool('delegate_task', raw)
  }
  if (toolName === 'skill') {
    return adaptResultByTool('skill_view', raw)
  }

  if (toolName === 'web_search' || toolName === 'x_search' || toolName === 'spotify_search' || toolName === 'session_search') {
    const sections = adaptWebSearch(obj)
    if (sections.length) return sections
  }
  if (toolName === 'web_extract' || toolName.startsWith('feishu_doc')) {
    const sections = adaptWebExtract(obj)
    if (sections.length) return sections
  }
  if (
    toolName === 'terminal'
    || toolName === 'process'
    || toolName === 'execute_code'
    || toolName === 'browser_console'
    || toolName === 'read_terminal'
  ) {
    return adaptTerminalResult(obj)
  }
  if (
    toolName === 'read_file'
    || toolName === 'write_file'
    || toolName === 'patch'
    || toolName === 'search_files'
  ) {
    return adaptFileResult(obj)
  }
  if (toolName.startsWith('browser_')) {
    return adaptBrowserResult(obj)
  }
  if (toolName === 'todo' || toolName.startsWith('kanban_')) {
    return adaptTodoResult(obj)
  }
  if (toolName === 'clarify') {
    const q = firstString(obj, ['question', 'prompt', 'text', 'message'])
    const sections: ToolDetailSection[] = []
    if (q) sections.push({ kind: 'text', title: '问题', text: q })
    const opts = obj.options
    if (Array.isArray(opts) && opts.every((v) => typeof v === 'string' || typeof v === 'number')) {
      sections.push({ kind: 'list', title: '选项', items: opts.map(String) })
    }
    if (sections.length) return sections
  }
  if (toolName === 'image_generate' || toolName === 'vision_analyze' || toolName.startsWith('video_')) {
    const sections: ToolDetailSection[] = []
    const chips = statusFromRecord(obj)
    if (chips.length) sections.push({ kind: 'status', items: chips })
    const rows = pickRows(obj, ['url', 'path', 'image', 'image_url', 'prompt', 'model', 'screenshot_path'], {
      skip: new Set(['ok', 'success', 'status']),
    })
    if (rows.length) sections.push({ kind: 'kv', rows })
    const desc = firstString(obj, ['description', 'caption', 'analysis', 'text', 'content'])
    if (desc) sections.push({ kind: 'text', title: '说明', text: desc })
    if (sections.length) return sections
  }
  if (toolName === 'delegate_task') {
    return adaptDelegateResult(obj)
  }
  if (toolName === 'subagent') {
    return adaptSubagentResult(obj, firstString(obj, ['tool', 'tool_name', 'childToolName']))
  }

  return adaptGenericObject(obj)
}

function formatGoalItem(g: unknown, i: number): string {
  if (typeof g === 'string' && g.trim()) return `${i + 1}. ${g.trim()}`
  const rec = asRecord(g)
  if (rec) {
    const goal = firstString(rec, ['goal', 'prompt', 'task', 'text'])
    if (goal) return `${i + 1}. ${goal}`
    const rows = flattenToRows(rec)
    if (rows.length) return `${i + 1}. ${rows.map((r) => `${r.label} ${r.value}`).join('；')}`
  }
  const formatted = formatDetailValue(g)
  return formatted.text ? `${i + 1}. ${formatted.text}` : `${i + 1}. （无内容）`
}

function adaptDelegateResult(obj: Record<string, unknown>): ToolDetailSection[] {
  const sections: ToolDetailSection[] = []
  const status = firstString(obj, ['status', 'mode'])
  const count = typeof obj.count === 'number' ? obj.count : undefined
  const chips: ToolStatusChip[] = []
  if (status) {
    const lower = status.toLowerCase()
    chips.push({
      label: humanizeStatus(status) || status,
      tone: lower === 'dispatched' || lower === 'background' ? 'warn' : 'muted',
    })
  }
  if (count != null && count > 0) {
    chips.push({ label: `${count} 个子智能体`, tone: 'muted' })
  }
  if (chips.length) sections.push({ kind: 'status', items: chips })

  const goals = obj.goals
  if (Array.isArray(goals) && goals.length) {
    sections.push({
      kind: 'list',
      title: '派工目标',
      items: goals.map((g, i) => formatGoalItem(g, i)),
    })
  }
  const note = firstString(obj, ['note', 'message', 'summary', 'result', 'output', 'content', 'text'])
  if (note) sections.push({ kind: 'text', title: '说明', text: note })

  const rows = pickRows(obj, ['delegation_id', 'mode', 'count', 'status'], {
    skip: new Set(['goals', 'note', 'message', 'summary', 'ok', 'success']),
  })
  if (rows.length) sections.push({ kind: 'kv', title: '派工信息', rows })
  return sections
}

function adaptSubagentResult(obj: Record<string, unknown>, childToolName = ''): ToolDetailSection[] {
  const sections: ToolDetailSection[] = []
  const goalVal = obj.goal ?? obj.prompt ?? obj.task
  const goalCoerced = coerceJson(goalVal)
  if (typeof goalCoerced === 'object' && goalCoerced) {
    const nested = sectionsFromUnknown(goalCoerced)
    if (nested.length) {
      sections.push(...nested.map((s) => ({
        ...s,
        title: s.title ? `任务目标 · ${s.title}` : '任务目标',
      })))
    } else {
      const rows = flattenToRows(goalCoerced, '任务目标')
      if (rows.length) sections.push({ kind: 'kv', title: '任务目标', rows })
    }
  } else {
    const goal = firstString(obj, ['goal', 'prompt', 'task'])
    if (goal) sections.push({ kind: 'text', title: '任务目标', text: goal })
  }

  const childTool = firstString(obj, ['tool', 'tool_name', 'childToolName']) || childToolName
  const rows = pickRows(obj, ['tool', 'tool_name', 'childToolName', 'task_index', 'taskIndex', 'task_count', 'taskCount', 'status', 'summary'], {
    skip: new Set([
      'goal', 'prompt', 'task', 'ok', 'success',
      'text', 'result', 'output', 'progress', 'content',
    ]),
  })
  if (rows.length) sections.push({ kind: 'kv', rows })

  for (const key of ['text', 'result', 'output', 'progress', 'content']) {
    const v = obj[key]
    if (v == null || v === '') continue
    const coerced = coerceJson(v)
    if (typeof coerced === 'object' && coerced) {
      const nested = adaptResultByTool(normalizeToolName(childTool), coerced)
      if (nested.length) {
        sections.push(...nested.map((s) => ({
          ...s,
          title: s.title || '调用结果',
        })))
        continue
      }
      const flat = flattenToRows(coerced, humanizeKey(key))
      if (flat.length) {
        sections.push({ kind: 'kv', title: '调用结果', rows: flat })
        continue
      }
    }
    if (typeof v === 'string' && v.trim()) {
      const goalText = firstString(obj, ['goal', 'prompt', 'task'])
      if (v.trim() !== goalText) {
        sections.push({ kind: 'text', title: '进展', text: v.trim() })
      }
    }
  }
  return sections
}

const FAILED_STATUS_VALUES = new Set([
  'error', 'failed', 'failure', 'timeout', 'timed_out',
  'cancelled', 'canceled', 'interrupted', 'aborted', 'denied', 'rejected',
])

/** 结果体里的失败信号；失败返回中文原因，成功返回 null */
function structuredFailure(value: unknown): string | null {
  const obj = asRecord(coerceJson(value))
  if (!obj) return null

  let failed = false
  if (obj.ok === false || obj.success === false) failed = true
  if (FAILED_STATUS_VALUES.has(firstString(obj, ['status', 'state']).toLowerCase())) failed = true
  const exit = obj.exit_code ?? obj.exitCode ?? obj.returncode
  if (typeof exit === 'number' && exit !== 0) failed = true
  if (obj.blocked_by_policy === true) failed = true

  let reason = firstString(obj, ['error', 'error_message', 'errorMessage'])
  if (reason) failed = true
  if (!failed) return null

  if (!reason) reason = firstString(obj, ['message', 'detail', 'reason', 'stderr'])
  if (!reason && typeof exit === 'number' && exit !== 0) reason = `命令退出码 ${exit}`
  return reason || '工具执行失败'
}

/**
 * 工具是否失败。除了上游状态，还看结果体：Hermes 多数工具失败时仍走 tool.complete，
 * 只在结果里标 success/ok/exit_code。
 */
export function toolCallFailure(tool: ToolCallInfo): string | null {
  if (tool.error?.trim()) return tool.error.trim()
  const status = (tool.status || '').toLowerCase()
  if (status === 'error') return '执行失败'
  if (status !== 'completed') return null
  return structuredFailure(tool.result) || structuredFailure(tool.resultText)
}

function toolOutputRaw(tool: ToolCallInfo): string {
  const err = tool.error?.trim()
  const status = (tool.status || '').toLowerCase()
  const isErr = status === 'error' || !!err
  if (isErr && err) {
    const base = tool.result?.trim() || tool.resultText?.trim() || ''
    if (base && base !== err) return `${err}\n\n${base}`
    return err
  }
  return (tool.result || tool.resultText || tool.progress || '').trim()
}

function enrichArgRecord(tool: ToolCallInfo): Record<string, unknown> | null {
  const parsed = asRecord(coerceJson(tool.args)) || asRecord(parseJson(tool.args))
  const extra: Record<string, unknown> = parsed ? { ...parsed } : {}
  if (tool.taskIndex != null && extra.task_index == null && extra.taskIndex == null) {
    extra.task_index = tool.taskIndex
  }
  if (tool.taskCount != null && extra.task_count == null && extra.taskCount == null) {
    extra.task_count = tool.taskCount
  }
  if (tool.childToolName && extra.tool == null && extra.tool_name == null) {
    extra.tool = tool.childToolName
  }
  if (Object.keys(extra).length) return extra
  const fallback = (tool.args || tool.context || '').trim()
  const coerced = coerceJson(fallback)
  return asRecord(coerced)
}

function adaptArgs(toolName: string, tool: ToolCallInfo): ToolKvRow[] {
  const parsed = enrichArgRecord(tool)
  if (parsed) {
    const preferred = ARG_KEY_ORDER[toolName]
      || ARG_KEY_ORDER[toolName.replace(/_v\d+$/, '')]
      || (toolName.startsWith('browser_') ? ['url', 'ref', 'selector', 'text', 'full'] : undefined)
      || (toolName.startsWith('kanban_') ? ['action', 'task', 'id', 'content', 'text', 'url', 'path'] : undefined)
      || ['query', 'command', 'cmd', 'path', 'url', 'urls', 'content', 'code', 'prompt', 'question', 'text', 'goal']
    const rows = pickRows(parsed, preferred)
    if (rows.length) return rows
    const flattened = flattenToRows(parsed)
    if (flattened.length) return flattened
  }
  const fallback = (tool.args || tool.context || '').trim()
  if (!fallback) return []
  const coerced = coerceJson(fallback)
  if (typeof coerced === 'object' && coerced) {
    return flattenToRows(coerced)
  }
  if (fallback.startsWith('{') || fallback.startsWith('[')) return []
  return [{
    label: '说明',
    value: fallback,
    mono: looksLikeCode(fallback),
    multiline: fallback.includes('\n') || fallback.length > 80,
  }]
}

function stripJsonBlob(text: string): string {
  const cut = text.search(/\s*[{[]/)
  if (cut >= 0) {
    const blob = text.slice(cut).trim()
    if (blob.startsWith('{') || blob.startsWith('[')) {
      return text.slice(0, cut).trim()
    }
  }
  return text.trim()
}

export function isLiveToolStatus(status?: string): boolean {
  const s = (status || '').toLowerCase()
  return s === 'started' || s === 'running' || s === 'awaiting' || s === 'background'
}

function summaryFromArgs(tool: ToolCallInfo, max: number): string {
  if (tool.context?.trim() && !tool.args?.trim()) return compactOneLine(tool.context, max)
  const toolName = normalizeToolName(tool.toolName)
  const args = asRecord(parseJson(tool.args)) || asRecord(coerceJson(tool.args))
  if (args) {
    const preferred = ARG_KEY_ORDER[toolName] || [
      'query', 'q', 'command', 'cmd', 'path', 'url', 'urls', 'prompt', 'question', 'text', 'content', 'title', 'task', 'goal',
    ]
    for (const key of preferred) {
      const v = args[key]
      if (v == null || v === '') continue
      if (typeof v === 'object') continue
      const formatted = formatFieldValue(key, v)
      if (!formatted.text) continue
      return `${humanizeKey(key)} ${compactOneLine(formatted.text, max)}`
    }
    for (const [key, v] of Object.entries(args)) {
      if (META_SKIP_KEYS.has(key) || v == null || typeof v === 'object') continue
      const formatted = formatFieldValue(key, v)
      if (formatted.text) return `${humanizeKey(key)} ${compactOneLine(formatted.text, max)}`
    }
  }
  const fallback = (tool.args || tool.context || '').trim()
  if (fallback && !fallback.startsWith('{') && !fallback.startsWith('[')) {
    return compactOneLine(fallback, max)
  }
  return ''
}

/** 一行摘要：执行中优先调用信息，完成后优先结果摘要 */
export function toolCallSummaryLine(tool: ToolCallInfo, max = 120): string {
  if (isLiveToolStatus(tool.status)) {
    const call = summaryFromArgs(tool, max)
    if (call) return call
    if (tool.progress?.trim()) {
      const coerced = coerceJson(tool.progress)
      if (typeof coerced !== 'object') return compactOneLine(String(tool.progress), max)
    }
    return ''
  }
  if (tool.summary?.trim()) {
    const cleaned = stripJsonBlob(tool.summary.trim())
    if (cleaned) return compactOneLine(cleaned, max)
  }
  if (tool.error?.trim()) return compactOneLine(tool.error, max)
  const fromArgs = summaryFromArgs(tool, max)
  if (fromArgs) return fromArgs
  return ''
}

function childToolOf(tool: ToolCallInfo): string {
  if (tool.childToolName?.trim()) return normalizeToolName(tool.childToolName)
  const args = asRecord(parseJson(tool.args))
  if (!args) return ''
  return normalizeToolName(firstString(args, ['tool', 'tool_name', 'childToolName']))
}

function sectionsFromRaw(toolName: string, raw: unknown): ToolDetailSection[] {
  const coerced = coerceJson(raw)
  const sections = adaptResultByTool(toolName, coerced)
  if (sections.length) return sections
  if (typeof coerced === 'object' && coerced) {
    const rows = flattenToRows(coerced)
    if (rows.length) return [{ kind: 'kv', title: '详情', rows }]
  }
  return []
}

function liveProgressSections(resultTool: string, progress: string): ToolDetailSection[] {
  if (!progress) return []
  const fromProgress = sectionsFromRaw(resultTool, progress)
  if (fromProgress.length) {
    return fromProgress.map((section) => (section.title ? section : { ...section, title: '实时进度' }))
  }
  const cleaned = stripJsonBlob(progress)
  if (cleaned) return [{ kind: 'text', title: '实时进度', text: cleaned }]
  return []
}

export function adaptToolDetail(tool: ToolCallInfo): ToolDetailModel {
  const toolName = normalizeToolName(tool.toolName)
  const argRows = adaptArgs(toolName, tool)
  const resultTool = toolName === 'subagent' ? (childToolOf(tool) || toolName) : toolName
  const progress = tool.progress?.trim() || ''
  const status = (tool.status || '').toLowerCase()

  if (isLiveToolStatus(status)) {
    return { argRows, resultSections: liveProgressSections(resultTool, progress) }
  }

  const raw = toolOutputRaw(tool)
  if (!raw) {
    if (progress) {
      const fromProgress = sectionsFromRaw(resultTool, progress)
      if (fromProgress.length) return { argRows, resultSections: fromProgress }
      const cleaned = stripJsonBlob(progress)
      if (cleaned) return { argRows, resultSections: [{ kind: 'text', title: '进度', text: cleaned }] }
    }
    return { argRows, resultSections: [] }
  }

  const sections = sectionsFromRaw(resultTool, raw)
  if (sections.length) return { argRows, resultSections: sections }

  const mono = toolName === 'terminal' || toolName === 'execute_code' || toolName === 'bash'
    || toolName === 'powershell' || toolName === 'monitor' || looksLikeCode(raw)
    || raw.startsWith('{') || raw.startsWith('[') || raw.startsWith('```')
    || raw.split('\n').length > 4
  const isErr = (tool.status || '').toLowerCase() === 'error' || !!tool.error?.trim()
  return {
    argRows,
    resultSections: [{
      kind: 'text',
      title: isErr ? '执行异常' : '执行结果',
      text: raw,
      mono,
      error: isErr,
    }],
  }
}

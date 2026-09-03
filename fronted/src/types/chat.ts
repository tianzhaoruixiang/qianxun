import type { SessionGoal } from '@/utils/sessionGoal'

/** 对话消息角色 */
export type MessageRole = 'user' | 'assistant'

/** 工具调用信息（对齐 Dashboard tool.start / tool.complete 等） */
export interface ToolCallInfo {
  toolCallId?: string
  toolName?: string
  /** 服务端转换的中文展示名 */
  displayName?: string
  /** 服务端给出的动态图标种类 */
  iconKind?: string
  args?: string
  result?: string
  status?: 'started' | 'running' | 'awaiting' | 'background' | 'completed' | 'error' | string
  startedAt?: number
  endedAt?: number
  durationMs?: number
  /** Dashboard duration_s（秒） */
  durationSeconds?: number
  /** 该工具开始时助手正文长度，用于穿插在会话中 */
  contentOffset?: number
  /** 上游事件类型，如 tool.start / tool.complete */
  eventType?: string
  /** tool.start 的短预览（通常 ≤80 字符） */
  context?: string
  /** tool.complete 的摘要句（如搜索条数） */
  summary?: string
  /** verbose 模式下的结果尾部文本 */
  resultText?: string
  error?: string
  inlineDiff?: string
  risk?: string
  findings?: string[]
  redacted?: boolean
  stderr?: string
  progress?: string
  todos?: unknown
  /** 子智能体事件附加信息 */
  subagent?: boolean
  taskIndex?: number
  taskCount?: number
  parentId?: string
  childSessionId?: string
  childToolName?: string
  apiCalls?: number
  toolCount?: number
  awaitingBackground?: boolean
  /** 专业智能体 code（委派卡片对齐注册表头像/名称） */
  agentCode?: string
  /** 注册表 icon / 人像 id */
  agentIcon?: string
}

/** 上游（Hermes / OpenAI）usage；会话层只做求和，不本地估算抬高 */
export interface ContextUsage {
  /** 计费输入：本轮或会话累计的 prompt_tokens（含系统/工具/历史） */
  promptTokens?: number
  /** 计费输出：completion_tokens */
  completionTokens?: number
  /** 计费总计：上游 total，或缺省为 prompt+completion */
  totalTokens?: number
  contextWindow?: number
  /** 当前上下文占用（SDK getContextUsage / 上游 context_used），仅用于进度条 */
  contextUsed?: number
  contextPercent?: number
  live?: boolean
  /** true 表示 input/output/total 是 Hermes Dashboard 的会话累计快照，不可跨消息再求和 */
  sessionSnapshot?: boolean
  /** true 表示 contextUsed/contextWindow 来自 Claude SDK /context，计费仍按各轮 result 累加 */
  contextSnapshot?: boolean
  /** true 表示 contextUsed 为本地正文粗估（上游尚未返回 /context 或 context_used） */
  estimatedOccupancy?: boolean
  /** Claude SDK result.modelUsage 汇总（含子智能体），单轮口径 */
  treePromptTokens?: number
  treeCompletionTokens?: number
  /** Claude SDK result.total_cost_usd，单轮估算成本（美元） */
  totalCostUsd?: number
  cacheReadTokens?: number
  cacheCreationTokens?: number
  /** 非计费：最近一轮 prompt 中系统/工具等开销对照 */
  promptOverheadTokens?: number
  /** 用户正文粗估（不含系统/工具），仅展示对照 */
  userTokensEstimate?: number
  /** 本轮模型生成耗时（毫秒），优先 SDK duration_ms */
  generationMs?: number
}

/** 对话消息接口 */
export interface Message {
  /** 消息唯一 ID */
  id: string
  /** 消息角色：用户或AI助手 */
  role: MessageRole
  /** 消息内容 */
  content: string
  /** 可选：流式过程中的工具调用记录 */
  toolCalls?: ToolCallInfo[]
  /** 是否仍在调用工具中（流结束后置为 false） */
  toolCalling?: boolean
  /** 本轮上下文占用 */
  usage?: ContextUsage
  /** 本轮结束后的下一步建议 */
  suggestions?: string[]
  /** 消息时间戳（毫秒） */
  timestamp: number
  /** 本轮附带的用户文档 */
  attachments?: Array<{ id: string; name: string }>
  /** completed | streaming | cancelled | error */
  status?: string
  runId?: string
}

/** 推荐问题项 */
export interface RecommendedQuestion {
  id: string
  text: string
  category?: string
}

/** 模型选项 */
export interface ModelOption {
  label: string
  value: string
}

/** 数据源选项 */
export interface DataSourceOption {
  label: string
  value: string
}

/** 历史会话 */
export interface HistorySession {
  /** 会话唯一标识 */
  id: string
  /** 会话标题 */
  title: string
  /** 创建时间戳（毫秒） */
  createdAt: number
  /** 最近更新时间（毫秒），用于「我的空间」排序 */
  updatedAt: number
  /** 消息数量 */
  messageCount: number
  /** 最后一条消息预览 */
  lastMessage: string
  /** 完整消息记录 */
  messages: Message[]
  /** 注册智能体 code；数智干警为空 */
  agentCode?: string
  hermesProfile?: string
  /** 展示用中文名 */
  agentName: string
  /** 会话长程目标 */
  goal?: SessionGoal | null
  /** 侧栏：是否正在流式输出 */
  streaming?: boolean
}

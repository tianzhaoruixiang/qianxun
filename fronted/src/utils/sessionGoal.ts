export interface SessionGoal {
  /** 会话条展示用短名；不是 Claude Code `/goal` 的独立参数 */
  title: string
  /** 完成条件（官方唯一必填入参，最长 4000 字） */
  description?: string
  /** 验收方式：对话里如何证明条件成立 */
  steps?: string
  constraints?: string
  /** 写入条件末尾：or stop after N turns */
  stopAfterTurns?: number | null
}

export function isSessionGoalActive(goal?: SessionGoal | null): boolean {
  if (!goal) return false
  return !!(goal.title?.trim() || goal.description?.trim())
}

export function formatGoalCondition(goal: SessionGoal): string {
  const title = goal.title?.trim() || ''
  const description = goal.description?.trim() || ''
  let core = description || title || '未命名目标'
  if (title && description && !description.includes(title)) {
    core = `${title}：${description}`
  }
  let text = core.replace(/\n/g, ' ').trim()
  const check = goal.steps?.trim()
  if (check) text += `；验收方式：${check.replace(/\n/g, ' ')}`
  const constraints = goal.constraints?.trim()
  if (constraints) text += `；约束：${constraints.replace(/\n/g, ' ')}`
  const turns = Number(goal.stopAfterTurns)
  if (Number.isFinite(turns) && turns > 0) {
    text += ` or stop after ${Math.min(200, Math.round(turns))} turns`
  }
  return text
}

/** 聊天气泡展示。发给 Claude Code 的是 `/goal <完成条件>`，不是这段中文。 */
export function formatGoalUserMessage(goal: SessionGoal): string {
  const title = goal.title.trim() || truncate(goal.description?.trim() || '未命名目标', 40)
  const lines = [`【长程目标】${title}`]
  const description = goal.description?.trim()
  if (description) lines.push(`完成条件：${description}`)
  const steps = goal.steps?.trim()
  if (steps) lines.push(`验收方式：${steps}`)
  const constraints = goal.constraints?.trim()
  if (constraints) lines.push(`约束：${constraints}`)
  const turns = Number(goal.stopAfterTurns)
  if (Number.isFinite(turns) && turns > 0) {
    lines.push(`轮次上限：${Math.round(turns)}`)
  }
  return lines.join('\n')
}

function truncate(text: string, max: number): string {
  const t = text.replace(/\s+/g, ' ').trim()
  return t.length <= max ? t : t.slice(0, max)
}

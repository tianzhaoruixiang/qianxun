/** Hermes /plan 与执行手递（对齐捆绑技能 plan + subagent-driven-development） */
export const PLAN_SKILL = 'plan'
export const PLAN_EXECUTE_SKILL = 'subagent-driven-development'

export const DEFAULT_PLAN_CREATE_TASK = '请根据当前对话上下文生成实施计划，并保存到工作区计划目录。'

export const DEFAULT_PLAN_EXECUTE_TASK =
  '请执行工作区计划目录下最新的计划文件：按任务逐步推进，每个任务使用独立子智能体，先做规范符合评审再做代码质量评审，两项通过后再进入下一任务。'

export const LOCAL_PLAN_EXECUTE_DISPLAY = '【执行计划】按工作区最新计划逐步实施'

export function isPlanExecuteToken(token: string): boolean {
  const t = (token || '').trim().toLowerCase()
  return /^(execute|exec|run|执行|开始执行|动手)$/i.test(t)
}

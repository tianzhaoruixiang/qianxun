import qinglanPhoto from '@/assets/images/portraits/qinglan.webp'
import mochuanPhoto from '@/assets/images/portraits/mochuan.webp'
import nuanchenPhoto from '@/assets/images/portraits/nuanchen.webp'
import zisuPhoto from '@/assets/images/portraits/zisu.webp'
import jilanPhoto from '@/assets/images/portraits/jilan.webp'
import danxiaPhoto from '@/assets/images/portraits/danxia.webp'
import cangsongPhoto from '@/assets/images/portraits/cangsong.webp'
import xingdianPhoto from '@/assets/images/portraits/xingdian.webp'
import chisongPhoto from '@/assets/images/portraits/chisong.webp'
import yunhuiPhoto from '@/assets/images/portraits/yunhui.webp'
import jinglanPhoto from '@/assets/images/portraits/jinglan.webp'
import jinghePhoto from '@/assets/images/portraits/jinghe.webp'
import jingshuPhoto from '@/assets/images/portraits/jingshu.webp'
import jingyanPhoto from '@/assets/images/portraits/jingyan.webp'
import jingningPhoto from '@/assets/images/portraits/jingning.webp'
import jingboPhoto from '@/assets/images/portraits/jingbo.webp'
import jingzhaoPhoto from '@/assets/images/portraits/jingzhao.webp'
import jingxuePhoto from '@/assets/images/portraits/jingxue.webp'
import jingchenPhoto from '@/assets/images/portraits/jingchen.webp'
import jingyuePhoto from '@/assets/images/portraits/jingyue.webp'

/** 智能体人物头像预设（写入 agent_registry.icon） */
export const AGENT_PORTRAIT_IDS = [
  'qinglan',
  'mochuan',
  'nuanchen',
  'zisu',
  'jilan',
  'danxia',
  'cangsong',
  'xingdian',
  'chisong',
  'yunhui',
  'jinglan',
  'jinghe',
  'jingshu',
  'jingyan',
  'jingning',
  'jingbo',
  'jingzhao',
  'jingxue',
  'jingchen',
  'jingyue',
] as const

export type AgentPortraitId = (typeof AGENT_PORTRAIT_IDS)[number]

export interface AgentPortraitPreset {
  id: AgentPortraitId
  label: string
  hint: string
  photo: string
}

export const AGENT_PORTRAITS: AgentPortraitPreset[] = [
  { id: 'qinglan', label: '青岚', hint: '短发女性 · 豆包风卡通', photo: qinglanPhoto },
  { id: 'mochuan', label: '墨川', hint: '短发男性 · 藏青西装', photo: mochuanPhoto },
  { id: 'nuanchen', label: '衡正', hint: '盘发女性 · 炭灰西装', photo: nuanchenPhoto },
  { id: 'zisu', label: '疏影', hint: '长发女性 · 靛蓝西装', photo: zisuPhoto },
  { id: 'jilan', label: '观澜', hint: '眼镜男性 · 青灰衬衫', photo: jilanPhoto },
  { id: 'danxia', label: '丹宸', hint: '短发女性 · 酒红西装', photo: danxiaPhoto },
  { id: 'cangsong', label: '苍松', hint: '短须男性 · 橄榄绿外套', photo: cangsongPhoto },
  { id: 'xingdian', label: '玄青', hint: '齐耳短发 · 靛蓝衬衫', photo: xingdianPhoto },
  { id: 'chisong', label: '赤卫', hint: '侧分短发 · 深褐外套', photo: chisongPhoto },
  { id: 'yunhui', label: '云晖', hint: '银发眼镜 · 石板西装', photo: yunhuiPhoto },
  { id: 'jinglan', label: '警岚', hint: '短发女性 · 藏青警服', photo: jinglanPhoto },
  { id: 'jinghe', label: '警和', hint: '沉稳男性 · 藏青警服', photo: jinghePhoto },
  { id: 'jingshu', label: '警舒', hint: '眼镜女性 · 藏青警服', photo: jingshuPhoto },
  { id: 'jingyan', label: '警岩', hint: '青年男性 · 藏青警服', photo: jingyanPhoto },
  { id: 'jingning', label: '警宁', hint: '马尾女性 · 藏青警服', photo: jingningPhoto },
  { id: 'jingbo', label: '警博', hint: '眼镜男性 · 藏青警服', photo: jingboPhoto },
  { id: 'jingzhao', label: '警昭', hint: '侧分男性 · 米色背景', photo: jingzhaoPhoto },
  { id: 'jingxue', label: '警雪', hint: '短发女性 · 浅蓝背景', photo: jingxuePhoto },
  { id: 'jingchen', label: '警辰', hint: '沉稳男性 · 青绿背景', photo: jingchenPhoto },
  { id: 'jingyue', label: '警玥', hint: '盘发女性 · 玫瑰背景', photo: jingyuePhoto },
]

export const DEFAULT_PORTRAIT_ID: AgentPortraitId = 'mochuan'

export const CLASSIC_OFFICER_PORTRAIT_ID = 'officer'

export const OFFICER_PORTRAIT_CHOICES: { id: string; label: string; hint: string }[] = [
  { id: CLASSIC_OFFICER_PORTRAIT_ID, label: '干警', hint: '系统默认智能体形象' },
  ...AGENT_PORTRAITS,
]

export function resolveOfficerPortraitId(value?: string | null): string {
  const raw = (value || '').trim().toLowerCase()
  if (raw === CLASSIC_OFFICER_PORTRAIT_ID) return CLASSIC_OFFICER_PORTRAIT_ID
  if (isAgentPortraitId(raw)) return raw
  return CLASSIC_OFFICER_PORTRAIT_ID
}

const LEGACY_ICON_MAP: Record<string, AgentPortraitId> = {
  bot: 'qinglan',
  robot: 'qinglan',
  analysis: 'jilan',
  chart: 'jilan',
  store: 'zisu',
  market: 'zisu',
}

const ID_SET = new Set<string>(AGENT_PORTRAIT_IDS)

export function isAgentPortraitId(value?: string | null): value is AgentPortraitId {
  return !!value && ID_SET.has(value)
}

export function getPortraitPreset(id?: string | null): AgentPortraitPreset {
  const hit = AGENT_PORTRAITS.find((p) => p.id === id)
  return hit || AGENT_PORTRAITS.find((p) => p.id === DEFAULT_PORTRAIT_ID)!
}

/** 非警服人像，供专业智能体子任务稳定随机头像 */
export const CIVILIAN_PORTRAIT_IDS = AGENT_PORTRAIT_IDS.filter((id) => !id.startsWith('jing')) as AgentPortraitId[]

export function hashPortraitId(seed: string, pool: readonly AgentPortraitId[] = AGENT_PORTRAIT_IDS): AgentPortraitId {
  const list = pool.length ? pool : AGENT_PORTRAIT_IDS
  let h = 0
  for (let i = 0; i < seed.length; i += 1) h = (h * 31 + seed.charCodeAt(i)) >>> 0
  return list[h % list.length]
}

export function portraitIdForAgent(
  icon?: string | null,
  seed?: string | null,
  pool: readonly AgentPortraitId[] = AGENT_PORTRAIT_IDS,
): AgentPortraitId {
  const raw = (icon || '').trim().toLowerCase()
  if (isAgentPortraitId(raw)) return raw
  if (raw && LEGACY_ICON_MAP[raw]) return LEGACY_ICON_MAP[raw]
  if (seed?.trim()) return hashPortraitId(seed.trim(), pool)
  return DEFAULT_PORTRAIT_ID
}

export function portraitIdFromGroupKey(
  groupKey: string,
  agents: { code: string; icon?: string; hermesProfile?: string }[],
): AgentPortraitId {
  const key = groupKey || ''
  if (key.startsWith('code:')) {
    const code = key.slice(5)
    const hit = agents.find((a) => a.code === code)
    return portraitIdForAgent(hit?.icon, hit?.code || code)
  }
  if (key.startsWith('profile:')) {
    const profile = key.slice(8)
    const hit = agents.find((a) => (a.hermesProfile || '').trim().toLowerCase() === profile.toLowerCase())
    return portraitIdForAgent(hit?.icon, hit?.code || profile)
  }
  return portraitIdForAgent('', key)
}
